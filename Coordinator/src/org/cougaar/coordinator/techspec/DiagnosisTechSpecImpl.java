/*
 * DiagnosisTechSpecImpl.java
 *
 * Created on March 4, 2004, 2:25 PM
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
 */

package org.cougaar.coordinator.techspec;

import org.cougaar.coordinator.*;  
import org.cougaar.core.util.UID;

import java.util.Set;
import java.util.HashSet;
import java.util.Vector;
import java.util.Iterator;

/**
 *
 * @author  Administrator
 */
public class DiagnosisTechSpecImpl implements DiagnosisTechSpecInterface  {
    
    HashSet pvs;
    Vector levels;
    String name;
    Vector threatTypes;
    UID uid;
    String revision = "0";
    Vector probabilities = null;
    
    /** Creates a new instance of DiagnosisTechSpecImpl */
    public DiagnosisTechSpecImpl(String name, UID uid) {

        this.name = name;
        this.levels = new Vector();        
        this.pvs = new HashSet();
        this.threatTypes = new Vector();
        this.uid = uid;
        this.probabilities = new Vector();
        
    }
    
    /** @return the asset type that the threat cares about.
     */
    public AssetType getAssetType() {
        return AssetType.findAssetType("AGENT");
    }
    
    /** @return the vector of monitoring levels that this sensor supports
     *
     */
    public Vector getMonitoringLevels() {
        return levels;
    }
    
    /** @return a user-readable name for this TechSpec
     *
     */
    public String getName() {
        return name;
    }
    
    /** @return the possible values that Diagnoses generated by this Sensor can return via getValue()
     */
    public Set getPossibleValues() {
        return pvs;
    }
    
    /** @return a revision string for this TechSpec
     *
     */
    public String getRevision() {
        return "0.1";
    }
    
    /** @return the ThreatTypes associated with this Defense
     */
    public Vector getThreatTypes() {
        return threatTypes;
    }
    
    /** @return a unique cougaar level name for this TechSpec
     *
     */
    public UID getUID() {
        return uid;
    }

    
    
    /** @return the vector of monitoring levels that this sensor supports
     *
     */
//    public void addMonitoringLevel() {
//        levels.add(null);
//    }
    
    /** @return the possible values that Diagnoses generated by this Sensor can return via getValue()
     */
    public void addPossibleValue(String v) {
        pvs.add(v);
    }
    
    /** @return a revision string for this TechSpec
     *
     */
    public void setRevision(String r) {
        this.revision = r;
    }
    
    /** @return the ThreatTypes associated with this Defense
     */
//    public void addThreatType() {
//        threatTypes.add(null);
//    }
    
    /**
     * @return the DiagnosisProbabilities
     *
     */
    public Vector getDiagnosisProbabilities() {
        return probabilities;
    }

    
    
    /**
     * Add a DiagnosisProbability
     *
     */
    public void addDiagnosisProbability(DiagnosisProbability dp) {
        probabilities.add(dp);
    }
    
    
    public String toString() {
     
        String s = "Sensor ["+this.getName()+"], uid="+this.getUID()+"\n";
        Iterator i = this.getDiagnosisProbabilities().iterator();
        while (i.hasNext()) {
             DiagnosisProbability dp = (DiagnosisProbability)i.next();
             s = s+ "    When Actual State Is = " + dp.getActualState() + "\n";
             Iterator probs = dp.getProbabilities().iterator();
             while (probs.hasNext()) {
                 DiagnosisProbability.DiagnoseAs da = (DiagnosisProbability.DiagnoseAs)probs.next();
                 s = s + "       Will Diagnose As " + da.getDiagnosisValue() + " with prob="+ da.getProbability() + "\n";
             }             
        }        
        return s;
    }
    
}
