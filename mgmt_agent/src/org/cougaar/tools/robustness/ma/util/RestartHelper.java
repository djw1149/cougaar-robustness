/*
 * <copyright>
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the Cougaar Open Source License as published by
 *  DARPA on the Cougaar Open Source Website (www.cougaar.org).
 *
 *  THE COUGAAR SOFTWARE AND ANY DERIVATIVE SUPPLIED BY LICENSOR IS
 *  PROVIDED 'AS IS' WITHOUT WARRANTIES OF ANY KIND, WHETHER EXPRESS OR
 *  IMPLIED, INCLUDING (BUT NOT LIMITED TO) ALL IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE, AND WITHOUT
 *  ANY WARRANTIES AS TO NON-INFRINGEMENT.  IN NO EVENT SHALL COPYRIGHT
 *  HOLDER BE LIABLE FOR ANY DIRECT, SPECIAL, INDIRECT OR CONSEQUENTIAL
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE OF DATA OR PROFITS,
 *  TORTIOUS CONDUCT, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 *  PERFORMANCE OF THE COUGAAR SOFTWARE.
 * </copyright>
 */
package org.cougaar.tools.robustness.ma.util;

import org.cougaar.tools.robustness.ma.ldm.HealthMonitorRequest;
import org.cougaar.tools.robustness.ma.ldm.HealthMonitorRequestImpl;
import org.cougaar.tools.robustness.ma.ldm.RelayAdapter;

import org.cougaar.core.blackboard.BlackboardClientComponent;
import org.cougaar.core.blackboard.IncrementalSubscription;

import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.service.DomainService;
import org.cougaar.core.service.EventService;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.SchedulerService;
import org.cougaar.core.service.UIDService;

import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.ServiceBroker;

import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.SimpleMessageAddress;

import org.cougaar.core.mobility.AbstractTicket;
import org.cougaar.core.mobility.AddTicket;
import org.cougaar.core.mobility.ldm.AgentControl;
import org.cougaar.core.mobility.ldm.MobilityFactory;

import org.cougaar.core.service.AlarmService;
import org.cougaar.core.agent.service.alarm.Alarm;

import org.cougaar.core.util.UID;

import org.cougaar.util.UnaryPredicate;

import java.util.ArrayList;
import java.util.Date;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Provides convenience methods for invoking restarts on local and remote
 * nodes.
 */
public class RestartHelper extends BlackboardClientComponent {

  // Status codes returned to listeners
  public static final int SUCCESS = 0;
  public static final int FAIL = 1;

  public static final long TIMER_INTERVAL = 10 * 1000;
  public static final long RESTART_TIMEOUT = 2 * 60 * 1000;
  public static final long MAX_CONCURRENT_RESTARTS = 1;

  private List restartQueue = Collections.synchronizedList(new ArrayList());
  private List remoteRestartRequestQueue = new ArrayList();
  private Map restartsInProcess = Collections.synchronizedMap(new HashMap());

  private WakeAlarm wakeAlarm;

  private Set myUIDs = new HashSet();
  private MobilityFactory mobilityFactory;
  private LoggingService logger;
  private UIDService uidService = null;
  protected EventService eventService;
  private List listeners = new ArrayList();

  // For interaction with Mobility
  private IncrementalSubscription agentControlSub;
  private UnaryPredicate agentControlPredicate = new UnaryPredicate() {
    public boolean execute(Object o) {
      if (o instanceof AgentControl) {
        AgentControl ac = (AgentControl)o;
        return (myUIDs.contains(ac.getOwnerUID()));
      } else {
        return false;
      }
    }
  };

  // For receiving Relays from remote nodes
  private IncrementalSubscription healthMonitorRequests;
  private UnaryPredicate healthMonitorRequestPredicate = new UnaryPredicate() {
    public boolean execute (Object o) {
      if (o instanceof HealthMonitorRequest) {
        HealthMonitorRequest hmr = (HealthMonitorRequest)o;
        return (hmr.getRequestType() == hmr.RESTART);
      }
      return false;
  }};

  public RestartHelper(BindingSite bs) {
    this.setBindingSite(bs);
    initialize();
    load();
    start();
  }

  /**
   * Load required services.
   */
  public void load() {
    setAgentIdentificationService(
      (AgentIdentificationService)getServiceBroker().getService(this, AgentIdentificationService.class, null));
    setAlarmService(
      (AlarmService)getServiceBroker().getService(this, AlarmService.class, null));
    setSchedulerService(
      (SchedulerService)getServiceBroker().getService(this, SchedulerService.class, null));
    setBlackboardService(
      (BlackboardService)getServiceBroker().getService(this, BlackboardService.class, null));
    eventService = (EventService) getServiceBroker().getService(this, EventService.class, null);
    logger =
      (LoggingService)getBindingSite().getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, agentId + ": ");
    DomainService ds =
        (DomainService) getServiceBroker().getService(this, DomainService.class, null);
    mobilityFactory = (MobilityFactory) ds.getFactory("mobility");
    uidService = (UIDService) getServiceBroker().getService(this, UIDService.class, null);
    super.load();
  }

  /**
   * Subscribe to HealthMonitorRequest relays and mobility objects.
   */
  public void setupSubscriptions() {
    agentControlSub =
        (IncrementalSubscription)blackboard.subscribe(agentControlPredicate);
    healthMonitorRequests =
        (IncrementalSubscription)blackboard.subscribe(healthMonitorRequestPredicate);
    // Start timer to periodically check RestartQueue
    wakeAlarm = new WakeAlarm((new Date()).getTime() + TIMER_INTERVAL);
    alarmService.addRealTimeAlarm(wakeAlarm);
  }

  public void execute() {

    // Remove failed restarts
    if (!restartsInProcess.isEmpty()) {
      removeExpiredRestarts();
    }

    // Perform local restarts
    if (!restartQueue.isEmpty()) {
      restartNext();
    }

    // Forward non-local restarts to remote agent
    fireAll();

    // Get AgentControl objects
    for (Iterator it = agentControlSub.iterator(); it.hasNext();) {
      update(it.next());
    }

    for (Iterator it = healthMonitorRequests.getAddedCollection().iterator(); it.hasNext(); ) {
      HealthMonitorRequest hsm = (HealthMonitorRequest) it.next();
      logger.debug("Received HealthMonitorRequest:" + hsm);
      if (hsm.getRequestType() == HealthMonitorRequest.RESTART) {
        String agentNames[] = hsm.getAgents();
        for (int i = 0; i < agentNames.length; i++) {
          restartAgent(agentNames[i]);
        }
      }
    }
  }

  /**
   * Method used by clients to initiate a restart.  The destination node is
   * responsible for interaction with Mobility to perform the restart.  If the
   * local agent is not the destination a request is sent to the destination
   * via a blackboard Relay.
   * @param agentName      Name of agent to be restarted
   * @param origNode       Origin node
   * @param destNode       Destination node
   * @param communityName  Robustness community name
   */
  public void restartAgent(String agentName,
                           String origNode,
                           String destNode,
                           String communityName) {
    logger.debug("RestartAgent:" +
                " destNode=" + destNode +
                " agent=" + agentName);
    if (agentId.toString().equals(destNode)) {
      // Restart locally
      restartAgent(agentName);
    } else {
      // Queue request to remote agent
      fireLater(new RemoteRestartRequest(agentName,
                                         origNode,
                                         destNode,
                                         communityName));
    }
  }

  protected void fireLater(RemoteRestartRequest rrr) {
    synchronized (remoteRestartRequestQueue) {
      remoteRestartRequestQueue.add(rrr);
    }
    if (blackboard != null) {
      blackboard.signalClientActivity();
    }
  }

  private void fireAll() {
    int n;
    List l;
    synchronized (remoteRestartRequestQueue) {
      n = remoteRestartRequestQueue.size();
      if (n <= 0) {
        return;
      }
      l = new ArrayList(remoteRestartRequestQueue);
      remoteRestartRequestQueue.clear();
    }
    for (int i = 0; i < n; i++) {
      sendRemoteRequest((RemoteRestartRequest) l.get(i));
    }
  }

  private void sendRemoteRequest(RemoteRestartRequest rrr) {
    UIDService uidService = (UIDService)getServiceBroker().getService(this,
        UIDService.class, null);
    HealthMonitorRequest hmr =
        new HealthMonitorRequestImpl(agentId,
                                     rrr.communityName,
                                     HealthMonitorRequest.RESTART,
                                     new String[] {rrr.agentName}
                                     ,
                                     rrr.origNode,
                                     rrr.destNode,
                                     uidService.nextUID());
    RelayAdapter hmrRa = new RelayAdapter(agentId, hmr, hmr.getUID());
    hmrRa.addTarget(SimpleMessageAddress.getSimpleMessageAddress(rrr.
        destNode));
    if(logger.isDebugEnabled()) {
      logger.debug("Publishing HealthMonitorRequest:" +
                   " request=" + hmr.getRequestTypeAsString() +
                   " targets=" + targetsToString(hmrRa.getTargets()) +
                   " community-" + hmr.getCommunityName() +
                   " agents=" +
                   arrayToString(hmr.getAgents()) +
                   " destNode=" + hmr.getDestinationNode());
    }
    blackboard.publishAdd(hmrRa);

  }

  /**
   * Queue request for local action and trigger execute() method.
   * @param agentName
   */
  protected void restartAgent(String agentName) {
    logger.debug("RestartAgent:" +
                " agent=" + agentName);
    if (!restartQueue.contains(agentName)) {
      restartQueue.add(agentName);
      blackboard.signalClientActivity();
    }
  }

  /**
   * Returns current time as long.
   * @return Current time.
   */
  private long now() { return (new Date()).getTime(); }

  /**
   * Publish requests to Mobility to initiate restart and perform necessary
   * bookkeeping to keep track of restarts that are in process.
   */
  private void restartNext() {
    if ((!restartQueue.isEmpty()) &&
        (restartsInProcess.size() <= MAX_CONCURRENT_RESTARTS)) {
      logger.debug("RestartNext: " +
                   " RestartQueue=" + restartQueue.size() +
                   " restartsInProcess=" + restartsInProcess.size());
      MessageAddress agentToRestart =
          SimpleMessageAddress.getSimpleMessageAddress((String)
          restartQueue.remove(0));
      Long restartExpiration = new Long(now() + RESTART_TIMEOUT);
      restartsInProcess.put(agentToRestart, restartExpiration);
      try {
        Object ticketId = mobilityFactory.createTicketIdentifier();
        AddTicket addTicket = new AddTicket(ticketId, agentToRestart, agentId);
        UID acUID = uidService.nextUID();
        myUIDs.add(acUID);
        AgentControl ac =
            mobilityFactory.createAgentControl(acUID, agentId, addTicket);
        restartInitiated(agentToRestart, agentId);
        //event("Restarting agent: agent=" + agentToRestart + " dest=" + agentId);
        blackboard.publishAdd(ac);
        if (logger.isInfoEnabled()) {
          StringBuffer sb =
              new StringBuffer("Publishing AgentControl:" +
                               " myUid=" + myUIDs.contains(ac.getOwnerUID()) +
                               " status=" + ac.getStatusCodeAsString());
          if (ac.getAbstractTicket()instanceof AddTicket) {
            AddTicket at = (AddTicket)ac.getAbstractTicket();
            sb.append(" agent=" + at.getMobileAgent() +
                      " destNode=" + at.getDestinationNode());
          }
          logger.debug(sb.toString());
        }
      } catch (Exception ex) {
        logger.error("Exception in agent restart", ex);
      }
    }
  }

  /**
   * Check restarts that are in process and remove any that haven't been
   * completed within the expiration time.
   */
  private void removeExpiredRestarts() {
    long now = now();
    MessageAddress currentRestarts[] = getRestartsInProcess();
    for (int i = 0; i < currentRestarts.length; i++) {
      long expiration = ((Long)restartsInProcess.get(currentRestarts[i])).
          longValue();
      if (expiration < now) {
        restartComplete(currentRestarts[i], agentId, FAIL);
      }
    }
  }

  /**
   * Evaluate updates to AgentControl objects used by Mobility.
   * @param o
   */
  public void update(Object o) {
    if (o instanceof AgentControl) {
      AgentControl ac = (AgentControl)o;
      if (myUIDs.contains(ac.getOwnerUID())) {
        AbstractTicket ticket = ac.getAbstractTicket();
        if (ticket instanceof AddTicket) {
          AddTicket addTicket = (AddTicket) ticket;
          switch (ac.getStatusCode()) {
            case AgentControl.CREATED:
              /*
              event("Restart successful:" +
                    " agent=" + addTicket.getMobileAgent() +
                    " dest=" + addTicket.getDestinationNode() +
                    " status=" + ac.getStatusCodeAsString());
              */
              blackboard.publishRemove(ac);
              myUIDs.remove(ac.getOwnerUID());
              restartComplete(addTicket.getMobileAgent(),
                              addTicket.getDestinationNode(),
                              SUCCESS);
              break;
            case AgentControl.ALREADY_EXISTS:
              /*
              event("Restart successful:" +
                    " agent=" + addTicket.getMobileAgent() +
                    " dest=" + addTicket.getDestinationNode() +
                    " status=" + ac.getStatusCodeAsString());
              */
              blackboard.publishRemove(ac);
              myUIDs.remove(ac.getOwnerUID());
              restartComplete(addTicket.getMobileAgent(),
                              addTicket.getDestinationNode(),
                              SUCCESS);
              break;
            case AgentControl.FAILURE:
              /*
              event("Restart failed:" +
                    " agent=" + addTicket.getMobileAgent() +
                    " dest=" + addTicket.getDestinationNode() +
                    " status=" + ac.getStatusCodeAsString());
              */
              blackboard.publishRemove(ac);
              myUIDs.remove(ac.getOwnerUID());
              restartComplete(addTicket.getMobileAgent(),
                              addTicket.getDestinationNode(),
                              FAIL);
              break;
            case AgentControl.NONE:
              break;
            default:
              logger.info("Unexpected restart status" +
                          " statucCode=" + ac.getStatusCodeAsString() +
                          ", blackboard object not removed");
          }
        }
      }
    }
  }

  /**
   * Add a RestartListener.
   * @param rl  RestartListener to add
   */
  public void addListener(RestartListener rl) {
    synchronized (listeners) {
      if (!listeners.contains(rl))
        listeners.add(rl);
    }
  }

  /**
   * Remove a RestartListener.
   * @param rl  RestartListener to remove
   */
  public void removeListener(RestartListener rl) {
    synchronized (listeners) {
      if (listeners.contains(rl))
        listeners.remove(rl);
    }
  }

  /**
    * Notify restart listeners.
    */
   private void restartInitiated(MessageAddress agent, MessageAddress dest) {
     logger.debug("RestartInitiated: agent=" + agent + " dest=" + dest);
     synchronized (listeners) {
       for (Iterator it = listeners.iterator(); it.hasNext(); ) {
         RestartListener rl = (RestartListener) it.next();
         rl.restartInitiated(agent.toString(), dest.toString());
       }
     }
   }

   /**
    * Returns addresses of agents that are currently being restarted.
    * @return
    */
  private MessageAddress[] getRestartsInProcess() {
    synchronized (restartsInProcess) {
      return (MessageAddress[])restartsInProcess.keySet().toArray(new MessageAddress[0]);
    }
  }

  /**
   * Notify restart listeners.
   */
  private void restartComplete(MessageAddress agent, MessageAddress dest, int status) {
    logger.debug("RestartComplete: agent=" + agent + " dest=" + dest + " status=" + status);
    synchronized (listeners) {
      for (Iterator it = listeners.iterator(); it.hasNext(); ) {
        RestartListener rl = (RestartListener) it.next();
        rl.restartComplete(agent.toString(), dest.toString(), status);
      }
    }
    restartsInProcess.remove(agent);
    restartNext();
  }


  /**
   * Sends Cougaar event via EventService.
   */
  protected void event(String message) {
    if (eventService != null && eventService.isEventEnabled())
      eventService.event(message);
  }

  private static String targetsToString(Collection targets) {
    StringBuffer sb = new StringBuffer("[");
    for (Iterator it = targets.iterator(); it.hasNext();) {
      sb.append(it.next());
      if (it.hasNext()) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  private static String arrayToString(String[] strArray) {
    if (strArray == null) {
      return "null";
    }
    else {
      StringBuffer sb = new StringBuffer("[");
      for (int i = 0; i < strArray.length; i++) {
        sb.append(strArray[i]);
        if (i < strArray.length - 1)
          sb.append(",");
      }
      sb.append("]");
      return sb.toString();
    }
  }

  // Timer for periodically stimulating execute() method to check/process
  // restart queue
  private class WakeAlarm implements Alarm {
    private long expiresAt;
    private boolean expired = false;
    public WakeAlarm (long expirationTime) {
      expiresAt = expirationTime;
    }
    public long getExpirationTime() {
      return expiresAt;
    }
    public synchronized void expire() {
      if (!expired) {
        expired = true;
        if (blackboard != null) blackboard.signalClientActivity();
        wakeAlarm = new WakeAlarm((new Date()).getTime() + TIMER_INTERVAL);
        alarmService.addRealTimeAlarm(wakeAlarm);
      }
    }
    public boolean hasExpired() {
      return expired;
    }
    public synchronized boolean cancel() {
      boolean was = expired;
      expired = true;
      return was;
    }
  }

  private class RemoteRestartRequest {
    private String agentName;
    private String origNode;
    private String destNode;
    private String communityName;
    RemoteRestartRequest (String agent, String orig, String dest, String community) {
      this.agentName = agent;
      this.origNode = orig;
      this.destNode = dest;
      this.communityName = community;
    }
  }

}
