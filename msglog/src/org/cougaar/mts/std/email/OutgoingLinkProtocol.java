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
 * 26 Mar  2002: Update from Cougaar 8.6.2.x to 9.0.0 (OBJS)
 * 26 Sept 2001: Rename: MessageTransport to LinkProtocol. (OBJS)
 * 16 Sept 2001: Updated from Cougaar 8.3.1 to 8.4. (OBJS)
 * 09 Sept 2001: Created. (OBJS)
 */

package org.cougaar.mts.std.email;

import org.cougaar.core.mts.*;
import org.cougaar.mts.base.*;


/**
 *  Subclass of LinkProtocol, to allow for split message transports.
**/

public abstract class OutgoingLinkProtocol extends LinkProtocol
{
  public OutgoingLinkProtocol ()
  {}

  public void registerClient (MessageTransportClient client) 
  {}

  public void unregisterClient (MessageTransportClient client) 
  {}

  public final void registerMTS (MessageAddress addr)
  {}
}
