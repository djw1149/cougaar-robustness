/*
 * <copyright>
 *  Copyright 2001 Object Services and Consulting, Inc. (OBJS),
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
 * 19 Jun 2002: Removed "ignoreOldMessages" functionality - obsolete in
 *              a persistent world. (OBJS)
 * 18 Jun 2002: Restored Node name to inboxes properties due to facilitate
                CSMART test configuration. (OBJS)
 * 11 Jun 2002: Move to Cougaar threads. (OBJS)
 * 11 Apr 2002: Removed Node name from inboxes property (2 reasons -
 *              no longer able to get Node name, and, more importantly,
 *              each Node has it's own properties, the differentiation
 *              was just done for testing convienience.)  (OBJS)
 * 08 Jan 2002: Add mailServerPollTimeSecs property.  (OBJS)
 * 19 Dec 2001: Commented out FQDN warning per Steve's request. (OBJS)
 * 29 Nov 2001: Restrict data registered with nameserver to just that
 *              needed by the mail senders to us. (OBJS)
 * 29 Oct 2001: Conditionally print "<E" on successful message receipt. (OBJS)
 * 22 Oct 2001: Rewrote message in thread to handle server outages and
 *              restorations.  Went to a no header ObjectInputStream. (OBJS)
 * 26 Sep 2001: Rename: MessageTransport to LinkProtocol, add debug
 *              property. (OBJS)
 * 25 Sep 2001: Port to Cougaar 8.4.1 (OBJS)
 * 16 Sep 2001: Port to Cougaar 8.4 (OBJS)
 * 26 Aug 2001: Revamped for new 8.3.1 component model. (OBJS)
 * 14 Jul 2001: Require FQDN hostnames in props file, since FQDN not avail
 *               from java.net.InetAddress under Windows & JDK 1.3. Add feature
 *               to props file wherein "-" for mailbox username defaults
 *               to node name. (OBJS)
 * 11 July 2001: Added initial name server support. (OBJS)
 * 08 July 2001: Created. (OBJS)
 */

package org.cougaar.core.mts.email;

import java.util.*;
import java.net.InetAddress;

import org.cougaar.core.mts.*;
import org.cougaar.core.component.Service;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.component.ServiceProvider;
import org.cougaar.core.service.ThreadService;
import org.cougaar.core.thread.Schedulable;
import org.cougaar.core.thread.CougaarThread;


/**
 * IncomingEmailLinkProtocol is an IncomingLinkProtocol which receives
 * Cougaar messages from other nodes in a society via email.
 * <p>
 * <b>System Properties:</b>
 * <p>
 * <b>org.cougaar.message.protocol.classes</b>
 * Cause this link protocol to be loaded at init time by adding
 * org.cougaar.core.mts.email.IncomingEmailLinkProtocol to this System
 * property defined in your setarguments.bat file. If you don't have such a property, add one. 
 * Multiple protocols are separated by commas.
 * <br>(e.g. -Dorg.cougaar.message.protocol.classes=org.cougaar.core.mts.email.OutgoingEmailLinkProtocol,
 * org.cougaar.core.mts.email.IncomingEmailLinkProtocol)
 * <p>
 * <b>org.cougaar.message.protocol.email.inboxes</b> 
 * Specify the inbound POP3 mailboxes for a node by setting this property 
 * to a string of the form <i>pop3,host,port,user,pswd</i>, where:
 * <pre>
 * pop3:  The literal string "pop3".
 * host:  The fully qualified domain name (FQDN) of the host running the POP3 mail server.
 * port:  The port the POP3 mail server is listening on (typically port 110).
 * user:  The user name for the POP3 mailbox account.
 * pswd:  The password for the POP3 mailbox account
 * </pre>
 * (e.g. -Dorg.cougaar.message.protocol.email.inboxes=pop3,wally.objs.com,110,PerfNodeA,james)
 * <br><i>[Note:  Currently, only one inbox per node can be specified. That will change.]</i>
 * <p>
 * <b>org.cougaar.message.protocol.nntp.ignoreOldMessages</b>
 * By default, IncomingEmailLinkProtocol ignores Cougaar messages 
 * already cached at a POP3 server before the node is initialized, to avoid
 * processing messages that might have been sent in an earlier run.
 * To disable this feature, set this property to false. 
 * <br><i>[Note: this is a temporary workaround, and is likely to disappear.]</i>
 * <p>
 * <b>org.cougaar.message.protocol.email.oldMsgGracePeriod</b>
 * Messages sent this number of milliseconds before initialization
 * of this node will be accepted when <b>ignoreOldMessages</b> is true,
 * to account for the fact clocks might not be synchronized.  It should
 * be set to less than the time between tests.  It defaults to 120000ms
 * (2 minutes). 
 * <br><i>[Note: this is a temporary workaround, and is likely to disappear.]</i>
 * <p>
 * <b>org.cougaar.message.protocol.email.debug</b> 
 * If true, prints debug information to System.out.
 * <p>
 * <b>mail.debug</b> 
 * If true, JavaMail prints debug information to System.out.
 * */

public class IncomingEmailLinkProtocol extends IncomingLinkProtocol
{
  public static final String PROTOCOL_TYPE = "-email";

  private static final MailMessageCache cache = new MailMessageCache();

  private boolean debug;
  private boolean debugMail = false;
  private boolean showTraffic;

  private String nodeID;
  private String inboxesProp;
  private MailBox inboxes[];
  private MailData myMailData;
  private Vector messageInThreads;
  private ThreadService threadService;
  private boolean startedMailReadingThreads;
  private MessageAddress myAddress;
  private boolean ignoreOldMessages;
  private int gracePeriod, mailServerPollTime;
  private boolean firstTime = true;
private boolean hasStartedUp = false;  

  public IncomingEmailLinkProtocol ()
  {
    System.err.println ("Creating " + this);

    messageInThreads = new Vector();

    //  Read external properties

    String s = "org.cougaar.message.protocol.email.debug";
    debug = Boolean.valueOf(System.getProperty(s,"false")).booleanValue();
/*
    s = "org.cougaar.message.protocol.email.inboxes";
    inboxesProp = System.getProperty (s);

    if (inboxesProp == null || inboxesProp.equals(""))
    {
      throw new RuntimeException ("Bad or missing property: " +s);
    }
*/
/*
    s = "org.cougaar.message.protocol.email.ignoreOldMessages";
    ignoreOldMessages = Boolean.valueOf(System.getProperty(s,"true")).booleanValue();

    if (ignoreOldMessages)
    {
      System.err.println ("FYI: " +this+ " is ignoring old messages.");

      //  Check for a grace period 

      s = "org.cougaar.message.protocol.email.oldMsgGracePeriod";
      gracePeriod = Integer.valueOf(System.getProperty(s,"120000")).intValue();
    }
*/
    s = "org.cougaar.message.protocol.email.mailServerPollTimeSecs";
    mailServerPollTime = Integer.valueOf(System.getProperty(s,"5")).intValue();

    //  Set showTraffic based on if ShowTrafficAspect is loaded

    s = "org.cougaar.core.mts.ShowTrafficAspect";
    // showTraffic = (AspectSupportImpl.instance().findAspect(s) != null);
    // showTraffic = (getAspectSupport().findAspect(s) != null);

showTraffic = true; // HACK!!!
  }

  public DestinationLink getDestinationLink (MessageAddress destination) 
  {
    //  HACK! Attempt start the mail reading threads at the right time (not too early!)

    if (!startedMailReadingThreads) startMailReadingThreads();

    //  Incoming links don't have destination links like outgoing links do

    return dummyDestLink;  // inherited from superclass
  }

  public String toString ()
  {
    return this.getClass().getName();
  }

  public void setNameSupport (NameSupport nameSupport) 
  {
    //  HACK! Name support & registry not available in constructor above

    if (getNameSupport() == null)
    {
      throw new RuntimeException ("IncomingEmailLinkProtocol: Name server not available!");
    }

    if (getRegistry() == null)
    {
      throw new RuntimeException ("IncomingEmailLinkProtocol: Registry not available!");
    }

    nodeID = getRegistry().getIdentifier();

    String prop = "org.cougaar.message.protocol.email.inboxes." +nodeID;
    inboxesProp = System.getProperty (prop);

    if (inboxesProp == null || inboxesProp.equals(""))
    {
      throw new RuntimeException ("Bad or missing property: " +prop);
    }

if (!hasStartedUp)
{
    if (startup (inboxesProp) == false)
    {
      throw new RuntimeException ("Failure starting up IncomingEmailLinkProtocol!");
    }
}
  }

  public synchronized boolean startup (String inboxesProp)
  {
    shutdown();

    //  Only using the first inbox at this time

    inboxes = parseInboxes (inboxesProp);
    if (inboxes == null || inboxes.length < 1) return false;

    //  Start the threads that monitor the inboxes for the incoming
    //  Cougaar message emails.

    for (int i=0; i<inboxes.length; i++)
    {
      MessageInThread messageIn = null;

      try
      {
        if (MailMan.checkMailBoxAccess (inboxes[i]) == false)
        {
          System.err.println ("\nIncomingEmailLinkProtocol ALERT: Is your mail server up?");
          System.err.println ("Unable to access mailbox:\n" + inboxes[i].toStringDiscreet());

          continue;
        }

        messageIn = new MessageInThread (inboxes[i]);  // actually a Runnable
        Schedulable thread = threadService().getThread (this, messageIn, "inbox"+i);
        messageIn.setThread (thread);
//      thread.start();

        registerMailData (nodeID, inboxes[i]);
        messageInThreads.add (messageIn);
      }
      catch (Exception e)
      {
        System.err.println ("IncomingEmailLinkProtocol.startup inbox"+i+" exception: ");
        e.printStackTrace();

        if (messageIn != null) messageIn.quit();
      }
    }

    //  We can't call it a successful startup unless at least one thread 
    //  was successfully started.
// created?

hasStartedUp = true;  // kind of hack for now

    return messageInThreads.size() > 0;
  }

  public synchronized void shutdown ()
  {
    //  Kill all the inboxes

    if (inboxes != null)
    {
      for (int i=0; i<inboxes.length; i++)
      {
        // unregisterMailData ();
        inboxes[i] = null;
      }

      unregisterMailData ();
      inboxes = null;
    }

    //  Halt and get rid of all the inbox threads

    stopMailReadingThreads();

hasStartedUp = false;
  }

  private void startMailReadingThreads ()
  {
    if (!startedMailReadingThreads)
    {
      for (Enumeration e=messageInThreads.elements(); e.hasMoreElements(); ) 
      {
        MessageInThread messageIn = (MessageInThread) e.nextElement();
        messageIn.getThread().start();
      }
        
      startedMailReadingThreads = true;
    }
  }

  private void stopMailReadingThreads ()
  {
    for (Enumeration e = messageInThreads.elements(); e.hasMoreElements(); ) 
    {
      MessageInThread messageIn = (MessageInThread) e.nextElement();
      messageIn.quit();  
    }

    messageInThreads.removeAllElements();
    startedMailReadingThreads = false;
  }

  public MailBox[] parseInboxes (String inboxesProp)
  {
    if (inboxesProp == null) return null;

    Vector in = new Vector();

    int MaxBoxes = 1;  // only handling one at the moment

    //  Parse the inbox specs (separated by semicolons)

    for (int i=0; i<MaxBoxes; i++)
    {
      StringTokenizer specs = new StringTokenizer (inboxesProp, ";");

      if (specs.hasMoreTokens())
      {
        String spec = specs.nextToken();
        StringTokenizer st = new StringTokenizer (spec, ",");

        while (st.hasMoreTokens()) 
        {
          String protocol = st.nextToken();

          //  POP3 inboxes
          //
          //  Format: pop3,host,port,user,pswd;...;...

          if (protocol.equals ("pop3"))
          {
            try
            {
              String host = getFQDN (nextParm (st));
              String port = nextParm (st);
              String user = nextParm (st);
              String pswd = nextParm (st);

              in.add (new MailBox (protocol, host, port, user, pswd, "Inbox"));
            }
            catch (Exception e)
            {
              System.err.println ("Error: bad inbox spec in " +spec);
              System.err.println ("Bad inbox spec ignored");
            }
          }
          else break;
        }
      }
      else break;
    }

    //  Create the inboxes array and return it

    if (in.size() > 0)  
    {
      inboxes =  (MailBox[]) in.toArray  (new MailBox[in.size()]);
      return inboxes;
    }
    else
    {
      System.err.println ("Error: No inboxes defined in " +inboxesProp);
      return null;
    }
  }

  private String getFQDN (String host)
  {
    //  Not till Java 1.4 can we get the fully qualified domain name
    //  (FQDN) for hosts (via the new InetAddress getCanonicalHostname())
    //  when running on Windows.  So for now we will require that the 
    //  hostnames in email .props files are FQDN names.

    if (host.equals ("localhost"))
    {
      System.err.println ("ERROR: Only fully qualified domain names allowed as mailhost names: " +host);
      throw new RuntimeException ("Only fully qualified domain names allowed as mailhost names: " +host);
    }
    else if (host.indexOf ('.') == -1)
    {
//    System.err.println ("WARNING: Only fully qualified domain names allowed as mailhost names: " +host);
    }

    return host;
  }

  private String nextParm (StringTokenizer st)
  {
    //  Convert "-" strings into nulls

    String next = st.nextToken().trim();
    if (next.equals("-")) next = null;
    return next;
  }

  private void registerMailData (String nodeID, MailBox mbox)
  {
    //  Update the name server
    //  LIMITATION:  Only one MailData per node (thus only one inbox).
    //  How to manage more than one (transactions can come into play).

    MailBox myMailBox = new MailBox (mbox.getServerHost(), mbox.getUsername());  
    myMailData = new MailData (nodeID, myMailBox);

    MessageAddress nodeAddress = getNameSupport().getNodeMessageAddress();
    getNameSupport().registerAgentInNameServer (myMailData, nodeAddress, PROTOCOL_TYPE);
  }

  private void unregisterMailData ()
  {
    if (myMailData == null) return;  // no data to unregister

    //  Name support not available till after transport construction  -- still, in 9.0.0?

    if (getNameSupport() == null) return;  

    //  Update the name server

    MessageAddress nodeAddress = getNameSupport().getNodeMessageAddress();
    getNameSupport().unregisterAgentInNameServer (myMailData, nodeAddress, PROTOCOL_TYPE);
  }

  public final void registerClient (MessageTransportClient client) 
  {
    if (myMailData == null) return;  // no data to register

    try 
    {
      MessageAddress clientAddress = client.getMessageAddress();
      getNameSupport().registerAgentInNameServer (myMailData, clientAddress, PROTOCOL_TYPE);
    } 
    catch (Exception e) 
    {
      System.err.println ("IncomingEmailLinkProtocol: registerClient");
      e.printStackTrace();
    }
  }

  public final void unregisterClient (MessageTransportClient client) 
  { 
    if (myMailData == null) return;  // no data to unregister

    try 
    {
      MessageAddress clientAddress = client.getMessageAddress();
      getNameSupport().unregisterAgentInNameServer (myMailData, clientAddress, PROTOCOL_TYPE);
    } 
    catch (Exception e) 
    {
      System.err.println ("IncomingEmailLinkProtocol: unregisterClient");
      e.printStackTrace();
    }
  }

  public final void registerMTS (MessageAddress addr)
  {
    if (myMailData == null) return;  // no data to register

    try 
    {
      getNameSupport().registerAgentInNameServer (myMailData, addr, PROTOCOL_TYPE);
    } 
    catch (Exception e) 
    {
      System.err.println ("IncomingEmailLinkProtocol: registerMTS");
      e.printStackTrace();
    }
  }

  private ThreadService threadService () 
  {
	if (threadService != null) return threadService;
	ServiceBroker sb = getServiceBroker();
	threadService = (ThreadService) sb.getService (this, ThreadService.class, null);
	return threadService;
  }

  private class MessageInThread implements Runnable
  { 
    private MailBox inbox;
    private NoHeaderInputStream messageIn = null;
    private boolean quitNow;
    private Schedulable thread;

    public MessageInThread (MailBox inbox)
    {
      this.inbox = inbox;
    }

    public void quit ()
    {
      quitNow = true;

      if (messageIn != null)
      {
        try { messageIn.close(); } catch (Exception e) {}
      }
    }

    public void run() 
    {
      //  We don't want this thread to stop until we want it to stop, so
      //  we do things very carefully here.  Later on, this and other
      //  sensitive threads should probably be monitored.

      boolean access;
      int n, waitTime;
        
      while (!quitNow) 
      {
        //  Make sure inbox is still accessible

        access = false;
        n = 0;

        while (!quitNow)
        {
          try
          {
            access = MailMan.checkMailBoxAccess (inbox);
          }
          catch (Exception e)
          {
            System.err.println ("IncomingEmailLinkProtocol: Checking inbox access: " + e);
          }

          if (access == false)
          {
            //  Print out a message that the inbox is not accessible
            //  and then wait for awhile till we try again.
            
            n++;
           
            if (n < 20)       waitTime = 15;
            else if (n < 100) waitTime = 30;
            else              waitTime = 60;
            
            if (debug)
            {
              try
              {
                String s =  "\nALERT: Unable to access mail inbox:\n" +inbox.toStringDiscreet();
                s += "\nTry " +n+ ": Will try to access it again in " +waitTime+ " seconds...";
                System.err.println (s);
              } 
              catch (Exception e) {}
            }

            try { CougaarThread.sleep (waitTime*1000); } catch (Exception e) {}
          }
          else
          {
            if (n > 0 && debug) System.err.println ("\nIncomingEmail: Inbox is accessible again.");
            break;
          }
        }

        //  Ok, inbox is accessible, now create the email message instream

        EmailInputStream emailIn = null;
        n = 0;

        while (!quitNow)
        {
          emailIn = createEmailInputStream (mailServerPollTime*1000);

          if (emailIn == null)
          {
            //  This should not happen.  But in case it does, we'll try a few times
            //  to see if we can successfully create the instream.  If we fail, 
            //  we'll head back to checking the mailbox access.

            n++;
           
            if (n < 5) waitTime = 5;
            else break;

            try { CougaarThread.sleep (waitTime*1000); } catch (Exception e) {}
          }
          else break;
        }

        if (emailIn == null) continue;  // go back to check mailbox access

        //  Finally, read and process incoming messages.  If we encounter a
        //  failure, we close the stream and go back to the top of the loop
        //  and start over establishing the inbox access and input stream.

        while (!quitNow)
        {
          if (readAndDeliverMessage (emailIn) == false) break;
        }

        //  It's important to close the message stream due to its
        //  underlying polling email stream.
      
        if (messageIn != null)
        {
          try { messageIn.close(); } catch (Exception e2) {}
          messageIn = null;
        }
      }    
    }

    public void setThread (Schedulable thread)
    {
      this.thread = thread;
    }

    public Schedulable getThread ()
    {
      return thread;
    }

    private EmailInputStream createEmailInputStream (int pollTime) 
    {
      EmailInputStream emailIn = null;

      try
      {
        emailIn = new EmailInputStream (inbox, pollTime, cache);

        emailIn.setInfoDebug (debug);      // informative progress debug
        emailIn.setDebug (false);          // low-level debug
        emailIn.setDebugMail (debugMail);  // low-level mail debug

        emailIn.setFromFilter ("EmailStream#");
        emailIn.setSubjectFilter ("To: " + nodeID);

        emailIn.setPollTime (pollTime);
/*
        //  We get the date that the OutgoingEmailLinkProtocol was created
        //  so we can detect messages that are responses for earlier incarnations
        //  of this Node, and (for now) discard them.  Note we are making the 
        //  (non-real-world) assumption that the clocks of all the Nodes are 
        //  synchronized.  This is a temporary scheme to assist in testing and 
        //  assessment, but is not meant for beyond that.  The grace period
        //  allows some room for de-synchronization among the machines.  It needs
        //  to be less than the time between tests.

        if (ignoreOldMessages)
        {        
          Date bday = OutgoingEmailLinkProtocol.transportBirthday;
          emailIn.ignoreOlderMessages (new Date (bday.getTime()-gracePeriod));
        }
*/
        return emailIn;
      }
      catch (Exception e) 
      { 
        System.err.println ("\nIncomingEmailLinkProtocol: Error creating email input stream: " +e);
        if (emailIn != null) try { emailIn.close(); } catch (Exception e2) {}
        return null;
      }
    }

    private final synchronized boolean readAndDeliverMessage (EmailInputStream emailIn)
    {
      //  This method is synchronized to insure messages are delivered in
      //  the same order they are read.

      AttributedMessage msg = null;

      try 
      {
        //  There's a bit of a tricky bit with this ObjectInputStream (creates its
        //  own thread perhaps).  We do this here so that later errors on the stream
        //  come out of the catch clause here.  NOTE:  This problem has been fixed
        //  as a side effect of removing the stream headers from the object streams.
        //  Before, with the regular ObjectInputStream, its construction caused 
        //  a separate thread to be created to read the stream header.

        if (messageIn == null)
        {  
           messageIn = new NoHeaderInputStream (emailIn);

           if (!firstTime && debug) System.err.println ("\nIncomingEmail: Email instream restored.");
           firstTime = false;
        }

        //  Sit and wait for a message to come in (the underlying
        //  email stream is polling its mail box).
      
        msg = (AttributedMessage) messageIn.readObject();

        if (showTraffic) System.err.print ("<E");

        if (debug) 
        {
          System.err.println ("\nIncomingEmail: read " +MessageUtils.toString(msg));
        }

        //  Deliver the message
      
        if (msg != null) getDeliverer().deliverMessage (msg, msg.getTarget());

        //  Return success

        return true;
      } 
      catch (Exception e) 
      {
        if (debug)
        {
          if (msg == null) System.err.println ("\nIncomingEmail: Email instream lost...");
          else System.err.println ("\nIncomingEmail: Problem delivering msg " +MessageUtils.toString(msg));
e.printStackTrace();
        }

        // if (debug) e.printStackTrace();
      
        //  It's important to close the message stream due to its
        //  underlying polling email stream.
      
        if (messageIn != null)
        {
          try { messageIn.close(); } catch (Exception e2) {}
          messageIn = null;
        }

        //  Return failure

        return false;
      }
    }
  }
}
