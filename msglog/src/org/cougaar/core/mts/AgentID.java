/*
 * <copyright>
 *  Copyright 2002 Object Services and Consulting, Inc. (OBJS),
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
 * 04 Jun 2002: Created. (OBJS)
 */

package org.cougaar.core.mts;

import java.util.Hashtable;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.service.TopologyReaderService;
import org.cougaar.core.service.TopologyEntry;
import org.cougaar.core.thread.Schedulable;


public class AgentID implements java.io.Serializable
{
  private static final int callTimeout;
  private static ThreadService threadService;
  private static TopologyReaderService topologyReaderService;
  private static final Hashtable topologyLookupTable = new Hashtable();

  private String nodeName;
  private String agentName;
  private String agentIncarnation;

  static
  {
    //  Read external properties

    String s = "org.cougaar.message.transport.mts.topology.callTimeout";
    callTimeout = Integer.valueOf(System.getProperty(s,"500")).intValue();
  }

  public AgentID (String nodeName, String agentName, String agentIncarnation)
  {
    this.nodeName = nodeName;
    this.agentName = agentName;
    this.agentIncarnation = agentIncarnation;
  }

  public String getNodeName ()
  {
    return nodeName;
  }

  public void setNodeName (String name)
  {
    nodeName = name;
  }

  public String getAgentName ()
  {
    return agentName;
  }

  public String getAgentIncarnation ()
  {
    return agentIncarnation;
  }

  public long getAgentIncarnationAsLong ()
  {
    try
    {
      return Long.parseLong (agentIncarnation);
    }
    catch (Exception e)
    {
      return -1;
    }
  }

  public String getNumberSequenceKey ()
  {
    return agentName+ "/" +agentIncarnation;
  }

  public String getID ()
  {
    return nodeName+ "/" +agentName+ "/" +agentIncarnation;
  }

  public String toString ()
  {
    return getID();
  }

  public String toShortString ()
  {
    return agentName +"@"+ nodeName;
  }

  public static String makeShortSequenceID (AgentID fromAgent, AgentID toAgent)
  {
    return fromAgent.toShortString() +" to "+ toAgent.toShortString();
  }

  public static String makeAckingSequenceID (AgentID fromAgent, AgentID toAgent)
  {
    return fromAgent +"::"+ toAgent;
  }

  public static TopologyReaderService getTopologyReaderService (Object requestor, ServiceBroker sb)
  {
    if (topologyReaderService != null) return topologyReaderService;
	topologyReaderService = (TopologyReaderService) sb.getService (requestor, TopologyReaderService.class, null);
    return topologyReaderService;
  }

  private static ThreadService getThreadService (Object requestor, ServiceBroker sb)
  {
    if (threadService != null) return threadService;
    threadService = (ThreadService) sb.getService (requestor, ThreadService.class, null);
    return threadService;
  }

  public static AgentID getAgentID (Object requestor, ServiceBroker sb, MessageAddress agent) 
    throws NameLookupException
  {
    return getAgentID (requestor, sb, agent, false);    
  }

  public static AgentID getAgentID (Object requestor, ServiceBroker sb, MessageAddress agent, boolean refreshCache) 
    throws NameLookupException
  {
    if (agent == null) return null;

    //  Make the topology lookup call in another thread

    TopologyReaderService svc = getTopologyReaderService (requestor, sb);
    TopologyLookup topologyLookup = new TopologyLookup (svc, agent, refreshCache);
    String name = "TopologyLookup_" +agent;
    Schedulable thread = getThreadService(requestor,sb).getThread (requestor, topologyLookup, name);
    thread.start();

    //  Wait till we get the topology lookup or we time out

    final int POLL_TIME = 100;
    long callDeadline = now() + callTimeout;
    TopologyEntry entry = null;
    boolean timedOut = false;

    while (true)
    {
      if (topologyLookup.isFinished()) 
      {
        entry = topologyLookup.getLookup();
        break;
      }

      try { Thread.sleep (POLL_TIME); } catch (Exception e) {}

      if (now() > callDeadline) 
      {
        timedOut = true;
        break;
      }
    }

    //  If the call timed out, try a value from our cache, else set the cache

    if (timedOut) 
    {
      entry = (TopologyEntry) getCachedTopologyLookup (agent);
      //System.err.println ("timed topology lookup timed out, using value from cache: " +entry);
    }
    else 
    {
      //System.err.println ("timed topology lookup completed on time");
      cacheTopologyLookup (agent, entry);
    }

    if (entry == null)
    {
      Exception e = new Exception ("Topology service blank on agent! : " +agent);
      throw new NameLookupException (e);
    }

    String nodeName = entry.getNode();
    String agentName = agent.toString();
    String agentIncarnation = "" + entry.getIncarnation();

    return new AgentID (nodeName, agentName, agentIncarnation);
  }

  private static class TopologyLookup implements Runnable
  {
    private TopologyReaderService topologyService;
    private MessageAddress agent;
    private boolean refreshCache;
    private TopologyEntry entry;
    private Exception exception;

    public TopologyLookup (TopologyReaderService svc, MessageAddress agent, boolean refreshCache)
    {
      this.topologyService = svc;
      this.agent = agent;
      this.refreshCache = refreshCache;
    }

    public void run ()
    {
      entry = null;
      exception = null;

      try
      {
        //System.err.println ("timed topology lookup called: agent=" +agent+ " refreshCache=" +refreshCache);

        if (!refreshCache) entry = topologyService.getEntryForAgent    (agent.getAddress());
        else               entry = topologyService.lookupEntryForAgent (agent.getAddress());

        //System.err.println ("timed topology lookup returned");
      }
      catch (Exception e)
      {
        //System.err.println ("timed topology lookup exception: " +stackTraceToString(e));
        exception = e;
      }
    }

    public boolean isFinished ()
    {
      return (entry != null || exception != null);
    }

    public Exception getException ()
    {
      return exception;
    }

    public TopologyEntry getLookup ()
    {
      return entry;
    }
  }

  private static Object getCachedTopologyLookup (MessageAddress agent)
  {
    synchronized (topologyLookupTable)
    {
      String key = agent.toString();
      return topologyLookupTable.get (key);
    }
  }

  private static void cacheTopologyLookup (MessageAddress agent, TopologyEntry entry)
  {
    synchronized (topologyLookupTable)
    {
      String key = agent.toString();
      if (entry != null) topologyLookupTable.put (key, entry);
      else topologyLookupTable.remove (key);
    }
  }

  private static long now ()
  {
    return System.currentTimeMillis();
  }

  private static String stackTraceToString (Exception e)
  {
    java.io.StringWriter stringWriter = new java.io.StringWriter();
    java.io.PrintWriter printWriter = new java.io.PrintWriter (stringWriter);
    e.printStackTrace (printWriter);
    return stringWriter.getBuffer().toString();
  }
}
