/*
 * SensorTypeLoader.java
 *
 * Created on March 18, 2004, 5:29 PM
 * <copyright>
 *  Copyright 2003 Object Services and Consulting, Inc.
 *  Copyright 2001-2003 Mobile Intelligence Corp
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

package org.cougaar.coordinator.techspec.xml;

import org.cougaar.coordinator.techspec.*;

import org.cougaar.core.plugin.ComponentPlugin;

import org.cougaar.core.service.LoggingService;

import org.cougaar.core.service.UIDService;

import org.cougaar.util.log.Logging;
import org.cougaar.util.log.Logger;
import org.cougaar.core.util.UID;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.util.UnaryPredicate;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.ArrayList;

import org.w3c.dom.*;

/**
 * This class is used to import techspecs from xml files.
 *
 * @author  Administrator
 */
public class SensorTypeLoader extends XMLLoader {
    
    Vector sensorTypes;
    DiagnosisTechSpecService diagnosisTechSpecService = null;
    
    /** Creates a new instance of SensorTypeLoader */
    public SensorTypeLoader() {
        
        super("SensorType", "SensorTypes"); //, requiredServices);
        sensorTypes = new Vector();
    }
    
    
    private static final Class[] requiredServices = {
        DiagnosisTechSpecService.class
    };
    
    /* Acquire needed services */
    private boolean haveServices() { //don't use logger here... until after super.load() is called

            diagnosisTechSpecService =
            (DiagnosisTechSpecService) getServiceBroker().getService(this, DiagnosisTechSpecService.class, null);
            if (diagnosisTechSpecService == null) {
                throw new RuntimeException(
                "Unable to obtain tech spec service");
            } 
            
            return true;
            
    }
    
    public void load() {
        
        haveServices(); //call have services first !!!
        super.load(); //loads in & begins parsing of xml files.
        
    }
    
    
    /** Called with a DOM "SensorType" element to process */
    protected void processElement(Element element) {
        
        //publish to BB during execute().
        //1. Create a new AssetType instance &
        String sensorName = element.getAttribute("name");
        String type = element.getAttribute("sensesAssetType");
        String stateDim = element.getAttribute("sensesStateDimension");
        String lat = element.getAttribute("sensorLatency"); //int msec
        
        try {
            
            //covert sensor latency to an int
            int latency = Integer.parseInt(lat);
            
            AssetType sensesAssetType = AssetType.findAssetType(type);
            
            //what to do when assetType is null? - create it, process it later?
            if (sensesAssetType == null) {
                logger.warn("SensorType XML Error - sensesAssetType unknown: "+type);
                return;
            }
            
            if (us == null) {
                logger.warn("SensorType XML Error - UIDService is null!");
                return;
            }
            UID uid = us.nextUID();
            DiagnosisTechSpecImpl sensor = new DiagnosisTechSpecImpl( sensorName, uid);
            diagnosisTechSpecService.addDiagnosisTechSpec( sensorName, sensor );
            
            //Create a SensorType
            Element e;
            for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equalsIgnoreCase("PotentialDiagnoses") ) {
                    e = (Element)child;
                    parsePotentialDiagnoses(e, sensor);
                } else if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equalsIgnoreCase("Diagnoses") ) {
                    e = (Element)child;
                    parseDiagnoses(e, sensor);
                } //else, likely a text element - ignore
            }
            
            logger.debug("Added new Sensor: \n"+sensor.toString() );
            
        } catch (NumberFormatException nfe) { //converting string to int
            logger.warn("SensorType XML Error for ["+sensorName+"]- Bad integer in latency: " + lat);
        }
    }
    
    
    protected void execute() {}
    
    
    private void parsePotentialDiagnoses(Element element, DiagnosisTechSpecImpl sensor) {
        
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equalsIgnoreCase("Diagnosis") ) {
                Element e = (Element) child;
                String diagnosis = e.getAttribute("name");
                
                if (diagnosis != null) {
                    sensor.addPossibleValue(diagnosis);
                }
                
            } //else, likely a text element - ignore
        }
    }
    
    
    
    
    private void parseDiagnoses(Element element, DiagnosisTechSpecImpl sensor) {
        
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equalsIgnoreCase("WhenActualStateIs") ) {
                Element e = (Element) child;
                String diagnosis = e.getAttribute("name");
                
                DiagnosisProbability dp = new DiagnosisProbability(diagnosis);
                sensor.addDiagnosisProbability(dp);
                parseWhenActualStateIsElements(e, dp, sensor);
                
            } //else, likely a text element - ignore
        }
    }
    
    
    
    
    private void parseWhenActualStateIsElements(Element element, DiagnosisProbability probs, DiagnosisTechSpecImpl sensor) {
        
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equalsIgnoreCase("WillDiagnoseAs") ) {
                Element e = (Element) child;
                String name = e.getAttribute("name");
                String p = e.getAttribute("withProbability");
                
                try {
                    float prob = Float.parseFloat(p);
                    probs.addProbability(name, prob);
                } catch (Exception ex) {
                    logger.warn("SensorType XML Error for ["+sensor.getName()+"]- Bad float in probability for ["+probs.getActualState()+"]: " + p);
                    continue; // ignore this one & move on.
                }
            } //else, likely a text element - ignore
        }
    }
    
    
}


