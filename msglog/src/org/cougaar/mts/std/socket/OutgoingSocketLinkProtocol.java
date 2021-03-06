/*
 * <copyright>
 *  Copyright 2001-2004 Object Services and Consulting, Inc. (OBJS),
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
 *  1 Mar 2004: Port to 11.0
 * 07 May 2003: Updated addMessageAttributes for Security compatibility. (102B)
 * 12 Feb 2003: Port to 10.0 (OBJS)
 * 27 Sep 2002: Add inband acking. (OBJS)
 * 22 Sep 2002: Revamp for new serialization & socket closer. (OBJS)
 * 18 Aug 2002: Various enhancements for Cougaar 9.4.1 release. (OBJS)
 * 16 May 2002: Port to Cougaar 9.2.x (OBJS)
 * 08 Apr 2002: Port to Cougaar 9.1.x (OBJS)
 * 21 Mar 2002: Port Cougaar 9.0.0 (OBJS)
 * 02 Jan 2002: Added connectTimeoutSecs property. (OBJS)
 * 11 Dec 2001: Save backup of cached socket data from name server. (OBJS) 
 * 02 Dec 2001: Implement getRemoteReference (8.6.2.0) (OBJS)
 * 20 Nov 2001: Cougaar 8.6.1 compatibility changes. (OBJS)
 * 09 Nov 2001: Alter sendMessage to throw exception in case of second
 *              failure, for debugging purposes. (OBJS)
 * 25 Oct 2001: Fix stream corruption bug by adopting no header object IO
 *              streams and reseting stream for each object.  Bug only 
 *              manifests itself if output stream not used shortly after 
 *              transport initialization.  Do not know yet know why this
 *              is happening, it is not use of SO timeout.  Testing shows
 *              reset is needed for each object send, just at stream 
 *              creation doesn't work.  This modification to the object
 *              IO streams was needed anyways at some point to deal with
 *              the possible case of object evolution happening sooner
 *              than stream reconstruction (for long lived connections),
 *              as object references would then be invalidated. (OBJS)
 * 23 Oct 2001: Remove extraneous synchonized statement on sendMessage 
 *              method. (OBJS)
 * 21 Sep 2001: Make call to sendMessage class synchronized. (OBJS)
 * 26 Sep 2001: Rename: MessageTransport to LinkProtocol, add debug and
 *              cost properties. (OBJS)
 * 25 Sep 2001: Port to Cougaar 8.4.1 (OBJS)
 * 14 Sep 2001: Port to Cougaar 8.4 (OBJS)
 * 22 Aug 2001: Revamped for new 8.3.1 component model. (OBJS)
 * 11 Jul 2001: Added initial name server support. (OBJS)
 * 08 Jul 2001: Created. (OBJS)
 */

package org.cougaar.mts.std.socket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Vector;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.mts.MessageAttributes;
import org.cougaar.core.mts.SimpleMessageAttributes;
import org.cougaar.core.service.LoggingService;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;
import org.cougaar.mts.base.AttributedMessage;
import org.cougaar.mts.base.CommFailureException;
import org.cougaar.mts.base.DestinationLink;
import org.cougaar.mts.base.MisdeliveredMessageException;
import org.cougaar.mts.base.NameLookupException;
import org.cougaar.mts.base.UnregisteredNameException;
import org.cougaar.mts.std.MessageDeserializationException;
import org.cougaar.mts.std.MessageSerializationUtils;
import org.cougaar.mts.std.MessageUtils;
import org.cougaar.mts.std.RTTService;
import org.cougaar.mts.std.SocketClosingService;
import org.cougaar.mts.std.acking.AckList;
import org.cougaar.mts.std.acking.MessageAckingAspect;
import org.cougaar.mts.std.acking.NumberList;
import org.cougaar.mts.std.acking.PureAck;
import org.cougaar.mts.std.acking.PureAckMessage;
import org.cougaar.mts.std.acking.PureAckAckMessage;


/**
 * OutgoingSocketLinkProtocol is an OutgoingLinkProtocol which uses Internet
 * sockets (TCP) to send Cougaar messages from the current node to other nodes in the society.
 * <p>
 * <b>System Properties:</b>
 * <p>
 * <b>org.cougaar.message.protocol.classes</b>
 * Cause this link protocol to be loaded at init time by adding
 * org.cougaar.core.mts.socket.OutgoingSocketLinkProtocol to this System
 * property defined in your setarguments.bat file. If you don't have such a property, add one. 
 * Multiple protocols are separated by commas.
 * <br>(e.g. -Dorg.cougaar.message.protocol.classes=org.cougaar.core.mts.socket.OutgoingSocketLinkProtocol,
 * org.cougaar.core.mts.socket.IncomingSocketLinkProtocol)
 * <p>
 * <b>org.cougaar.message.protocol.socket.cost</b>
 * The cost function of the DestinationLink inner subclass defaults to 500, so 
 * that, using the default MinCostLinkSelectionPolicy, it will be chosen first, before 
 * RMILinkProtocol, OutgoingEmailLinkProtocol, and NNTPLinkProtocol, which are
 * less efficient.  When using AdaptiveLinkSelectionPolicy, cost is
 * one of the factors that are used to select a protocol. To modify the default
 * cost, set this property to an integer. 
 * <br>(e.g. -Dorg.cougaar.message.protocol.socket.cost=1250)
 * <p>
 * <b>org.cougaar.message.protocol.socket.debug</b> 
 * If true, prints debug information to System.out.
 * */

public class OutgoingSocketLinkProtocol extends OutgoingLinkProtocol
{
  public static final String PROTOCOL_TYPE = "-socket";

  private static final int linkCost;
  private static final boolean doInbandAcking;
  private static final boolean useMessageDigest;
  private static final String messageDigestType;
  private static final int socketTimeout;
  private static final int inbandAckSoTimeout;
  private static final boolean oneSendPerConnection;

  private static int SID;

  private LoggingService log;
  private SocketClosingService socketCloser;
  private RTTService rttService;
  private Hashtable specCache, addressCache;
  private HashMap links;

  static
  {
    //  Read external properties

    String s = "org.cougaar.message.protocol.socket.cost";  // one way
    linkCost = Integer.valueOf(System.getProperty(s,"5000")).intValue();  // was 1000

    s = "org.cougaar.message.protocol.socket.doInbandAcking";
    doInbandAcking = Boolean.valueOf(System.getProperty(s,"true")).booleanValue();

    s = "org.cougaar.message.protocol.socket.useMessageDigest";
    useMessageDigest = Boolean.valueOf(System.getProperty(s,"false")).booleanValue();

    s = "org.cougaar.message.protocol.socket.messageDigestType";
    messageDigestType = System.getProperty (s, "MD5");

    s = "org.cougaar.message.protocol.socket.outgoing.socketTimeout";
    socketTimeout = Integer.valueOf(System.getProperty(s,"5000")).intValue();

    s = "org.cougaar.message.protocol.socket.outgoing.inbandAckSoTimeout";
    inbandAckSoTimeout = Integer.valueOf(System.getProperty(s,"2000")).intValue();

    s = "org.cougaar.message.protocol.socket.outgoing.oneSendPerConnection";
    oneSendPerConnection = Boolean.valueOf(System.getProperty(s,"true")).booleanValue();
  }
 
  public OutgoingSocketLinkProtocol ()
  {
    specCache = new Hashtable();
    addressCache = new Hashtable();
    links = new HashMap();
  }

  public void load ()
  {
    super_load();

    log = loggingService;
    if (doInfo()) log.info ("Creating " + this);

    if (socketTimeout > 0)
    {
      ServiceBroker sb = getServiceBroker();
      socketCloser = (SocketClosingService) sb.getService (this, SocketClosingService.class, null);
      if (socketCloser == null) log.error ("Cannot do socket timeouts - SocketClosingService not available!");
    }

    rttService = (RTTService) getServiceBroker().getService (this, RTTService.class, null);

    if (startup() == false)
    {
      String str = "Failure starting up " + this;
      log.error (str);
      throw new RuntimeException (str);
    }
  }

  public String toString ()
  {
    return this.getClass().getName();
  }

  public synchronized boolean startup ()
  {
    shutdown();
    return true;
  }

  public synchronized void shutdown ()
  {}

  private SocketSpec lookupSocketSpec (MessageAddress address) throws NameLookupException
  {
    URI uri = getNameSupport().lookupAddressInNameServer(address, PROTOCOL_TYPE);  //100
    
    if (uri != null) //100
    {
      SocketSpec spec = new SocketSpec (uri); //100

      try {
        spec.setInetAddress (getInetAddress (spec.getHost()));
      } catch (Exception e) {
        throw new NameLookupException (e);
      }
      return spec;
    }
    return null;
  }

  private SocketSpec getSocketSpecByNode (String node) throws NameLookupException
  {
    return getSocketSpec (MessageAddress.getMessageAddress (node+ "(MTS" +PROTOCOL_TYPE+ ")")); //100
  }

  private SocketSpec getSocketSpec (MessageAddress address) throws NameLookupException
  {
    synchronized (specCache)
    {
      SocketSpec spec = (SocketSpec) specCache.get (address);
      if (spec != null) return spec;
      spec = lookupSocketSpec (address);
      if (spec != null) specCache.put (address, spec);
      return spec;
    }
  }

  private InetAddress getInetAddress (String host) throws UnknownHostException
  {
    synchronized (addressCache)
    {
      InetAddress addr = (InetAddress) addressCache.get (host);
      if (addr != null) return addr;
      addr = InetAddress.getByName (host);
      if (addr != null) addressCache.put (host, addr);
      return addr;
    }
  }

  private synchronized void clearCaches ()
  {
    specCache.clear();
    addressCache.clear();    
  }

  public boolean addressKnown (MessageAddress address) 
  {
    try 
    {
      return (getSocketSpec (address) != null);
    } 
    catch (Exception e) 
    {
      return false;
    }
  }

  public DestinationLink getDestinationLink (MessageAddress address) 
  {
    DestinationLink link = (DestinationLink) links.get (address);

    if (link == null) 
    {
      link = new SocketOutLink (address);
      link = (DestinationLink) attachAspects (link, DestinationLink.class);
      links.put (address, link);
    }

    return link;
  }

  public static boolean doInbandAcking ()
  {
    return doInbandAcking;
  }

  public static int getSocketTimeout ()
  {
    return socketTimeout;
  }

  private synchronized static int getNextSendID ()  // for debugging purposes
  {
    return SID++;
  }

  class SocketOutLink implements DestinationLink 
  {
    private MessageAddress destination;
    private Socket socket;
    private OutputStream socketOut;
    private InputStream socketIn;
    private String sid;

    //102B
    public void addMessageAttributes(MessageAttributes attrs) {
      attrs.addValue(MessageAttributes.IS_STREAMING_ATTRIBUTE,
                     Boolean.FALSE);
      attrs.addValue(MessageAttributes.ENCRYPTED_SOCKET_ATTRIBUTE,
                     Boolean.FALSE);
    }

    public SocketOutLink (MessageAddress dest) 
    {
      destination = dest;
    }

    public MessageAddress getDestination() 
    {
      return destination;
    }

    public String toString ()
    {
      return OutgoingSocketLinkProtocol.this + "-dest:" + destination;
    }

    public Class getProtocolClass () 
    {
      return OutgoingSocketLinkProtocol.class;
    }
   
    public int cost (AttributedMessage msg) 
    {
      if (msg == null) return linkCost;  // forced HACK
      return (addressKnown(destination) ? linkCost : Integer.MAX_VALUE);
    }

    public boolean isValid (AttributedMessage message) 
    {
      return true;
    }

    public Object getRemoteReference ()
    {
      return null;
    }

    public boolean retryFailedMessage (AttributedMessage msg, int retryCount)
    {
      return true;
    }
   
    private synchronized void dumpCachedData ()
    {
      clearCaches();
      closeSocket();
    }

    public synchronized MessageAttributes forwardMessage (AttributedMessage msg) 
        throws NameLookupException, UnregisteredNameException,
               CommFailureException, MisdeliveredMessageException
    {
      //  Dump our cached data on message send retries

      if (MessageUtils.getSendTry (msg) > 1) dumpCachedData();

      //  Get the socket spec for the destination node.  Note we use node instead
      //  of agent as the receiver can move his socket to another port at will, making
      //  any and all client (ie. agent) socket spec registrations out of date.

      String toNode = MessageUtils.getToAgentNode (msg);
      SocketSpec destSpec = getSocketSpecByNode (toNode);

      if (destSpec == null)
      {
        String s = "No nameserver info for " +destination;
        if (doWarn()) log.warn (s);
        throw new NameLookupException (new Exception (s));
      }

      //  Send message via socket

      boolean success = false;
      Exception ex = null;

      try 
      {
        success = sendMessage (msg, destSpec);
      } 
      catch (Exception e) 
      {
        if (doDebug()) log.debug ("sendMessage exception: " +stackTraceToString(e));
        ex = e;
      }

      //  Dump our cached data on failed sends and throw an exception

      if (success == false)
      {
        dumpCachedData();
        Exception e = (ex != null ? ex : new Exception ("socket sendMessage unsuccessful"));
        throw new CommFailureException (e);
      }

      //  Successful send

	  MessageAttributes successfulSend = new SimpleMessageAttributes();
      String status = MessageAttributes.DELIVERY_STATUS_DELIVERED;
	  successfulSend.setAttribute (MessageAttributes.DELIVERY_ATTRIBUTE, status);
      return successfulSend;
    }

    private synchronized boolean sendMessage (AttributedMessage msg, SocketSpec spec) throws Exception
    {
      if (doDebug()) 
      {
        sid = "s" +getNextSendID()+ " ";
        log.debug (sid+ "Sending " +MessageUtils.toString(msg));
      }

      //  Set message send time for RTT service (updates msg attribute)

      if (rttService != null) rttService.setMessageSendTime (msg, now());  // closer than aspect to actual send

      //  Serialize the message into a byte array.  Depending on properties set, we possibly help
      //  insure message integrity via a message digest (eg an embedded MD5 hash of the message).

      byte msgBytes[] = MessageSerializationUtils.writeMessageToByteArray (msg, getDigest());
      if (msgBytes == null) return false;

      //  It appears that the only way we can determine whether our socket
      //  connection is still valid is to try using it.  So we try it,
      //  and if it fails, we create a new connected socket and try again.

      String sockString = null;
      PureAckMessage pam = null;
      long sendTime = 0, receiveTime = 0;
      
      for (int tryN=1; tryN<=2; tryN++)  // try at most twice
      {
        try
        {
          //  Create socket if needed

          if (socket == null) 
          {
            tryN = 2;  // only try once with a new socket

            if (doDebug()) log.debug (sid+ "Creating socket to " +destination+ " with " +spec);
            socket = getSocket (spec);
            if (doDebug()) log.debug (sid+ "Created socket " + (sockString = socket.toString()));

            socketOut = new BufferedOutputStream (socket.getOutputStream());
            if (doInbandAcking) socketIn = new BufferedInputStream (socket.getInputStream());
          }

          //  Send the message

          if (doDebug()) log.debug (sid+ "Sending " +msgBytes.length+ " byte msg thru " +sockString);
          sendTime = now();
          MessageSerializationUtils.writeByteArray (socketOut, msgBytes);
          if (doDebug()) log.debug (sid+ "Sending " +msgBytes.length+ " byte msg done " +sockString);
          break;  // success
        }
        catch (Exception e)
        {
          //  If this is the second send try, go ahead and throw the exception
          //  so that the caller can see it.

          closeSocket();
          if (tryN == 1) continue;
          else throw (e);
        }
      }

      //  Optionally do inband acking

      if (doInbandAcking && MessageUtils.isAckableMessage (msg))
      {
        try
        {
          //  See if we get an ack

          if (doDebug()) log.debug (sid+ "Waiting for ack from " +sockString);
          byte[] ackBytes = MessageSerializationUtils.readByteArray (socketIn);
          receiveTime = now();
          if (doDebug()) log.debug (sid+ "Waiting for ack done " +sockString);
          pam = processAck (sid, ackBytes);

          //  Send an ack-ack
          //
          //  The inband acking protocol is that if the ack back reports a message
          //  reception exception then no ack-ack is sent for the ack.

          if (!pam.hasReceptionException())
          {
            byte[] ackAckBytes = createAckAck (pam, receiveTime);
            if (doDebug()) log.debug (sid+ "Sending ack-ack thru " +sockString);
            MessageSerializationUtils.writeByteArray (socketOut, ackAckBytes);
            if (doDebug()) log.debug (sid+ "Sending ack-ack done " +sockString);
          }
          else
          {
            Exception e = pam.getReceptionException();

            if (doWarn()) 
            {
              String node = pam.getReceptionNode();
              log.warn (sid+ "Got reception exception from node " +node+ " " +sockString+ ": " +e);
            }

            throw e;
          }
        }
        catch (Exception e)
        {
          //  Any acking that did not complete will be taken care of in later acking

          if (doDebug()) log.debug (sid+ "Inband acking stopped for " +sockString+ ": " +e);

          //  Selectively close the socket 

          if (oneSendPerConnection || pam == null || !pam.hasReceptionException()) closeSocket();
          else unscheduleSocketClose (socket);

          //  Selectively throw exceptions back up

          if (e instanceof MessageDeserializationException) throw e;
        } 
      }

      //  Optionally close the socket

      try
      {
        if (oneSendPerConnection) closeSocket(); // useful if retiring threads fast on receive side
        else unscheduleSocketClose (socket); 
      }
      catch (Exception e)
      {
        closeSocket();
      }

      //  Update the inband RTT if possible

      if (rttService != null && pam != null)
      {
        DestinationLink sendLink = this;
        String node = MessageUtils.getToAgentNode (msg);
        int rtt = ((int)(receiveTime - sendTime)) - pam.getInbandNodeTime();
        rttService.updateInbandRTT (sendLink, node, rtt);
      }

      return true;  // msg send successful
    }

    private Socket getSocket (SocketSpec spec) throws Exception
    {
      //  We set timeouts on the socket and its connection in an attempt to
      //  insure we don't get stuffed up here.

      Socket socket = new Socket();
      SocketAddress sockAddr = new InetSocketAddress (spec.getInetAddress(), spec.getPortAsInt());
      scheduleSocketClose (socket, socketTimeout);
      socket.connect (sockAddr, socketTimeout);  // timeout as unconnected socket close raises no exceptions
      if (!doInbandAcking) socket.shutdownInput();  // not doing any input
      return socket;
    }

    private void scheduleSocketClose (Socket socket, int timeout)
    {
      if (socketCloser != null) socketCloser.scheduleClose (socket, timeout);
    }

    private void unscheduleSocketClose (Socket socket)
    {
      if (socketCloser != null) socketCloser.unscheduleClose (socket);
    }

    private void closeSocket ()
    {
      if (socket != null)
      {
        try { socket.close(); } catch (Exception e) {
          if (log.isDebugEnabled()) log.debug(null,e);}

        socket = null;
        socketOut = null;
        socketIn = null;
      } 
    }

    private PureAckMessage processAck (String sid, byte[] ackBytes) throws Exception
    {
      AttributedMessage msg = MessageSerializationUtils.readMessageFromByteArray (ackBytes);
      PureAckMessage pam = (PureAckMessage) msg;

      if (pam.hasReceptionException()) return pam;  // no more ack to process

      PureAck pureAck = (PureAck) MessageUtils.getAck (pam);
      Vector latestAcks = pureAck.getLatestAcks();

      if (latestAcks != null)
      {
        if (doDebug())
        {
          StringBuffer buf = new StringBuffer();
          AckList.printAcks (buf, "  latest", latestAcks);
          log.debug (sid+ "Got inband ack:\n" +buf);
        }

        for (Enumeration a=latestAcks.elements(); a.hasMoreElements(); )
          NumberList.checkListValidity ((AckList) a.nextElement());

        String fromNode = MessageUtils.getFromAgentNode (pam);
        MessageAckingAspect.addReceivedAcks (fromNode, latestAcks);
      }
      else if (doDebug()) log.debug (sid+ "Got empty inband ack!");

      return pam;
    }

    private byte[] createAckAck (PureAckMessage pam, long receiveTime) throws Exception
    {
      PureAckAckMessage paam = PureAckAckMessage.createInbandPureAckAckMessage (pam);
      paam.setInbandNodeTime ((int)(now()-receiveTime));
      byte ackAckBytes[] = MessageSerializationUtils.writeMessageToByteArray (paam, getDigest());
      return ackAckBytes;
    }
  }

  private static MessageDigest getDigest () throws java.security.NoSuchAlgorithmException
  {
    return (useMessageDigest ? MessageDigest.getInstance(messageDigestType) : null);
  }

  private boolean doDebug ()
  {
    return (log !=null && log.isDebugEnabled());
  }

  private boolean doInfo ()
  {
    return (log !=null && log.isInfoEnabled());
  }

  private boolean doWarn ()
  {
    return (log !=null && log.isWarnEnabled());
  }

  private static long now ()
  {
    return System.currentTimeMillis();
  }

  private static String stackTraceToString (Exception e)
  {
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter (stringWriter);
    e.printStackTrace (printWriter);
    return stringWriter.getBuffer().toString();
  }
}
