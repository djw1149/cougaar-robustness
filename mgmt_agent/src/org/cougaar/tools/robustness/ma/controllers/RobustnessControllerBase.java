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
package org.cougaar.tools.robustness.ma.controllers;

import org.cougaar.tools.robustness.ma.CommunityStatusModel;
import org.cougaar.tools.robustness.ma.StatusChangeListener;
import org.cougaar.tools.robustness.ma.CommunityStatusChangeEvent;

import org.cougaar.tools.robustness.ma.util.HeartbeatHelper;
import org.cougaar.tools.robustness.ma.util.PingHelper;
import org.cougaar.tools.robustness.ma.util.PingListener;
import org.cougaar.tools.robustness.ma.util.RestartHelper;
import org.cougaar.tools.robustness.ma.util.MoveHelper;
import org.cougaar.tools.robustness.ma.util.LoadBalancer;

import org.cougaar.core.blackboard.BlackboardClientComponent;
import org.cougaar.core.component.BindingSite;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.LoggingService;
import java.util.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Robustness controller base class (for sledgehammer use case).  Dispatches
 * state controllers in response to raw events received from
 * CommunityStatysModel.  Also provides a number of utility methods and helper
 * classes for use by state controllers.
 */
public abstract class RobustnessControllerBase implements
    RobustnessController, StatusChangeListener {

  protected static final int NEVER = -1;

  class ControllerEntry {
    private int state;
    private String stateName;
    private StateController controller;
    private ControllerEntry(int state, String name, StateController sc) {
      this.state = state;
      this.stateName = name;
      this.controller = sc;
    }
  }

  private Map controllers = new HashMap();

  // Helper classes
  private HeartbeatHelper heartbeatHelper;
  private MoveHelper moveHelper;
  private PingHelper pingHelper;
  private RestartHelper restartHelper;
  private LoadBalancer loadBalancer;

  protected static DateFormat df = new SimpleDateFormat("HH:mm:ss");

  protected String thisAgent;
  protected LoggingService logger;
  private BindingSite bindingSite;

  protected CommunityStatusModel model;

  public RobustnessControllerBase(String thisAgent,
                                  final  BindingSite bs,
                                  final  CommunityStatusModel csm) {
    this.thisAgent = thisAgent;
    this.model = csm;
    this.bindingSite = bs;
    logger =
      (LoggingService)bs.getServiceBroker().getService(this, LoggingService.class, null);
    logger = org.cougaar.core.logging.LoggingServiceWithPrefix.add(logger, thisAgent + ": ");
    heartbeatHelper = new HeartbeatHelper(bs);
    moveHelper = new MoveHelper(bs);
    pingHelper = new PingHelper(bs);
    restartHelper = new RestartHelper(bs);
    loadBalancer = new LoadBalancer(bs, this);
  }

  /**
   * Add a new StateController.
   * @param state     State number
   * @param stateName
   * @param controller
   */
  public void addController(int state, final String stateName, StateController sc) {
    logger.debug("Adding state controller: state=" + stateName);
    if (sc instanceof StateControllerBase) {
      final StateControllerBase scb = (StateControllerBase) sc;
      new Thread() {
        public void run() {
          try {
            scb.setBindingSite(bindingSite);
            scb.initialize(bindingSite, model);
            scb.load();
            scb.start();
          }
          catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
          }
        }
      }.start();
    }
    controllers.put(new Integer(state), new ControllerEntry(state, stateName, sc));
  }

  /**
   * Get reference to controllers move helper.
   */
  public MoveHelper getMover() {
    return moveHelper;
  }

  /**
   * Get reference to controllers heartbeat helper.
   */
  public HeartbeatHelper getHeartbeater() {
    return heartbeatHelper;
  }

  /**
   * Get reference to controllers restart helper.
   */
  public RestartHelper getRestarter() {
    return restartHelper;
  }

  /**
   * Get reference to controllers ping helper.
   */
  public PingHelper getPinger() {
    return pingHelper;
  }

  /**
   * Get reference to controllers load balancer interface.
   */
  public LoadBalancer getLoadBalancer() {
    return loadBalancer;
  }

  /**
   * Default handler for receiving and dissemminating model change events.
   */
  public void statusChanged(CommunityStatusChangeEvent[] csce,
                            CommunityStatusModel csm) {
    boolean interestingChange = false;
    for(int i = 0; i < csce.length; i++) {
      if(logger.isDebugEnabled()) {
        logger.debug(csce[i].toString());
      }
      if(csce[i].membersAdded() || csce[i].membersRemoved()) {
        processMembershipChanges(csce[i], csm);
        interestingChange = true;
      }
      if(csce[i].stateChanged()) {
        processStatusChanges(csce[i], csm);
        interestingChange = true;
      }
      if(csce[i].leaderChanged()) {
        processLeaderChanges(csce[i], csm);
        if(!(csce[i].getCurrentLeader() == null && csce[i].getPriorLeader() == null))
          interestingChange = true;
      }
      if(csce[i].locationChanged()) {
        processLocationChanges(csce[i], csm);
      }
      if(csce[i].stateExpired()) {
        processStatusExpirations(csce[i], csm);
      }
    }
    if(interestingChange && csm.isLeader(thisAgent) && logger.isInfoEnabled()) {
      logger.info(statusSummary());
    }
  }

  protected void processMembershipChanges(CommunityStatusChangeEvent csce,
                                          CommunityStatusModel csm) {
  }

  protected void processLeaderChanges(CommunityStatusChangeEvent csce,
                                      CommunityStatusModel csm) {
    if(logger.isInfoEnabled()) {
      if(csce.getCurrentLeader() == null) {
        logger.debug("Lost robustness manager:" + " priorLeader=" +
                    csce.getPriorLeader());
      } else {
        logger.info("New robustness manager:" +
                    " newLeader=" + csce.getCurrentLeader() +
                    " priorLeader=" + csce.getPriorLeader());
      }
    }
  }

  protected void processStatusChanges(CommunityStatusChangeEvent csce,
                                      CommunityStatusModel csm) {
    // Notify controllers of state transition
    logger.debug("State change:" +
                " name=" + csce.getName() +
                " state=" + stateName(csce.getCurrentState()) +
                " prior=" + stateName(csce.getPriorState()));
    for (Iterator it = controllers.values().iterator(); it.hasNext();) {
      ControllerEntry ce = (ControllerEntry)it.next();
      if (ce.state == csce.getCurrentState()) {
        ce.controller.enter(csce.getName());
      }
      if (ce.state == csce.getPriorState()) {
        ce.controller.exit(csce.getName());
      }
    }
  }

  protected void processLocationChanges(CommunityStatusChangeEvent csce,
                                        CommunityStatusModel csm) {
  }

  protected void processStatusExpirations(CommunityStatusChangeEvent csce,
                                          CommunityStatusModel csm) {
    logger.debug("Status expiration:" +
                " name=" + csce.getName() +
                " state=" + stateName(csce.getCurrentState()));
    // Notify controllers of state expirations
    for (Iterator it = controllers.values().iterator(); it.hasNext();) {
      ControllerEntry ce = (ControllerEntry)it.next();
      if (ce.state == csce.getCurrentState()) {
        ce.controller.expired(csce.getName());
      }
    }
  }

  public String stateName(int state) {
    ControllerEntry ce = (ControllerEntry)controllers.get(new Integer(state));
    if (ce != null) {
      return ce.stateName;
    } else {
      return "ILLEGAL_VALUE";
    }
  }

  protected boolean isLeader() {
    return (model != null && model.isLeader(thisAgent));
  }

  protected boolean isLeader(String name) {
    return (model != null && model.isLeader(name));
  }

  protected boolean isLocalAgent(String name) {
    return (model != null &&
            thisAgent.equals(model.getLocation(name)));
  }

  protected void newState(String name, int state) {
    model.setCurrentState(name, state);
  }

  protected boolean isAgent(String name) {
    return (model.getType(name) == model.AGENT);
  }

  protected boolean isNode(String name) {
    return (model.getType(name) == model.NODE);
  }

  protected void setLocation(String name, String location) {
    model.setLocation(name, location);
  }

  protected void restartAgent(String name, String dest) {
    String orig = model.getLocation(name);
    model.setLocation(name, "");
    restartHelper.restartAgent(name, orig, dest,
                               model.getCommunityName());
  }

  protected void doPing(String name,
                        long timeout,
                        final int stateOnSuccess,
                        final int stateOnFail) {
    pingHelper.ping(name, timeout, new PingListener() {
      public void pingComplete(String name, int status) {
        logger.info("Ping:" +
                     " agent=" + name +
                     " state=" + stateName(model.getCurrentState(name)) +
                     " result=" + (status == PingHelper.SUCCESS ? "SUCCESS" : "FAIL"));
        if (status == PingHelper.SUCCESS) {
          newState(name, stateOnSuccess);
        } else {
          newState(name, stateOnFail);
          logger.info("PingFailed:" +
                       " agent=" + name +
                       " newState=" + stateName(stateOnFail));
        }
      }
    });
  }

  /**
   * Returns a String containing top-level health status of monitored community.
   */
  public String statusSummary() {
    String activeNodes[] = model.listEntries(CommunityStatusModel.NODE, getNormalState());
    String agents[] = model.listEntries(CommunityStatusModel.AGENT);
    //String expiredNodes[] = model.getExpired(CommunityStatusModel.NODE);
    //String expiredAgents[] = model.getExpired(CommunityStatusModel.AGENT);
    StringBuffer summary = new StringBuffer("community=" + model.getCommunityName());
    summary.append(" leader=" + model.getLeader());
    summary.append(" activeNodes=" + activeNodes.length + arrayToString(activeNodes));
    //summary.append(" agents=" + agents.length + arrayToString(agents));
    summary.append(" agents=" + agents.length);
    for (Iterator it = controllers.values().iterator(); it.hasNext();) {
      int state = ( (ControllerEntry) it.next()).state;
      String agentsInState[] = model.listEntries(CommunityStatusModel.AGENT,
                                                 state);
      if (agentsInState.length > 0) {
        summary.append(" " + stateName(state) + "=" + agentsInState.length);
        if (!stateName(state).equals("ACTIVE") && agentsInState.length > 0)
          summary.append(arrayToString(agentsInState));
      }
    }
    /*
    summary.append(" expiredNodes=" + expiredNodes.length +
                   (expiredNodes.length > 0 ? arrayToString(expiredNodes) : ""));
    summary.append(" expiredAgents=" + expiredAgents.length +
                   (expiredAgents.length > 0 ? arrayToString(expiredAgents) : ""));
    */
    return summary.toString();
  }

  /**
   * Returns XML formatted dump of all community status information.
   * @return XML formatted status
   */
  public String getCompleteStatus() {
    StringBuffer sb = new StringBuffer("<community" +
                                      " name=\"" + model.getCommunityName() + "\"" +
                                      " leader=\"" + model.getLeader() + "\"" +
                                      //" statusTTL=\"" + TTL + "\"" +
                                      " >\n");
    String nodeNames[] = model.listEntries(CommunityStatusModel.NODE);
    sb.append("  <nodes count=\"" + nodeNames.length + "\" >\n");
    for (int i = 0; i < nodeNames.length; i++) {
      sb.append(nodeStatusToXML("    ", nodeNames[i]) + "\n");
    }
    sb.append("  </nodes>\n");
    String agentNames[] = model.listEntries(CommunityStatusModel.AGENT);
    sb.append("  <agents count=\"" + agentNames.length + "\" >\n");
    for (int i = 0; i < agentNames.length; i++) {
      sb.append(agentStatusToXML("    ", agentNames[i]) + "\n");
    }
    sb.append("  </agents>\n");
    sb.append("</community>\n");
    return sb.toString();
  }


  public String agentStatusToXML(String indent, String agentName) {
    return indent + "<agent" +
        " name=\"" + agentName + "\"" +
        " status=\"" + stateName(model.getCurrentState(agentName)) + "\"" +
        " node=\"" + model.getLocation(agentName) + "\"" +
        " last=\"" + df.format(model.getTimestamp(agentName)) + "\"" +
        " expired=\"" + model.isExpired(agentName) + "\"" +
        " />";
  }

  public String nodeStatusToXML(String indent, String nodeName) {
    return indent + "<node" +
        " name=\"" + nodeName + "\"" +
        " status=\"" + stateName(model.getCurrentState(nodeName)) + "\"" +
        " vote=\"" + model.getLeaderVote(nodeName) + "\"" +
        " last=\"" + df.format(model.getTimestamp(nodeName)) + "\"" +
        " expired=\"" + model.isExpired(nodeName) + "\"" +
        " />";
  }

  public static String arrayToString(String[] strArray) {
    StringBuffer sb = new StringBuffer("[");
    for (int i = 0; i < strArray.length; i++) {
      sb.append(strArray[i]);
      if (i < strArray.length - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

}