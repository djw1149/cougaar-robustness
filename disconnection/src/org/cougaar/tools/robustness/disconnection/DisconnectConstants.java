/*
 * DefenseConstants.java
 *
 * Created on August 10, 2003, 10:20 AM
 *
 
 * @author David Wells - OBJS 
 *
 * <copyright>
 *  Copyright 2003 Object Services and Consulting, Inc.
 *  under sponsorship of the Defense Advanced Research Projects Agency (DARPA)
 *  and the Defense Logistics Agency (DLA).
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
 *
 * </copyright> 
 */ 

package org.cougaar.tools.robustness.disconnection;

public class DisconnectConstants {

    /** Creates new DefenseConstants */
    public DisconnectConstants() {}
    
    public static final String DEFENSE_NAME = "Disconnect";

    // Possible Diagnosis Values
    public final static String DISCONNECT_REQUEST = "Disconnect_Request";
    public final static String CONNECT_REQUEST = "Connect_Request";
    public final static String TARDY = "Tardy";
    public final static String DISCONNECTED = "Disconnected";
    public final static String CONNECTED = "Connected";

    // The Possible Actions
    public final static String ALLOW_DISCONNECT = "Allow_Disconnect";
    public final static String ALLOW_CONNECT = "Allow_Connect";
    public final static String AUTONOMOUS_RESTART = "Autonomous_Restart";

}
