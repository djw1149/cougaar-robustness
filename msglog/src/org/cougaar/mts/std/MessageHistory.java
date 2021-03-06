/*
 * <copyright>
 *  Copyright 2001-2002, 2004 Object Services and Consulting, Inc. (OBJS),
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
 * 01 Nov 2002: Cap the depth of history, add get percent successful sends. (OBJS)
 * 18 Apr 2002: Update from Cougaar 9.0.0 to 9.1.x (OBJS)
 * 21 Mar 2002: Update from Cougaar 8.6.2.x to 9.0.0 (OBJS)
 * 29 Nov 2001: Change getting message number to MessageAckingAspect way. (OBJS)
 * 24 Sep 2001: Updated from Cougaar 8.4 to 8.4.1 (OBJS)
 * 18 Sep 2001: Updated from Cougaar 8.3.1 to 8.4 (OBJS)
 * 08 Jul 2001: Created. (OBJS)
 */

package org.cougaar.mts.std;

import java.io.Serializable;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Hashtable;
import java.util.Vector;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.mts.base.AttributedMessage;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;

/** 
 *  Store records of the activity of messages as they go thru the MessageTransport 
 *  system. 
**/

class MessageHistory
{
  public static final int MAX_TRANSPORT_HISTORY = 5;
  public static final int MAX_MESSAGE_HISTORY = 200;  // bit of a HACK

  static final sendsClass sends = new sendsClass();
  static final receivesClass receives = new receivesClass();

  static final Logger log = Logging.getLogger(MessageHistory.class);

  public static class sendsClass
  {
    private static final Hashtable msgTable = new Hashtable();
    private static final Hashtable idTable = new Hashtable();
    
    public static synchronized void put (MessageHistory.SendRecord rec)
    {
      String mkey = rec.messageKey;
      Vector v = (Vector) msgTable.get (mkey);
      if (v == null) v = new Vector();
      else if (v.contains (rec)) return;  // no duplicates
      v.add (rec);
      if (log.isDebugEnabled())
	  log.debug("put: rec="+rec);
      if (v.size() > MAX_MESSAGE_HISTORY) v.remove (0); // delete the oldest
      msgTable.put (mkey, v);

      Integer tkey = new Integer (rec.transportID);
      v = (Vector) idTable.get (tkey);
      if (v == null) v = new Vector();
      else if (v.contains (rec)) return;  // no duplicates
      v.add (rec);
      if (v.size() > MAX_TRANSPORT_HISTORY) v.remove (0); // delete the oldest
      idTable.put (tkey, v);
    }

    public static MessageHistory.SendRecord get (int id, AttributedMessage msg)
    {
      MessageHistory.SendRecord[] recs = getByMessage (msg);

      for (int i=0; i<recs.length; i++)
      {
        if (recs[i].transportID == id) return recs[i];
      }      

      return null;
    }

    public static MessageHistory.SendRecord[] getByMessage (AttributedMessage msg)
    {
      String mkey = getMessageKey (msg);
      Vector v = (Vector) msgTable.get (mkey);
      if (v == null) return new MessageHistory.SendRecord[0];
      return (MessageHistory.SendRecord[]) v.toArray (new MessageHistory.SendRecord[v.size()]); 
    }

    public static MessageHistory.SendRecord[] getByTransportID (int id)
    {
      Vector v = (Vector) idTable.get (new Integer(id));
      if (v == null) return new MessageHistory.SendRecord[0];
      return (MessageHistory.SendRecord[]) v.toArray (new MessageHistory.SendRecord[v.size()]); 
    }

    public static MessageHistory.SendRecord getLastRecByTransportID (int id)
    {
      Vector v = (Vector) idTable.get (new Integer(id));
      return (v != null ? (MessageHistory.SendRecord) v.lastElement() : null);
    }

    public boolean hasHistory (AttributedMessage msg)
    {
      String mkey = getMessageKey (msg);
      Vector v = (Vector) msgTable.get (new Integer (mkey));
      return (v != null && v.size() > 0);
    }

    public static int countSuccessfulSendsByMessage (AttributedMessage msg)
    {
      int count = 0;
      MessageHistory.SendRecord[] recs = getByMessage (msg);

      for (int i=0; i<recs.length; i++) 
      {
        if (recs[i].sendTimestamp > 0 && recs[i].success == true) count++;
      }

      return count;
    }      

    public static int countSuccessfulSendsByTransportID (int id)
    {
      int count = 0;
      MessageHistory.SendRecord[] recs = getByTransportID (id);

      for (int i=0; i<recs.length; i++) 
      {
        if (recs[i].sendTimestamp > 0 && recs[i].success == true) count++;
      }

      return count;
    }      

    private static int getHistoryDepthByTransportID (int id)
    {
      Vector v = (Vector) idTable.get (new Integer(id));
      return (v != null ? v.size() : 0);
    }

    public boolean hasHistory (int id)
    {
      return getHistoryDepthByTransportID (id) > 0;
    }

    public static float getPercentSuccessfulSendsByTransportID (int id)
    {
      int maxCount = getHistoryDepthByTransportID (id);
      if (maxCount < 1) return 0.0f;
      int numSuccess = countSuccessfulSendsByTransportID (id);
      return ((float) numSuccess/(float) maxCount);
    }

    public static boolean allSendsUnsuccessfulByMessage (AttributedMessage msg)
    {
      int count = 0;
      MessageHistory.SendRecord[] recs = getByMessage (msg);

      for (int i=0; i<recs.length; i++) 
      {
        if (recs[i].sendTimestamp > 0 && recs[i].success == false) count++;
      }

      //  If there were no sends then return false

      return (recs.length > 0 ? (count == recs.length) : false);
    }      

    public static int countUnsuccessfulSendsByTransportID (int id)
    {
      int count = 0;
      MessageHistory.SendRecord[] recs = getByTransportID (id);

      for (int i=0; i<recs.length; i++) 
      {
        if (recs[i].sendTimestamp > 0 && recs[i].success == false) count++;
      }

      return count;
    }

    public static boolean lastSendSuccessfulByTransportID (int id)
    {
      MessageHistory.SendRecord rec = getLastRecByTransportID (id);
      return (rec != null ? rec.success : false);
    }      

    /*
     *  Get a snapshot of the history of sends to each agent over each 
     *  protocol over the previous millis milliseconds.
     */
    private static synchronized Hashtable snapshot (long millis) {
        long start = System.currentTimeMillis() - millis;
        Hashtable agentTbl = null;
	Enumeration e = msgTable.elements();
	while (e.hasMoreElements()) {
	    Vector recs = (Vector)e.nextElement();
	    AgentEntry agentEntry = null;
	    boolean newAgentEntry = false;
	    Iterator iter = recs.iterator();
	    while (iter.hasNext()) {
		SendRecord rec = (SendRecord)iter.next();
		if (log.isDebugEnabled())
		    log.debug("snapshot: rec="+rec);
		if (rec.sendTimestamp >= start) {
		    String dest = rec.destination.getAddress();
		    if (agentTbl != null)
			agentEntry = (AgentEntry)agentTbl.get(dest);
		    if (agentEntry == null) {
			agentEntry = new AgentEntry(dest);
			newAgentEntry = true;
		    }
		    Hashtable protocolTbl = agentEntry.protocolTbl;
		    int protocolID = rec.transportID;
		    Integer pI = new Integer(protocolID);
		    ProtocolEntry protocolEntry = 
			(ProtocolEntry)protocolTbl.get(pI);
		    if (protocolEntry == null) {
			protocolEntry = new ProtocolEntry(protocolID); 
			protocolTbl.put(pI, protocolEntry);
		    }
               	    ++protocolEntry.sends;
                    if (rec.success) ++protocolEntry.successes;
		}
	    }
	    if (newAgentEntry == true &&
		agentEntry.protocolTbl.size() > 0) {
		if (agentTbl == null) 
		    agentTbl = new Hashtable();
		agentEntry.timestamp = System.currentTimeMillis();
		agentTbl.put(agentEntry.agent,agentEntry);
	    }
	}
	return agentTbl;
    }

  }

  public static synchronized Hashtable snapshotSendHistory (long millis) {
      return sendsClass.snapshot(millis);
  }
      
  public static class AgentEntry implements Serializable {
      String agent;
      Hashtable protocolTbl;
      long timestamp;
      AgentEntry (String addr) {
	  agent = addr;
	  protocolTbl = new Hashtable();
      }
      public String toString () {
	  return "<AgentEntry "+agent+","+protocolTbl+">";
      }
  }
	  
  public static class ProtocolEntry implements Serializable {
      int protocolID;
      char linkLetter;
      int sends;
      int successes;
      ProtocolEntry (int id) {
	  protocolID = id;
	  linkLetter = 
	      AdaptiveLinkSelectionPolicy.getLinkLetter(AdaptiveLinkSelectionPolicy.getTransportName(id));
      }
      SuccessMetric getMetric () {
	  return new SuccessMetric(protocolID, sends, successes);
      }
      public String toString () {
	  return "<ProtocolEntry protocol="+linkLetter+",sends="+sends+",successes="+successes+">";
      }
  }

  public static class SuccessMetric implements Serializable {
      int protocolID;
      float successRate;
      SuccessMetric(int prot, int sends, int successes) {
	  protocolID = prot;
	  successRate = ((float)successes)/sends;
      }
  }
	  
  public static class receivesClass
  {
    private static final Hashtable msgTable = new Hashtable();
    private static final Hashtable idTable = new Hashtable();
    
    public static synchronized void put (MessageHistory.ReceiveRecord rec)
    {
      String mkey = rec.messageKey;
      Vector v = (Vector) msgTable.get (mkey);
      if (v == null) v = new Vector();
      else if (v.contains (rec)) return;  // no duplicates
      v.add (rec);
      if (v.size() > MAX_MESSAGE_HISTORY) v.remove (0); // delete the oldest
      msgTable.put (mkey, v);

      Integer tkey = new Integer (rec.transportID);
      v = (Vector) idTable.get (tkey);
      if (v == null) v = new Vector();
      else if (v.contains (rec)) return;  // no duplicates
      v.add (rec);
      if (v.size() > MAX_TRANSPORT_HISTORY) v.remove (0); // delete the oldest
      idTable.put (tkey, v);
    }

    public static MessageHistory.ReceiveRecord get (int id, AttributedMessage msg)
    {
      MessageHistory.ReceiveRecord[] recs = getByMessage (msg);

      for (int i=0; i<recs.length; i++)
      {
        if (recs[i].transportID == id) return recs[i];
      }      

      return null;
    }

    public static MessageHistory.ReceiveRecord[] getByMessage (AttributedMessage msg)
    {
      String mkey = getMessageKey (msg);
      Vector v = (Vector) msgTable.get (mkey);
      if (v == null) return new MessageHistory.ReceiveRecord[0];
      return (MessageHistory.ReceiveRecord[]) v.toArray (new MessageHistory.ReceiveRecord[v.size()]); 
    }

    public static MessageHistory.ReceiveRecord[] getByTransportID (int id)
    {
      Vector v = (Vector) idTable.get (new Integer(id));
      if (v == null) return new MessageHistory.ReceiveRecord[0];
      return (MessageHistory.ReceiveRecord[]) v.toArray (new MessageHistory.ReceiveRecord[v.size()]); 
    }
  }

  private static class BaseRecord
  {
    public int transportID;
    public String messageKey;
    public boolean success;
  }

  public static class SendRecord extends BaseRecord
  {
    public MessageAddress destination;
    public long routeTimestamp;
    public long sendTimestamp;
    public long abandonTimestamp;

    public SendRecord (int transportID, AttributedMessage msg)
    {
      this.transportID = transportID;
      messageKey = getMessageKey (msg);
      destination = msg.getTarget();
    }

    public String toString ()
    {
      return "[" + "\n" +
             "      transportID= " + transportID + "\n" +
             "       messageKey= " + messageKey + "\n" +
             "      destination= " + destination + "\n" +
             "   routeTimestamp= " + toDate (routeTimestamp) + "\n" +
             "    sendTimestamp= " + toDate (sendTimestamp) + "\n" +
             " abandonTimestamp= " + toDate (abandonTimestamp) + "\n" +
             "          success= " + success + "\n" +
             "]";
    }
  }

  public static class ReceiveRecord extends BaseRecord
  {
    public MessageAddress origination; 
    public long receiveTimestamp;

    public String toString ()
    {
      return "[" + "\n" +
             "      transportID= " + transportID + "\n" +
             "       messageKey= " + messageKey + "\n" +
             "      origination= " + origination + "\n" +
             " receiveTimestamp= " + toDate (receiveTimestamp) + "\n" +
             "          success= " + success + "\n" +
             "]";
    }
  }

  private static String getMessageKey (AttributedMessage msg)
  {
    return ""+ MessageUtils.getMessageNumber(msg) +"::"+ MessageUtils.getToAgent(msg);
  }

  private static long timeNow ()
  {
    return System.currentTimeMillis();
  }

  private static String toDate (long time)
  {
    if (time == 0) return "0";

    //  Return date string with milliseconds

    String date = (new Date(time)).toString();
    String d1 = date.substring (0, 19);
    String d2 = date.substring (19);
    long ms = time % 1000;
    return d1 + "." + ms + d2;
  }
}
