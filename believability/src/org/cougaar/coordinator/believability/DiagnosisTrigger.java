/**
 *<SOURCE_HEADER>
 *
 *<NAME>
 * $RCSfile: DiagnosisTrigger.java,v $
 *</NAME>
 *
 * <copyright>
 *  Copyright 2004 Telcordia Technologies, Inc.
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
 * </copyright>
 */

package org.cougaar.coordinator.believability;

import org.cougaar.coordinator.techspec.AssetID;

/**
 * Abstract superclass of all trigger objects that are diagnsis
 * related.
 * 
 * @author Tony Cassandra
 */
abstract class DiagnosisTrigger extends BeliefUpdateTrigger
{

    //************************************************************
    /**
     * Constructor, from a diagnosis on the blackboard
     * @param diag The diagnosis from the blackboard
     **/
    DiagnosisTrigger( AssetID id ) 
    {
        super( id );
    } // constructor
    
    //************************************************************
    /**
     * Return the value of the diagnosis, as published by the sensor
     * @return the diagnosis value
     **/
    String getSensorValue() { return _sensor_value; }

    //************************************************************
    /**
     * This routine should return the asset statew dimension name that
     * this trigger pertains to.
     */
    String getStateDimensionName() { return _sensor_state_dimension; }

    //************************************************************
    /**
     * Return the name of the sensor that made this diagnosis
     * @return 
     **/
    String getSensorName() { return _sensor_name; }

    //************************************************************
    /**
     * Return the believability diagnosis as a string
     **/
    public String toString() 
    {
        StringBuffer buff = new StringBuffer();

        buff.append( this.getClass().getName() );
        buff.append( " - asset " + getAssetID().toString() );
        buff.append( " with sensor " + _sensor_name );
        buff.append( " asserted diagnosis " + _sensor_value );
        buff.append( " at time " + getTriggerTimestamp() );
        
        return buff.toString();

    } // method toString

    //---------------------------------------------------------------
    // protected interface
    //---------------------------------------------------------------

    // The value of the diagnosis, in the terms that the sensor uses
    protected String _sensor_value;

    // The name of the diagnosis
    protected String _sensor_name;

    // The state dimension that the diagnosis concerns
    protected String _sensor_state_dimension;

} // class DiagnosisTrigger

