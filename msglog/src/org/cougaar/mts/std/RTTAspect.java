/*
 * <copyright>
 *  Copyright 2002,2004 Object Services and Consulting, Inc. (OBJS),
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
 *
 * CHANGE RECORD 
 *  1 Mar 2004: Port to 11.0. (OBJS)
 * 25 Oct 2002: Updates for setting times in protocols. (OBJS)
 * 21 Jul 2002: Created. (OBJS)
 */

package org.cougaar.mts.std;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageAttributes;
import org.cougaar.mts.base.AttributedMessage;
import org.cougaar.mts.base.DestinationLink;
import org.cougaar.mts.base.DestinationLinkDelegateImplBase;
import org.cougaar.mts.base.MessageDeliverer;
import org.cougaar.mts.base.MessageDelivererDelegateImplBase;
import org.cougaar.mts.base.StandardAspect;
import org.cougaar.mts.base.CommFailureException;
import org.cougaar.mts.base.MisdeliveredMessageException;
import org.cougaar.mts.base.NameLookupException;
import org.cougaar.mts.base.UnregisteredNameException;
import org.cougaar.mts.std.acking.RunningAverage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

/**
 **  Measure the round trip time (RTT) for messages.  The commRTT is defined to
 **  be the time a message takes to travel from this node to its destination
 **  node plus the time it takes for another message (of no necessary relation
 **  to the first) to travel from the destination node back to this one.  The
 **  nodeRTT is defined to be commRTT + nodeTime, where nodeTime is defined to
 **  be the average time between the last arriving and first leaving messages
 **  of a node.
 **
 **  Note: There is basically a "one network" assumption here currently, ie.
 **  that all messages to all agents on a node travel over the same network
 **  (although if extra networks are confined to individual protocols then no
 **  problem). When this situation changes this code will need to be updated.
 **/

public class RTTAspect extends StandardAspect implements Serializable  // for embedded class
{
  private static final String RTT_SEND_TIME = "RTT_SendTime";
  private static final String RTT_RECV_TIME = "RTT_RecvTime";
  private static final String RTT_DATA =      "RTT_Data";

  private static final int c_samplePoolsize, n_samplePoolsize;
  private static final int c_startDelay, n_startDelay;
  private static final float c_percentChangeLimit, n_percentChangeLimit;
  private static final int c_changeLimitDelay, n_changeLimitDelay;

  private final ServiceImpl serviceImpl = new ServiceImpl();

  private static final Hashtable initialCommRTTsTable = new Hashtable();
  private static final Hashtable latestReceptionsTable = new Hashtable();
  private static final Hashtable roundtripTable = new Hashtable();
  private static final Comparator timeSort = new TimeSort();

  private static int initialNodeTime = 0;

  static
  {
    //  Read external properties

    String s = "org.cougaar.message.transport.aspects.rtt.comm.samplePoolsize";
    c_samplePoolsize = Integer.valueOf(System.getProperty(s,"10")).intValue();

    s = "org.cougaar.message.transport.aspects.rtt.comm.startDelay";
    c_startDelay = Integer.valueOf(System.getProperty(s,"2")).intValue();

    s = "org.cougaar.message.transport.aspects.rtt.comm.percentChangeLimit";
    c_percentChangeLimit = Float.valueOf(System.getProperty(s,"0.25")).floatValue();

    s = "org.cougaar.message.transport.aspects.rtt.comm.changeLimitDelay";
    c_changeLimitDelay = Integer.valueOf(System.getProperty(s,"20")).intValue();

    s = "org.cougaar.message.transport.aspects.rtt.node.samplePoolsize";
    n_samplePoolsize = Integer.valueOf(System.getProperty(s,"10")).intValue();

    s = "org.cougaar.message.transport.aspects.rtt.node.startDelay";
    n_startDelay = Integer.valueOf(System.getProperty(s,"2")).intValue();

    s = "org.cougaar.message.transport.aspects.rtt.node.percentChangeLimit";
    n_percentChangeLimit = Float.valueOf(System.getProperty(s,"0.50")).floatValue();

    s = "org.cougaar.message.transport.aspects.rtt.node.changeLimitDelay";
    n_changeLimitDelay = Integer.valueOf(System.getProperty(s,"5")).intValue();
  }

  public RTTAspect () 
  {}

  //  Aspect delegation - RTT outbound to DestinationLink, inbound to MessageDeliverer

  public Object getDelegate (Object delegate, Class type) 
  {
    if (type == DestinationLink.class) 
    {
      //  RTTs not maintained for local messages

      DestinationLink link = (DestinationLink) delegate;
      String linkProtocol = link.getProtocolClass().getName();
      
      if (!linkProtocol.equals ("org.cougaar.mts.base.LoopbackLinkProtocol")) 
      {
        return new RTTOutbound (link);
      }
    }
 
    return null;
  }

  public Object getReverseDelegate (Object delegate, Class type) 
  {
    if (type == MessageDeliverer.class) 
    {
      return new RTTInbound ((MessageDeliverer) delegate);
    }
 
    return null;
  }

  //  Provide external RTT service 

  public void load () 
  {
    super.load();
    Provider provider = new Provider();
    getServiceBroker().addService (RTTService.class, provider);
  }

  private class Provider implements ServiceProvider 
  {
    public Object getService (ServiceBroker sb, Object requestor, Class serviceClass) 
    {
      if (serviceClass == RTTService.class) return serviceImpl;
      return null;
    }

    public void releaseService (ServiceBroker sb, Object requestor, Class serviceClass, Object service)
    {}
  }

  private class ServiceImpl implements RTTService, Serializable  // enclosing classes must be serializable too
  {
    public void setInitialCommRTTForLinkPair (DestinationLink sendLink, DestinationLink recvLink, int rtt)
    {
      setTheInitialCommRTTForLinkPair (getName(sendLink), getName(recvLink), rtt);
    }
  
    public void setInitialCommRTTForLinkPair (String sendLink, String recvLink, int rtt)
    {
      setTheInitialCommRTTForLinkPair (sendLink, recvLink, rtt);
    }

    public boolean isSomeCommRTTStartDelaySatisfied (DestinationLink sendLink, String node)
    {
      return isThereSomeCommRTTStartDelaySatisfied (getName(sendLink), node);
    }

    public boolean isCommRTTStartDelaySatisfied (DestinationLink sendLink, String node, DestinationLink recvLink)
    {
      return isTheCommRTTStartDelaySatisfied (getName(sendLink), node, getName(recvLink));
    }

    public float getHighestCommRTTPercentFilled (DestinationLink sendLink, String node)
    {
      return highestCommRTTPercentFilled (getName(sendLink), node);
    }

    public float getCommRTTPercentFilled (DestinationLink sendLink, String node, DestinationLink recvLink)
    {
      return commRTTPercentFilled (getName(sendLink), node, getName(recvLink));
    }

    public void setInitialNodeTime (int nodeTime)
    {
      setTheInitialNodeTime (nodeTime);
    }
  
    public int getBestCommRTTForLink (DestinationLink link, String node)
    {
      return getTheBestCommRTTForLink (link, node);
    }

    public int getBestFullRTTForLink (DestinationLink link, String node)
    {
      return getTheBestFullRTTForLink (link, node);
    }

    public void updateInbandRTT (DestinationLink sendLink, String node, int rtt)
    {
      updateRTT (sendLink, node, rtt);
    }

    public void updateInbandRTT (String node, String recvLink, int rtt)
    {
      updateRTT (node, recvLink, rtt);
    }

    public void setMessageSendTime (AttributedMessage msg, long time)
    {
      setRTTSendTimeAttribute (msg, time);
    }

    public void setMessageReceiveTime (AttributedMessage msg, long time)
    {
      setRTTRecvTimeAttribute (msg, time);
    }
  }

  //  Implementation

  private static class MessageTimes
  {
    public String sendLink;
    public long sendTime;     // by sender's clock
    public long receiveTime;  // by receiver's clock

    public MessageTimes (String sendLink, long sendTime, long receiveTime)
    {
      this.sendLink = sendLink;
      this.sendTime = sendTime;
      this.receiveTime = receiveTime;
    }

    public MessageTimes (MessageTimes mt)
    {
      sendLink = mt.sendLink;
      sendTime = mt.sendTime;
      receiveTime = mt.receiveTime;
    }

    public void setTimes (long sendTime, long receiveTime)
    {
      this.sendTime = sendTime;
      this.receiveTime = receiveTime;
    }

    public String toString ()
    {
      return "sendLink=" +sendLink+ " sendTime=" +sendTime+ " receiveTime=" +receiveTime;
    }
  }

  private void recordLatestMessageReception (String sendNode, String sendLink, long sendTime, long receiveTime)
  {
    //  Sanity check

    if (sendNode == null || sendLink == null) 
    {
      loggingService.error ("Missing sendNode or sendLink (missing msg attribute?)");
      return;
    }

    //  Record message reception if it is the latest

    synchronized (latestReceptionsTable)
    {
      Hashtable nodeTable = (Hashtable) latestReceptionsTable.get (sendNode);

      if (nodeTable == null)
      {
        nodeTable = new Hashtable();
        latestReceptionsTable.put (sendNode, nodeTable);
      }

      MessageTimes latest = (MessageTimes) nodeTable.get (sendLink);
      if (latest != null && latest.receiveTime > receiveTime) return;  // not latest for link
      if (latest == null) nodeTable.put (sendLink, new MessageTimes (sendLink, sendTime, receiveTime));
      else latest.setTimes (sendTime, receiveTime);
    }
  }

  private MessageTimes[] getLatestMessageReceptions (String sendNode)
  {
    //  Sanity check

    if (sendNode == null)
    {
      loggingService.error ("Missing sendNode (missing msg attribute?)");
      return new MessageTimes[0];
    }

    //  Get latest message receptions for all links of node

    synchronized (latestReceptionsTable)
    {
      Hashtable table = (Hashtable) latestReceptionsTable.get (sendNode);
      MessageTimes latest[] = new MessageTimes[(table != null ? table.size() : 0)];

      if (latest.length > 0)
      {
        //  We return copies

        int i = 0;

        for (Enumeration e=table.elements(); e.hasMoreElements(); )
        {
          latest[i++] = new MessageTimes ((MessageTimes)e.nextElement());
        }
      }

      return latest;
    }
  }

  private static class HalfRTT implements Serializable  // enclosing classes must be serializable too
  {
    private final String sendLink;
    private final long sendTime;
    private final long receiveTime;

    public HalfRTT (String sendLink, long sendTime, long receiveTime)
    {
      this.sendLink = sendLink;
      this.sendTime = sendTime;
      this.receiveTime = receiveTime;
    }

    public HalfRTT (HalfRTT hr)
    {
      this.sendLink = hr.sendLink;
      this.sendTime = hr.sendTime;
      this.receiveTime = hr.receiveTime;
    }

    public String getSendLink ()
    {
      return sendLink;
    }

    public long getSendTime ()
    {
      return sendTime;
    }

    public long getReceiveTime ()
    {
      return receiveTime;
    }

    public String toString ()
    {
      return "sendLink=" +sendLink+ " sendTime=" +sendTime+ " receiveTime=" +receiveTime;
    }
  }

  private static void setRTTSendTimeAttribute (AttributedMessage msg, long time)
  {
    msg.setAttribute (RTT_SEND_TIME, new Long (time));
  }

  private static long getRTTSendTimeAttribute (AttributedMessage msg)
  {
    Long time = (Long) msg.getAttribute (RTT_SEND_TIME);
    return (time != null ? time.longValue() : 0);
  }

  private static void setRTTRecvTimeAttribute (AttributedMessage msg, long time)
  {
    msg.setAttribute (RTT_RECV_TIME, new Long (time));
  }

  private static long getRTTRecvTimeAttribute (AttributedMessage msg)
  {
    Long time = (Long) msg.getAttribute (RTT_RECV_TIME);
    return (time != null ? time.longValue() : 0);
  }

  public static Vector getRTTDataAttribute (AttributedMessage msg)
  {
    return (Vector) msg.getAttribute (RTT_DATA);
  }

  public static void setRTTDataAttribute (AttributedMessage msg, Vector rttData)
  {
    msg.setAttribute (RTT_DATA, rttData);
  }

  public static Vector cloneRTTData (Vector rttData)
  {
    if (rttData == null) return null;

    Vector v = new Vector();

    for (Enumeration e=rttData.elements(); e.hasMoreElements(); )
      v.add (new HalfRTT ((HalfRTT) e.nextElement()));

    return v;
  }

  private class RTTOutbound extends DestinationLinkDelegateImplBase 
  {
    private DestinationLink link;
 
    public RTTOutbound (DestinationLink link) 
    {
      super (link);
      this.link = link;
    }

    public MessageAttributes forwardMessage (AttributedMessage msg) 
      throws UnregisteredNameException, NameLookupException, 
             CommFailureException, MisdeliveredMessageException
    {
      //  Get a the message times for the latest messages to arrive
      //  on this node.

      String node = MessageUtils.getToAgentNode (msg);
      MessageTimes times[] = getLatestMessageReceptions (node);

      //  Set a send time for the message.  Kind of a hack, but need BBN 
      //  support to have link protocols set the send time at the time a 
      //  message is actually sent.  On the other hand, travel time thru
      //  MTS can be considered a legitimate part of the RTT. 
      //
      //  NOTE: It is important that this send time be established after 
      //  getting the latest receptions (above), to avoid the case where
      //  the latest receptions are possibly later than the send time,
      //  resulting in negative node times in the calculations below.

      setRTTSendTimeAttribute (msg, now());

      //  We now calculate the first half of the RTTs and store the results
      //  in the outgoing message.  On the other node these results will be
      //  used to calculate RTTs for a set of protocol links.
      
      int MAX_RTTs = 5;  // HACK: arbitrary for now
      Vector v = null;

      if (times.length > 0)
      {
        //  NOTE:  We sort the latest receptions so that are ordered latest
        //  first.  This is so if we cannot send them all (MAX_RTTs limit),
        //  we send the freshest.  Also, the other side is depending that
        //  the first entry is the latest of all, as it uses that for the
        //  "real" nodeTime - the nodeTime that exists between the latest
        //  msg receive and latest send (this msg).  All other node times,
        //  while good for calculating commRTTs, are considered "virtual",
        //  and not representative of the best "response" time of the node.

        Arrays.sort (times, timeSort);  // latest receives first
        v = new Vector();

        for (int i=0; i<MAX_RTTs && i<times.length; i++) // at most MAX_RTTs
        {
          v.add (new HalfRTT (times[i].sendLink, times[i].sendTime, times[i].receiveTime));
        }
      }

      setRTTDataAttribute (msg, v);

      //  Send the message on its way

      return link.forwardMessage (msg);
    }
  }

  class RTTInbound extends MessageDelivererDelegateImplBase 
  {
    MessageDeliverer deliverer;

    public RTTInbound (MessageDeliverer deliverer)
    {
      super (deliverer);
      this.deliverer = deliverer;
    }

    public MessageAttributes deliverMessage (AttributedMessage msg, MessageAddress dest) throws MisdeliveredMessageException
    {
      //  RTTs not maintained for local messages

      if (MessageUtils.isLocalMessage (msg)) return super.deliverMessage (msg, dest);

      //  Get/Set the receive time for the message

      long receiveTime = getRTTRecvTimeAttribute (msg);
      if (receiveTime == 0) receiveTime = now();

      //  Record this latest message reception

      long sendTime = getRTTSendTimeAttribute (msg);
      if (sendTime == 0) return deliverer.deliverMessage (msg, dest);  // missing data

      String sendNode = MessageUtils.getFromAgentNode (msg);
      String sendLink = MessageUtils.getSendProtocolLink (msg);

      recordLatestMessageReception (sendNode, sendLink, sendTime, receiveTime);     

      //  Now we turn around and view this received message as completing a
      //  round trip from us to the node we got it from (now the destination
      //  node).  We take the first half roundtrip times in the message and
      //  make real RTTs out of them and store them away.

      Vector v = getRTTDataAttribute (msg);

      if (v != null)
      {
        String destinationNode = sendNode;
        String receiveLink = convertOut2In (sendLink);
        long secondHalfSendTime = sendTime;

        for (Enumeration e=v.elements(); e.hasMoreElements(); )
        {
          HalfRTT firstHalf = (HalfRTT) e.nextElement();
          sendLink = firstHalf.getSendLink();
          sendTime = firstHalf.getSendTime();
          long firstHalfReceiveTime = firstHalf.getReceiveTime();
          long nodeTime = secondHalfSendTime - firstHalfReceiveTime;
          int rtt = (int) (receiveTime - (sendTime + nodeTime));
          updateRTT (sendLink, destinationNode, receiveLink, rtt, firstHalfReceiveTime, nodeTime);
        }
      }

      //  Send the message on its way

      return deliverer.deliverMessage (msg, dest);
    }
  }

  private class NodeTimeAverage
  {
    public RunningAverage nodeTime;
    public long lastNodeTime;

    public NodeTimeAverage (RunningAverage nodeTime, long lastNodeTime)
    {
      this.nodeTime = nodeTime;
      this.lastNodeTime = lastNodeTime;
    }
  }

  private void updateRTT (DestinationLink sendLink, String node, int rtt)
  {
    updateRTT (getName(sendLink), node, convertOut2In(getName(sendLink)), rtt, 0, 0);    
  }

  private void updateRTT (String node, String recvLink, int rtt)
  {
    updateRTT (convertIn2Out(recvLink), node, recvLink, rtt, 0, 0);    
  }

  private void updateRTT (String sendLink, String node, String receiveLink, int rtt,
                          long receiveTime, long nodeTime)
  {
    synchronized (roundtripTable)
    {
      //  Update commRTT

      String key = sendLink +"-"+ node;
      Hashtable commTable = (Hashtable) roundtripTable.get (key);

      if (commTable == null)
      {
        commTable = new Hashtable();
        roundtripTable.put (key, commTable);
      }

      key = receiveLink;
      RunningAverage commRTT = (RunningAverage) commTable.get (key);

      if (commRTT == null)
      {
        //      samplePoolsize:  number of latest samples avg is calc from
        //          startDelay:  throw away initial samples (which may be wildly off)
        //  percentChangeLimit:  limit the amount the latest sample can change avg
        //    changeLimitDelay:  how many samples before change limit kicks in

        commRTT = new RunningAverage (c_samplePoolsize, c_startDelay, c_percentChangeLimit, c_changeLimitDelay);

        int initialCommRTT = getInitialCommRTT (sendLink, receiveLink);
        if (initialCommRTT > 0)  commRTT.setAverage (initialCommRTT);
 
        commTable.put (key, commRTT);
      }

      if (rtt >= 0) commRTT.add (rtt);  // 0 RTTs are possible (ie. RTTs < 1 millisecond)

      //  Update nodeTime

      key = node;
      NodeTimeAverage nodeTimeAvg = (NodeTimeAverage) roundtripTable.get (key);

      if (nodeTimeAvg == null)
      {
        RunningAverage nt = new RunningAverage (n_samplePoolsize, n_startDelay, n_percentChangeLimit, n_changeLimitDelay);

        if (initialNodeTime > 0) nt.setAverage (initialNodeTime);

        nodeTimeAvg = new NodeTimeAverage (nt, 0);
        roundtripTable.put (key, nodeTimeAvg);
      }

      if (receiveTime > nodeTimeAvg.lastNodeTime)  // only the latest once (no virtuals skewing)
      {
        //  Only first-response-to-latest-send node times used; earlier receive times 
        //  and later send times are "virtual" in a way, and only suitable for calculating 
        //  comm RTTs (where node time is factored out).  Granted that the first message
        //  to reach us may not be the first to leave the sending node, but that is ok, as
        //  that is the real "response" time that we are looking for.

        nodeTimeAvg.nodeTime.add (nodeTime);
        nodeTimeAvg.lastNodeTime = receiveTime;  
      }

      //  Show running averages

      if (loggingService.isDebugEnabled())
      {
        StringBuffer buf = new StringBuffer();
        buf.append ("\n\nupdateRTT:");
        buf.append ("\n  destNode= " +node);
        buf.append ("\n  sendLink= " +sendLink);
        buf.append ("\n  recvLink= " +receiveLink);

        int commLast = (int) commRTT.getLastSample();
        int commAvg = (int) Math.rint (commRTT.getAverage());
        int nodeLast = (int) nodeTimeAvg.nodeTime.getLastSample();
        int nodeAvg = (int) Math.rint (nodeTimeAvg.nodeTime.getAverage());

        String b11 = blank1 (commLast, nodeLast);  String b12 = blank1 (commAvg, nodeAvg);
        String b21 = blank2 (commLast, nodeLast);  String b22 = blank2 (commAvg, nodeAvg);

        buf.append ("\n   commRTT= " +b11+commLast+ " avg= " +b12+commAvg);
        buf.append ("\n  nodeTime= " +b21+nodeLast+ " avg= " +b22+nodeAvg + "\n");
        loggingService.debug (buf.toString());
      }
    }    
  }

  private boolean isSomeCommRTTStartDelaySatisfied (String sendLink, String node)
  {
    //  Determine if some combination of the given send link with any receive link is satisfied

    synchronized (roundtripTable)
    {
      String key = sendLink +"-"+ node;
      Hashtable table = (Hashtable) roundtripTable.get (key);
      if (table == null) return false;

      for (Enumeration e=table.elements(); e.hasMoreElements(); )
      {
        RunningAverage commRTT = (RunningAverage) e.nextElement();
        if (commRTT.isStartDelaySatisfied()) return true;
      }
      
      return false;
    }
  }

  private boolean isCommRTTStartDelaySatisfied (String sendLink, String node, String receiveLink)
  {
    synchronized (roundtripTable)
    {
      String key = sendLink +"-"+ node;
      Hashtable table = (Hashtable) roundtripTable.get (key);
      if (table == null) return false;

      key = receiveLink;
      RunningAverage commRTT = (RunningAverage) table.get (key);
      if (commRTT == null) return false;

      return commRTT.isStartDelaySatisfied();
    }
  }

  private float theHighestCommRTTPercentFilled (String sendLink, String node)
  {
    //  Determine the highest percent filled of any combination of the given send link with any receive link

    synchronized (roundtripTable)
    {
      String key = sendLink +"-"+ node;
      Hashtable table = (Hashtable) roundtripTable.get (key);
      if (table == null) return 0.0f;

      float highestPF = 0.0f;

      for (Enumeration e=table.elements(); e.hasMoreElements(); )
      {
        RunningAverage commRTT = (RunningAverage) e.nextElement();
        float pf = commRTT.percentFilled();
        if (pf > highestPF) highestPF = pf;
      }
      
      return highestPF;
    }
  }

  private float theCommRTTPercentFilled (String sendLink, String node, String receiveLink)
  {
    synchronized (roundtripTable)
    {
      String key = sendLink +"-"+ node;
      Hashtable table = (Hashtable) roundtripTable.get (key);
      if (table == null) return 0.0f;

      key = receiveLink;
      RunningAverage commRTT = (RunningAverage) table.get (key);
      if (commRTT == null) return 0.0f;

      return commRTT.percentFilled();
    }
  }

  //  RTTService interface fulfillment methods

  private void setTheInitialCommRTTForLinkPair (String sendLink, String recvLink, int rtt)
  {
    if (loggingService.isDebugEnabled())
    {
      loggingService.debug ("Setting initial commRTT to " +rtt+ " for pair: " +sendLink+ ", " +recvLink);
    }

    synchronized (initialCommRTTsTable)
    {
      String key = sendLink +"-"+ recvLink;
      initialCommRTTsTable.put (key, new Integer (rtt));
    }
  }  

  private static int getInitialCommRTT (String sendLink, String recvLink)
  {
    synchronized (initialCommRTTsTable)
    {
      String key = sendLink +"-"+ recvLink;
      Integer rtt = (Integer) initialCommRTTsTable.get (key);
      if (rtt != null) return rtt.intValue();
      return 0;
    }
  }

  private static void setTheInitialNodeTime (int nodeTime)
  {
    initialNodeTime = nodeTime;
  }
  
  public boolean isThereSomeCommRTTStartDelaySatisfied (String sendLink, String node)
  {
    return isSomeCommRTTStartDelaySatisfied (sendLink, node);
  }

  public boolean isTheCommRTTStartDelaySatisfied (String sendLink, String node, String recvLink)
  {
    return isCommRTTStartDelaySatisfied (sendLink, node, recvLink);
  }

  public float highestCommRTTPercentFilled (String sendLink, String node)
  {
    return theHighestCommRTTPercentFilled (sendLink, node);
  }

  public float commRTTPercentFilled (String sendLink, String node, String recvLink)
  {
    return theCommRTTPercentFilled (sendLink, node, recvLink);
  }

  private static int getTheBestCommRTTForLink (DestinationLink link, String node)
  {
    return getBestRTTForLink (link, node, false);
  }

  private static int getTheBestFullRTTForLink (DestinationLink link, String node)
  {
    return getBestRTTForLink (link, node, true);
  }

  private static int getBestRTTForLink (DestinationLink link, String node, boolean includeNodeTime)
  {
    synchronized (roundtripTable)
    {
      String sendLink = getName (link);
      String key = sendLink +"-"+ node;
      Hashtable commTable = (Hashtable) roundtripTable.get (key);

      if (commTable != null)
      {
        //  Go through the table and find the smallest non-zero average roundtrip time

        int rtt, smallestRTT = Integer.MAX_VALUE;
        double nodeTime = 0.0;

        if (includeNodeTime)
        {
          key = node;
          NodeTimeAverage nodeTimeAvg = (NodeTimeAverage) roundtripTable.get (key);
          if (nodeTimeAvg != null) nodeTime = nodeTimeAvg.nodeTime.getAverage();
        }

        for (Enumeration e=commTable.elements(); e.hasMoreElements(); )
        {
          RunningAverage commRTT = (RunningAverage) e.nextElement();
          rtt = (int) (commRTT.getAverage() + nodeTime);
          if (rtt > 0 && rtt < smallestRTT) smallestRTT = rtt;
        }

        if (smallestRTT != Integer.MAX_VALUE) return smallestRTT;
      }
      
      return 0;  // no non-zero rtt average available
    }
  }

  //  Utility classes and methods

  private static class TimeSort implements Comparator
  {
    public int compare (Object mt1, Object mt2)
    {
      if (mt1 == null)  // drive nulls to bottom (top is index 0)
      {
        if (mt2 == null) return 0;
        else return 1;
      }
      else if (mt2 == null) return -1;

      //  Sort on message receive time

      long t1 = ((MessageTimes) mt1).receiveTime;
      long t2 = ((MessageTimes) mt2).receiveTime;

      if (t1 == t2) return 0;
      return (t1 > t2 ? 1 : -1);  // later times to top
    }

    public boolean equals (Object obj)
    {
      return (this == obj);
    }
  }

  private static String getName (DestinationLink link)
  {
    return link.getProtocolClass().getName();
  }

  private static String convertOut2In (String link)
  {
    //  Turn Outgoing link into Incoming one (depends on link naming convention)

    int i = link.indexOf (".Outgoing");
    if (i > 0) return link.substring(0,i)+ ".Incoming" +link.substring(i+9);
    else return link;  // not a split link
  }

  private static String convertIn2Out (String link)
  {
    //  Turn Incoming link into Outgoing one (depends on link naming convention)

    int i = link.indexOf (".Incoming");
    if (i > 0) return link.substring(0,i)+ ".Outgoing" +link.substring(i+9);
    else return link;  // not a split link
  }

  private static long now ()
  {
    return System.currentTimeMillis();
  }

  private static String blank1 (int v1, int v2)
  {
    int l1 = (new String (""+v1)).length();
    int l2 = (new String (""+v2)).length();
    return (l1 >= l2 ? "" : blankString (l2-l1));
  }

  private static String blank2 (int v1, int v2)
  {
    int l1 = (new String (""+v1)).length();
    int l2 = (new String (""+v2)).length();
    return (l2 >= l1 ? "" : blankString (l1-l2));
  }

  private static String blankString (int len)
  {
    StringBuffer buf = new StringBuffer (len);
    for (int i=0; i<len; i++) buf.append (" ");
    return buf.toString();
  }
}
