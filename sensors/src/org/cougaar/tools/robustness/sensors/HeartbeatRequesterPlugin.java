/*
 * <copyright>
 *  Copyright 1997-2001 BBNT Solutions, LLC
 *  Copyright 2002 Object Services and Consulting, Inc.
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA).
 *
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

package org.cougaar.tools.robustness.sensors;

import org.cougaar.core.plugin.*;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.blackboard.IncrementalSubscription;
import java.util.Iterator;
import org.cougaar.core.service.BlackboardService;
import org.cougaar.core.util.UID;
import org.cougaar.core.service.UIDService;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.blackboard.UniqueObjectSet;
import java.util.Date;
import java.util.Hashtable;
import org.cougaar.core.agent.service.alarm.Alarm;

/**
 * This Plugin receives HeartbeatRequests from the local Blackboard and
 * sends HbReqs to the target agent's HeartbeatServerPlugin. It should 
 * be installed in the Agent that is originating the HeartbeatRequests.
 **/
public class HeartbeatRequesterPlugin extends ComponentPlugin {
  private IncrementalSubscription heartbeatRequestSub;
  private IncrementalSubscription hbReqSub;
  private BlackboardService bb;
  private UniqueObjectSet reqTable;
  private Hashtable hbTable;
  private Hashtable reportTable;
  private SendHealthReportsAlarm nextAlarm = null;
  
  private class SendHealthReportsAlarm implements Alarm {
    private long detonate = -1;
    private boolean expired = false;

    public SendHealthReportsAlarm (long delay) {
      detonate = delay + System.currentTimeMillis();
    }

    public long getExpirationTime () {
      return detonate;
    }

    public void expire () {
      if (!expired) {
        bb.openTransaction();
        prepareHealthReports();
        bb.closeTransaction();
        expired = true;
      }
    }

    public boolean hasExpired () {
      return expired;
    }

    public boolean cancel () {
      if (!expired)
        return expired = true;
      return false;
    }
  }

  private UnaryPredicate HeartbeatRequestPred = new UnaryPredicate() {
    public boolean execute(Object o) {
      return (o instanceof HeartbeatRequest);
    }
  };

  private UnaryPredicate hbReqPred = new UnaryPredicate() {
    public boolean execute(Object o) {
      return (o instanceof HbReq);
    }
  };

  private void updateHeartbeatRequest (HbReq hbReq) {
    HbReqContent content = (HbReqContent)hbReq.getContent();
    UID reqUID = content.getHeartbeatRequestUID();
    HeartbeatRequest req = (HeartbeatRequest)reqTable.findUniqueObject(reqUID);
    Date timeReceived = new Date();
    req.setTimeReceived(timeReceived);
    HbReqResponse response = (HbReqResponse)hbReq.getResponse();
    req.setStatus(response.getStatus());
    req.setRoundTripTime(timeReceived.getTime() - req.getTimeSent().getTime());
    bb.publishChange(req);
    System.out.println("\nHeartbeatRequesterPlugin.updateHeartbeatRequest: published changed HeartbeatRequest = " + req);
  }

  // produce HeartbeatHealthReports from ACCEPTED HeartbeatRequests
  private void prepareHealthReports() {
    long timeUntilNextAlarm = Long.MAX_VALUE; // milliseconds until next call to prepareHealthReports
    Iterator iter = heartbeatRequestSub.getCollection().iterator();
    while (iter.hasNext()) {
      HeartbeatRequest req = (HeartbeatRequest)iter.next();
      int status = req.getStatus();
      if (status == HeartbeatRequest.ACCEPTED) {
        // Is it time yet to send a health report for this request?
        // first, calculate when last heartbeat should have arrived
        long start = req.getTimeReceived().getTime(); //baseline for heartbeats for this request
        long now = System.currentTimeMillis();
        long hbFreq = req.getHbFrequency();
        long lastHbDue = start + ((now - start)/hbFreq)*hbFreq;
        // now calculate when to send report 
        float percentOutOfSpec = req.getPercentOutOfSpec();
        long reportDue = lastHbDue + (long)(hbFreq * (percentOutOfSpec / 100));
        if (reportDue > now){ // report isn't due yet, so just set next wakeup time
          long timeUntilReportDue = reportDue - now;    
          if (timeUntilNextAlarm > timeUntilReportDue) timeUntilNextAlarm = timeUntilReportDue;
       } else { // now is the time to send the report, if needed
          // first, set next wakeup time
          long nextReportDue = reportDue + hbFreq;
          long timeUntilNextReportDue = nextReportDue - now;  
          if (timeUntilNextAlarm > timeUntilNextReportDue) timeUntilNextAlarm = timeUntilNextReportDue;
          // now check to see how late the heartbeat is
          MessageAddress target = req.getTarget();  // change here for multiple targets
          Date lastHbDate = (Date)hbTable.get(target);
          long lastHbTime = lastHbDate.getTime();
          long outOfSpec = lastHbTime - lastHbDue;
          float thisPercentOutOfSpec =  ((float)outOfSpec / (float)hbFreq) * 100.0f;
          // send report if requester wants report whether in or out of spec
          // or if the heartbeat is enough out of spec
          if ( req.getOnlyOutOfSpec() == false ||  
               thisPercentOutOfSpec > percentOutOfSpec ) {
            HeartbeatEntry [] entries = new HeartbeatEntry[1];
            entries[0] = new HeartbeatEntry(target, lastHbDate, thisPercentOutOfSpec);
            HeartbeatHealthReport report = new HeartbeatHealthReport(entries);
            bb.publishAdd(report);
            System.out.println("\nHeartbeatRequesterPlugin.prepareHealthReports:" +
                               " published new HeartbeatHealthReport = " + report);
          }   
        }    
      }
    } 
    if (timeUntilNextAlarm != Long.MAX_VALUE) {
      nextAlarm = new SendHealthReportsAlarm(timeUntilNextAlarm);
      alarmService.addRealTimeAlarm(nextAlarm);
    }
  }

  protected void setupSubscriptions() {
    reqTable = new UniqueObjectSet();
    hbTable = new Hashtable();
    reportTable = new Hashtable();
    bb = getBlackboardService();
    heartbeatRequestSub = (IncrementalSubscription)bb.subscribe(HeartbeatRequestPred);
    hbReqSub = (IncrementalSubscription)bb.subscribe(hbReqPred);
  }

  protected void execute() {
    // check for responses from HeartbeatServerPlugin
    // the first response will cause the status in the HeartbeatRequest to be updated
    // all responses except REFUSED are considered heartbeats and are entered in hbTable
    // Eventually, heartbeats won't show up this way, as they will be filtered in MTS,
    // and fed into the Metrics Service and we will get them there.
    Iterator iter = hbReqSub.getChangedCollection().iterator();
    while (iter.hasNext()) {
      HbReq hbReq = (HbReq)iter.next();
      System.out.println("\nHeartbeatRequesterPlugin.execute: changed HbReq received = " + hbReq);
      MessageAddress myAddr = getBindingSite().getAgentIdentifier();
      if (hbReq.getSource().equals(myAddr)) {
        HbReqResponse response = (HbReqResponse)hbReq.getResponse();
        int status = response.getStatus();
        Date now = new Date();
        switch (status) {
          case HeartbeatRequest.ACCEPTED:
            hbTable.put(response.getResponder(), now);
            HbReqContent content = (HbReqContent)hbReq.getContent();
            UID reqUID = content.getHeartbeatRequestUID();
            reportTable.put(reqUID, now);
            // figure out when the alarm should be set
            HeartbeatRequest req = (HeartbeatRequest)reqTable.findUniqueObject(reqUID);
            long freq = req.getHbFrequency();
            float percentOutOfSpec = req.getPercentOutOfSpec();
            long fromNow = freq + (long)(freq * (percentOutOfSpec / 100));
            if (nextAlarm == null){    // if no alarm set yet, this is the first
                                       // ACCEPTED request, so set one to 
                                       // (freq + (freq * percentOutOfSpec)) from now
              nextAlarm = new SendHealthReportsAlarm(fromNow);
              alarmService.addRealTimeAlarm(nextAlarm);
            } else { // an alarm is already set - figure out if its soon enough
              long fromNowTime = now.getTime() + fromNow;
              if (nextAlarm.getExpirationTime() > fromNowTime) {
                nextAlarm.cancel();
                nextAlarm = new SendHealthReportsAlarm(fromNow);
                alarmService.addRealTimeAlarm(nextAlarm);
              }
            } // falls through
          case HeartbeatRequest.REFUSED:
            updateHeartbeatRequest(hbReq);
            break;
          case HbReqResponse.HEARTBEAT:
            hbTable.put(response.getResponder(), now);
            break;
          default:
            throw new RuntimeException("illegal status = " + status);
        }
      }
    }
    // process NEW HeartbeatRequests
    iter = heartbeatRequestSub.getAddedCollection().iterator();
    while (iter.hasNext()) {
      HeartbeatRequest req = (HeartbeatRequest)iter.next();
      int status = req.getStatus();
      if (status == HeartbeatRequest.NEW) {
        System.out.println("\nHeartbeatRequesterPlugin.prepareHealthReports: new HeartbeatRequest received = " + req);
        MessageAddress source = getBindingSite().getAgentIdentifier();
        MessageAddress target = req.getTarget();
        UID reqUID = req.getUID();
        reqTable.add(req);
        req.setStatus(HeartbeatRequest.SENT);
        req.setTimeSent(new Date());
        HbReqContent content = new HbReqContent(reqUID, 
                                                req.getReqTimeout(), 
                                                req.getHbFrequency(), 
                                                req.getHbTimeout());
        HbReq hbReq = new HbReq(getUIDService().nextUID(), 
                                source, 
                                target, 
                                content, 
                                null);
        bb.publishAdd(hbReq);
        bb.publishChange(req);
        System.out.println("\nHeartbeatRequesterPlugin.execute: published new HbReq = " + hbReq);
        System.out.println("\nHeartbeatRequesterPlugin.execute: published changed HeartbeatRequest = " + req);
      }
    }
  }

  private UIDService UIDService;
   
  public UIDService getUIDService() {
      return this.UIDService;
  }
   
  public void setUIDService(UIDService UIDService) {
      this.UIDService = UIDService;
  }

}
