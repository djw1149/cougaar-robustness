/*
 * Diagnosis.java
 *
 * Created on January 23, 2004, 12:11 PM
 */
/*
 * Diagnosis.java
 *
 * Created on March 19, 2003, 4:07 PM
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
 * </copyright>
 *
 * Modified on 08/01/2003 - to support relay API
 *
 */

package org.cougaar.coordinator;

import org.cougaar.coordinator.techspec.*;

import org.cougaar.core.service.AgentIdentificationService;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.mts.MessageAddress;
import org.cougaar.core.persist.NotPersistable;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.Vector;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.cougaar.core.util.UID;
import org.cougaar.core.util.UniqueObject;
import org.cougaar.util.log.Logger;
import org.cougaar.util.log.Logging;
import org.cougaar.core.relay.Relay;



/**
 * This class is the superclass for all Diagnosis objects. 
 *
 */
public abstract class Diagnosis 
       implements NotPersistable, Serializable, UniqueObject, Relay.Source, Relay.Target

{

   
    /** TRUE if the class attributes have been initialized */
    static private boolean inited = false;
    
    /** The asset type of this object */
    static private AssetType assetType = null;

    /** The possible values that getValue() can return */
    static private Set possibleValues;
    
    /** The possible values that getValue() can return -- cloned for dissemination to others */
    //static protected Set possibleValuesCloned;

    /** The service broker passed in  */
    static private ServiceBroker serviceBroker = null;
        
    /** The DiagnosisTechSpec for this action class */
    static private DiagnosisTechSpecInterface diagnosisTechSpec;
    
    /** 
     *  The address of the node agent. May change if the diagnosis is moved. 
     *  Making this transient will cause the target to go to null when this object moves.
     *  So, we will need to check for null each time & lookup the local node if necessary.
     */
    static transient private MessageAddress nodeId;
    
    /** The address of this agent */
    static private MessageAddress agentId = null;
    

    /** UID for this relayable object */
    private UID uid = null;
    
    /** Time when diagnosis' value last differed from its previous value  */
    private long lastChangedTimestamp = 0L;
    
    /** Time when diagnosis was most recently asserted via setValue() */
    private long lastAssertedTimestamp = 0L;
    
    /** a single string identifier of this object, e.g. might be assetType:assetName */
    private String expandedName = null;
    
    /** Name of the asset that this object describes */
    private  String assetName = null;

    /** The current diagnosis value */
    private Object value = null;
    
    /**
     * Creates a Diagnosis instance for Diagnoses to be performed on the specified asset.  
     */
    public Diagnosis(String assetName, ServiceBroker serviceBroker) throws TechSpecNotFoundException 
    {
        this.serviceBroker = serviceBroker;        
        this.assetName = assetName;
        this.possibleValues = new LinkedHashSet();    
        
        
        if (!inited) { //only called once!
            init(); //get the possibleValues from the techSpec.
        }

        this.expandedName = AssetName.generateExpandedAssetName(assetName, assetType);
    }

   

    /**
     * Creates a Diagnosis instance for Diagnoses to be performed on the specified asset. Includes initialValue 
     */
    public Diagnosis(String assetName, Object initialValue, ServiceBroker serviceBroker) 
    throws IllegalValueException, TechSpecNotFoundException 
    {
        this(assetName, serviceBroker);
        this.setValue(initialValue);
        
    }        

    /**
      * This method is intended to be called ONLY once when the first instance is created,
      * or more precisely, it is called as many times as required until the necessary attributes are
      * assigned values.
      */
    private synchronized boolean init() throws TechSpecNotFoundException {
     
        //set up the relay mechanism to the node level coordinator
        initSourceAndTarget(); 
        
        // get the diagnosis tech spec & load the possibleValues
        initPossibleValues();
     
        this.inited = true;
        
        return inited;
    }
    
    
    /**
     * Load the needed services, populate agentId and nodeId attrs, and
     * set up the relay mechanism
     */
    private void initSourceAndTarget() throws TechSpecNotFoundException {

        //** This is intended to be called once when the first instance is created.
        //** However, it can be called again if values are lost for some reason, e.g.
        //   the object is moved to another node & the target needs to be reset.
        
        if (serviceBroker == null) {
            throw new TechSpecNotFoundException("ServiceBroker is null.");            
        }
        
        if (agentId != null && nodeId != null)  { return; } //nothing to do.
        
        // **********************************************get the agentId
        AgentIdentificationService agentIdService =
                (AgentIdentificationService) serviceBroker.getService(this, AgentIdentificationService.class, null);
        if (agentIdService == null) {
            throw new RuntimeException(
            "Unable to obtain agent-id service");
        }
        this.agentId = agentIdService.getMessageAddress();

        serviceBroker.releaseService(this, AgentIdentificationService.class, agentIdService);
        if (agentId == null) {
            throw new RuntimeException(
            "Unable to obtain agent id");
        }

        
        // **********************************************get the nodeId
        NodeIdentificationService nodeIdService = (NodeIdentificationService)
            serviceBroker.getService(this, NodeIdentificationService.class, null);
        if (nodeIdService == null) {
            throw new RuntimeException("Unable to obtain node-id service");
        }
        nodeId = nodeIdService.getMessageAddress();
        serviceBroker.releaseService(this, NodeIdentificationService.class, agentIdService);
        if (nodeId == null) {
            throw new RuntimeException(
            "Unable to obtain agent id");
        }

    }

    /**
     * Retrieves the DiagnosisTechSpec for this class from the DiagnosisTechSpecService.
     * Then it populates the possibleValues & assetType
     */
    private void initPossibleValues() throws TechSpecNotFoundException {
            
        if (serviceBroker == null) {
            throw new TechSpecNotFoundException("ServiceBroker is null.");            
        }
        
        //Don't do this unless needed.
        if (diagnosisTechSpec != null && this.possibleValues != null) { return; }
        
        
        if (!serviceBroker.hasService( DiagnosisTechSpecService.class )) {
            throw new TechSpecNotFoundException("TechSpec Service not available.");            
        }
        
        // **********************************************get the tect spec service
        DiagnosisTechSpecService DiagnosisTechSpecService =
                (DiagnosisTechSpecService) serviceBroker.getService(this, DiagnosisTechSpecService.class, null);
        if (DiagnosisTechSpecService == null) {
            throw new TechSpecNotFoundException(
            "Unable to obtain tech spec service");
        }
      
        //call tech spec service & get action tech spec        
        diagnosisTechSpec = (DiagnosisTechSpecInterface) DiagnosisTechSpecService.getDiagnosisTechSpec(this.getClass());
        if (diagnosisTechSpec == null) {
            throw new TechSpecNotFoundException("Cannot find Diagnosis Tech Spec for "+ this.getClass().getName() );
        }        
        this.setPossibleValues(diagnosisTechSpec.getPossibleValues());
        this.assetType = diagnosisTechSpec.getAssetType();
        
        serviceBroker.releaseService(this, DiagnosisTechSpecService.class, DiagnosisTechSpecService);
        
    }
    
    
    
    //*************************************************** PossibleValues
    
    /**
     * @return the possible values that this Diagnosis can return from the getValue() method.
     */
    public Set getPossibleValues ( ) { return possibleValues; }

    
    /**
     *     Not for public viewing / access. For use only at init time. Sets the values retrieved by tech spec.
     */
    private void setPossibleValues ( Set values) 
    {
    
        Iterator i = values.iterator();
        Object o;
        //boolean canClone = true;
        while (i.hasNext()) {
            o = getValueFromXML((String) i.next());
            possibleValues.add(o);
            
            //** Cannot call clone() on Object type -- we'd need to know the actual type, so
            // the implementing subclass would need to do this...
            //Now store clone of object in a duplicate set, so others can see but not modify the original
            //if (canClone) { //if() used to avoid throwing more than one exception if object cannot be cloned.
            //    try {
            //        possibleValuesCloned.add(o.clone());
            //    } catch (CloneNotSupportedException cns) { //implementing cloneable does not guarantee the object supports clone()!
            //        canClone = false;
            //        possibleValuesCloned.add(o);
            //    }
            //} else {
            //    possibleValuesCloned.add(o);
            //}
        }
        
    }


    /**
     *  Return an object that represents the value listed in the Diagnostic tech spec's XML as a PossibleValue.
     *  This allows the implementor to return any representation they choose, whether a simple String, Integer,
     *  or some key-based lookup of a statically stored object.
     *
     *  The default behavior is to simply return the value as a String.
     *
     *  @return an object that represents the value 
     */
    public static Object getValueFromXML(String value) { return value; }

    

    //*************************************************** Current Diagnosis Value

    /**
     * Set the value of this diagnosis. Unless otherwise noted this should 
     * only be called by the diagnosis. The value must be in the set of permittedValues.
     * <p>
     */
    public void setValue(Object newValue) throws IllegalArgumentException {
        
        if (value != newValue) {
            this.setLastChangedTimestamp( System.currentTimeMillis() );            
        }
        value = newValue;
        this.setLastAssertedTimestamp( System.currentTimeMillis() );
    }
    
    /**
     * Get the value (one of the PossibleValues) that represents what the Sensor has diagnosed
     * about this asset.  
     */
    public Object getValue() { return value; }

    
    
    //***************************************************Timestamping
    
   /**
     * @return timestamp of when the diagnosis value last changed
     */    
    public long getLastChangedTimestamp() { return lastChangedTimestamp; }

    /**
     * Set the timestamp of when the diagnosis value changed. Only call
     * if the value differs from the old value.
     */    
    void setLastChangedTimestamp(long t) { lastChangedTimestamp = t; }
    
    
   /**
     * @return timestamp of when the diagnosis value was last asserted
     * The value may remain the same, but its the last time that the sensor
     * updated its belief about the state. If the value actually changed,
     * then {@see #setLastChangedTimestamp } is also called.
     */    
    public long getLastAssertedTimestamp() { return lastAssertedTimestamp; }

    /**
     * Set the timestamp of when the diagnosis was last changed
     */    
    void setLastAssertedTimestamp(long t) { lastAssertedTimestamp = t; }
    
    
    
    //***************************************************Miscellaneous
    
    /**
     * Always the same value for a given class of Diagnosis.
     * @return the asset type related to this diagnosis (e.g. AGENT, NODE, HOST, NETWORK)
     */
     AssetType getAssetType() { return assetType; }
    
    /**
     * @return the asset name related to this diagnosis (e.g. "123-MSB")
     */
    public String getAssetName() { return assetName; }
    
    /**
     * @return expanded name - "type:String"
     */
    String getExpandedName() { return expandedName; }

    /**
     * @return the (read-only) tech spec for this diagnosis type
     */
    public DiagnosisTechSpecInterface getTechSpec ( ) { return diagnosisTechSpec; }


    /**
     * @return a string representing an instance of this class of the form "<classname:assetType:assetName=value>"
     */
    public String toString ( ) { return "<" + this.getClass().getName()+ ":" + 
                                              this.getAssetType() + ":" + 
                                              this.getAssetName() + "=" + 
                                              this.getValue()  + ">"; 
    }
    
    
    
    
 
    /** UniqueObject implementation */
    public UID getUID() {
       return uid;
    }

    /**
    * Set the UID (unique identifier) of this UniqueObject. Used only
    * during initialization.
    * @param uid the UID to be given to this
    **/
    public void setUID(UID uid) {
        if (this.uid != null) throw new RuntimeException("Attempt to change UID");
        this.uid = uid;
    }
    
    //public boolean compareSignature(String expandedName, String defenseName) {
    //    Logger logger = Logging.getLogger(getClass());
    //    if (logger.isDebugEnabled()) logger.debug("DefOpMode/compareSignature");
    //    return ((this.getSensorName().equals(defenseName)) &&
    //    (this.getExpandedName().equals(expandedName)));
    //}
    
    //public boolean compareSignature(String type, String id, String defenseName) {
    //    return ((this.assetType.equals(type)) &&
    //    (this.assetName.equals(id)) &&
    //    (this.defenseName.equals(defenseName)));
    // }
    
    /** Compares equality based upon UID */
    public boolean compareSignature(UID uid) {
        return (uid.equals(getUID()));
    }

    
    //*******************************relay routines*******************************************
    
    
    // Relay.Source implementation -------------------------------------------
    /**
     * Get all the addresses of the target agents to which this Relay
     * should be sent. For this implementation this is always a
     * singleton set contain just one target.
     **/
    public Set getTargets() {
        return Collections.singleton(nodeId);
    }
    
    /**
     * Get an object representing the value of this Relay suitable
     * for transmission. This implementation uses itself to represent
     * its Content.
     **/
    public Object getContent() {
        return this;
    }
    
    /**
     * @return a factory to convert the content to a Relay Target.
     **/
    public Relay.TargetFactory getTargetFactory() {
        return null;
    }
    
    
    /**
     * Set the response that was sent from a target. The coordinator doesn't
     * change this object.
     **/
    public int updateResponse(MessageAddress target, Object response) {
        
        return Relay.NO_CHANGE;
    }
    
    
    // Relay.Target implementation -----------------------------------------
    /**
     * Get the address of the Agent holding the Source copy of
     * this Relay.
     **/
    public MessageAddress getSource() {
        return agentId;
    }
    
    /**
     * Get the current response for this target. Null indicates that
     * this target has no response.
     **/
    public Object getResponse() {
        return this;
    }
    
    /**
     * Update target with new content.
     * @return true if the update changed the Relay. The LP should
     * publishChange the Relay.
     **/
    public int updateContent(Object content, Relay.Token token) {
        Diagnosis d = (Diagnosis) content;
        
        //At this pt it only propagates the getValue() attribute
        this.setValue(d.getValue());
        this.setLastAssertedTimestamp(d.getLastAssertedTimestamp());
        this.setLastChangedTimestamp(d.getLastChangedTimestamp());
        return Relay.CONTENT_CHANGE;
    }
    
    
    
    //*****************************************************************DiagnosisWrapper
    
    /**
     * The DiagnosesWrapper that is wrapping this object
     **/
    DiagnosesWrapper wrapper;
    /**
     * @return the DiagnosesWrapper that is wrapping this diagnosis
     **/
    DiagnosesWrapper getWrapper() {
        return wrapper;
    }
    
    /**
     * Set the DiagnosesWrapper that is wrapping this diagnosis
     **/
    void setWrapper(DiagnosesWrapper w) {
        wrapper = w;
    }

}
    
    