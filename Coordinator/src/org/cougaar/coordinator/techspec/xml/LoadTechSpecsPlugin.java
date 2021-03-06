/*
 * LoadTechSpecsPlugin.java
 *
 * Created on May 26, 2004, 8:59 AM
 *
 * 
 * <copyright>
 * 
 *  Copyright 2004 Object Services and Consulting, Inc.
 *  under sponsorship of the Defense Advanced Research Projects
 *  Agency (DARPA).
 *
 *  You can redistribute this software and/or modify it under the
 *  terms of the Cougaar Open Source License as published on the
 *  Cougaar Open Source Website (www.cougaar.org).
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 *  A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 *  OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 *  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 *  LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 *  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 *  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 *  OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * </copyright>
 */

package org.cougaar.coordinator.techspec.xml;

import org.cougaar.coordinator.*;
import org.cougaar.coordinator.techspec.*;

import org.cougaar.core.adaptivity.ServiceUserPluginBase;

import org.cougaar.core.service.LoggingService;

import org.cougaar.core.service.UIDService;

import org.cougaar.core.service.LoggingService;
import org.cougaar.util.log.Logging;
import org.cougaar.util.log.Logger;

import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.util.UnaryPredicate;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;
import java.util.ArrayList;

import org.xml.sax.InputSource;
import java.io.File;
import java.io.FileInputStream;
import org.cougaar.core.persist.NotPersistable;

import org.w3c.dom.*;

import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.node.NodeControlService;
import org.cougaar.core.node.NodeIdentificationService;
import org.cougaar.core.mts.MessageAddress;

/**
 * This class is used to import techspecs from xml files.
 *
 * @author  Administrator
 */
public class LoadTechSpecsPlugin extends ServiceUserPluginBase implements NotPersistable  {
    
    //protected LoggingService logger;

    protected UIDService us = null;

    //Vectors of tech spec files, each will contain zero or more Document objects
    Vector actuatorTypes;
    Vector assetStateDims;
    Vector assetSubtypes;
    Vector assetTypes;
    Vector crossDiagnoses;
    Vector threats;
    Vector sensors;
    Vector events;
    Vector utilities;
    
    ActuatorTypeLoader actuatorLoader;
    AssetStateDimensionLoader assetStateDimLoader;
    AssetSubtypeLoader assetSubtypeLoader;
    AssetTypeLoader assetTypeLoader;
    CrossDiagnosisLoader crossDiagnosisLoader;
    ThreatLoader threatLoader;
    SensorTypeLoader sensorLoader;
    EventLoader eventLoader;
    SocietalUtilityLoader utilityLoader;
    DiagnosisTechSpecService diagnosisTechSpecService = null;
    ActionTechSpecService actionTechSpecService = null;

    Vector eventDescriptions = null;
    Vector threatDescriptions = null;
    
    ActionTechSpecServiceProvider atssp;
    DiagnosisTechSpecServiceProvider dtssp;

    ServiceBroker rootsb; //root service broker -- used to make services available to all agents on a node.
    boolean isMgmtAgent = false;
    
    
    public LoadTechSpecsPlugin() {
        super(requiredServices);   
    }
    
    private static final Class[] requiredServices = {
        ActionTechSpecService.class, 
        DiagnosisTechSpecService.class,
        UIDService.class
    };

    
    /** Creates a new instance of AssetTypeLoader */
//    public LoadTechSpecsPlugin() { 

    public void initialize() {

        super.initialize();
        
        actuatorTypes = new Vector(3,3);
        assetStateDims = new Vector(3,3);
        assetSubtypes = new Vector(3,3);
        assetTypes = new Vector(3,3);
        crossDiagnoses = new Vector(3,3);
        threats = new Vector(3,3);
        sensors = new Vector(3,3);
        events = new Vector(3,3);
        utilities = new Vector(3,3);
        
    }
    
    
    private boolean haveServices() {
        
            us = (UIDService )
            getServiceBroker().getService( this, UIDService.class, null ) ;
            if (us == null) {
                throw new RuntimeException("Unable to obtain UIDService");
            }

            //this.logger = (LoggingService)  this.getServiceBroker().getService(this, LoggingService.class, null);
            //if (logger == null) {
            //    throw new RuntimeException("Unable to obtain LoggingService");
            //}

            // **********************************************get the nodeId
            NodeIdentificationService nodeIdService = (NodeIdentificationService)
                getServiceBroker().getService(this, NodeIdentificationService.class, null);
            if (nodeIdService == null) {
                throw new RuntimeException("Unable to obtain node-id service");
            }
            
            MessageAddress nodeId = nodeIdService.getMessageAddress();
            getServiceBroker().releaseService(this, NodeIdentificationService.class, nodeIdService);
            if (nodeId == null) {
                throw new RuntimeException(
                "Unable to obtain node id");
            }

            // first check if these services all ready exist
            diagnosisTechSpecService =
            (DiagnosisTechSpecService) getServiceBroker().getService(this, DiagnosisTechSpecService.class, null);

            actionTechSpecService =
            (ActionTechSpecService) getServiceBroker().getService(this, ActionTechSpecService.class, null);

            isMgmtAgent = (!nodeId.equals(agentId) );
            if (isMgmtAgent) {
                if (logger.isDebugEnabled()) logger.debug("*****************************************************************************Loaded on mgmt agent.");
            } else {            
                if (logger.isDebugEnabled()) logger.debug("*****************************************************************************Loaded on node.");
            }
            
            if (!isMgmtAgent) {
                
                //Publish tech spec services to node control service, ONLY if on a node
                //& not in the mgmt agent
                NodeControlService ncs = (NodeControlService)
                    getServiceBroker().getService(this, NodeControlService.class, null);
                if (ncs != null) {
                    rootsb = ncs.getRootServiceBroker();
                    getServiceBroker().releaseService(this, NodeControlService.class, ncs);
                } else {
                    logger.error("Unable to obtain NodeControlService");
                }
            } else {
                //Just add services to local mgmt agent, so set the root service broker (rootsb) = the local service broker
                rootsb = getServiceBroker();
            }

	    if (diagnosisTechSpecService == null) {
		// create and advertise our service node-wide
		this.dtssp = new DiagnosisTechSpecServiceProvider();
		rootsb.addService(DiagnosisTechSpecService.class, dtssp);
		diagnosisTechSpecService =
		    (DiagnosisTechSpecService) getServiceBroker().getService(this, DiagnosisTechSpecService.class, null);
		if (diagnosisTechSpecService == null) {
		    logger.error("Unable to obtain DiagnosisTechSpecService");
		}
	    }

	    if (actionTechSpecService == null) {
		// create and advertise our service node-wide
		this.atssp = new ActionTechSpecServiceProvider();
		rootsb.addService(ActionTechSpecService.class, atssp);
		actionTechSpecService =
		    (ActionTechSpecService) getServiceBroker().getService(this, ActionTechSpecService.class, null);
		if (actionTechSpecService == null) {
		    logger.error(
				 "Unable to obtain ActionTechSpecService");
		}
	    }
            return true;
//        }
//        else if (logger.isDebugEnabled()) logger.error(".haveServices - at least one service not available!");
//        return false;

    }
    
    
    
    
    /** Read in xml files */
    public void load() {
        
        super.load();
        
        haveServices();

        //Instantiate each tech spec loader
        actuatorLoader = new ActuatorTypeLoader(actionTechSpecService, getServiceBroker(), us, getConfigFinder());
        assetStateDimLoader = new AssetStateDimensionLoader(getServiceBroker(), us);
        assetSubtypeLoader = new AssetSubtypeLoader(getServiceBroker(), us);
        assetTypeLoader = new AssetTypeLoader(getServiceBroker(), us);
        crossDiagnosisLoader = new CrossDiagnosisLoader(diagnosisTechSpecService, getServiceBroker(), us);
        threatLoader = new ThreatLoader(getServiceBroker(), us, getConfigFinder());
        sensorLoader = new SensorTypeLoader(diagnosisTechSpecService, getServiceBroker(), us);
        eventLoader = new EventLoader(getServiceBroker(), us, getConfigFinder());
        utilityLoader = new SocietalUtilityLoader(getServiceBroker(), us);

        actuatorLoader.load();
        assetStateDimLoader.load();
        assetSubtypeLoader.load();
        assetTypeLoader.load();
        crossDiagnosisLoader.load();
        threatLoader.load();
        sensorLoader.load();
        eventLoader.load();
        utilityLoader.load();
        
        
        //------------------------------
        //read in the xml files to parse
        //------------------------------
        Vector files = getPluginParams();
        
        //Create DOM parser
        DOMifier dom = new DOMifier(getConfigFinder());
        Document doc;
        String filename;
        
        //Start processing files.
        for (Iterator iter = files.iterator(); iter.hasNext(); ) {
            //1. Call dom to parse
            filename = (String)iter.next();
            try {
                doc = dom.parseFile(filename);
                
                //Now store doc into appropriate vector
                storeDoc(doc, filename);
                                
            } catch (Exception e) {
                logger.error("Error parsing XML file [" + filename + "]. Error was: "+ e.toString(), e);
            }
            
        }
        
        //Now, process the tech specs in the desired order.
        processTechSpecs();
        
    }
    
    //Publish the events & threats
    public void setupSubscriptions() {
        eventLoader.publishEvents(blackboard, eventDescriptions);
        threatLoader.publishThreats(blackboard);        
        //Announce tech specs
        blackboard.publishAdd(new TechSpecsLoadedCondition());

        //Now set attrs to null to allow garbage collecting of loader attrs.
        cleanup();
    }
    

    /** This method controls the loading of tech specs to ensure ordered processing to account
     * for inter-dependencies.
     */
    private void processTechSpecs() {


        //1. Load new asset types first
        loadDocs( assetTypeLoader, assetTypes );
        //2. Load asset subtypes
        loadDocs( assetSubtypeLoader, assetSubtypes );
        //3. Load asset state dimensions
        loadDocs( assetStateDimLoader, assetStateDims );
        //4. Load societal utilities
        loadDocs( utilityLoader, utilities );

        //5. Load events & publish them
            eventDescriptions = loadDocs( eventLoader, events ); //must publish        
        if (eventDescriptions != null) { //it would be null if this isn't the MgmtAgent
            eventLoader.setEventLinks(eventDescriptions);
        }
        
        //Load actuators & sensors & cross diagnoses
        loadDocs( actuatorLoader,actuatorTypes );
        loadDocs( sensorLoader, sensors );
        loadDocs( crossDiagnosisLoader, crossDiagnoses );

        //Load threats & publish them
        threatDescriptions = loadDocs( threatLoader, threats ); //must publish
        if (threatDescriptions != null & eventDescriptions != null) { //it would be null if this isn't the MgmtAgent
            threatLoader.setEventLinks(threatDescriptions, eventDescriptions);
        }    
    }
    
    /**
     * Set all major objects to null to allow them to be garbage collected
     */
    private void cleanup() {

        actuatorTypes = null;
        assetStateDims = null;
        assetSubtypes = null;
        assetTypes = null;
        crossDiagnoses = null;
        threats = null;
        sensors = null;
        events = null;
        utilities = null;

        actuatorLoader= null;
        assetStateDimLoader = null;
        assetSubtypeLoader = null;
        assetTypeLoader = null;
        crossDiagnosisLoader = null;
        threatLoader = null;
        sensorLoader = null;
        eventLoader = null;
        utilityLoader = null;
        
    }
    
    /** Causes the XMLLoader to load the tech specs */
    private Vector loadDocs(XMLLoader loader, Vector docs) {
     
        Vector objects = null;
        for (Iterator i = docs.iterator(); i.hasNext(); ) {
        
            objects = loader.processDocument( (Document) i.next() ) ;
        }
        return objects; //vector is accumulative in class
    }
    
    
    /** This method simply examines the tech spec file for the root tag to determine
     *  which vector it should be stored in.
     */
    private void storeDoc(Document doc, String filename) {
     
        //First see if there is more than one
        if (doc == null) return;
        
        Element root = doc.getDocumentElement();
        String tag = root.getTagName();
        
        if (actuatorLoader.isValidTag(tag))  { actuatorTypes.add(doc); return; }
        if (assetStateDimLoader.isValidTag(tag))  { assetStateDims.add(doc); return; }
        if (assetSubtypeLoader.isValidTag(tag))  { assetSubtypes.add(doc); return; }
        if (assetTypeLoader.isValidTag(tag))  { assetTypes.add(doc); return; }
        if (crossDiagnosisLoader.isValidTag(tag))  { crossDiagnoses.add(doc); return; }
        if (threatLoader.isValidTag(tag))  { threats.add(doc); return; }
        if (sensorLoader.isValidTag(tag))  { sensors.add(doc); return; }
        if (eventLoader.isValidTag(tag))  { events.add(doc); return; }
        if (utilityLoader.isValidTag(tag))  { utilities.add(doc); return; }
        else { // unknown tag!
            logger.warn("Saw unknown tag ["+tag+"]. Ignoring file = "+ filename);
        }
        
    }
    
    
    
    
    /**
     * Read in XML file parameters passed in via configuration file.
     *
     * *****!!!!!
     * Temp - read in the # of assets to see before emitting AllAssetsSeenCondition ****************** STILL DO THIS IN 2004???
     * *****!!!!!
     *
     */
    private Vector getPluginParams() {
        
        if (logger.isInfoEnabled() && getParameters().isEmpty()) logger.error("plugin saw 0 parameters.");
        
        String fileParam;
        int count = 0;
        Vector files = new Vector();
        Iterator iter = getParameters().iterator();
        while (iter.hasNext()) {
            fileParam = (String) iter.next();
            files.add(fileParam);
            count++;
        }
        if (logger.isDebugEnabled()) logger.debug("*** Plugin read " +count+" XML files ***");
        
        return files;
        
    }
        
    
    public void unload() {
        
        super.unload();
        
        if ((logger != null) && (logger != LoggingService.NULL)) {
            getServiceBroker().releaseService(
            this, LoggingService.class, logger);
            logger = LoggingService.NULL;
        }

        if (us != null) {
            getServiceBroker().releaseService(
            this, UIDService.class, us);
            us = null;
        }
        
    }
    
    
    protected void execute() {}
    
    
    
}
