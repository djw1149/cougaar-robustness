/*
 * <copyright>
 *  Copyright 2004 Object Services and Consulting, Inc.
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
 */

package org.cougaar.coordinator.examples.SampleDefense;

import java.util.Iterator;
import java.util.Hashtable;
import java.util.Set;
import org.cougaar.coordinator.*;
import org.cougaar.coordinator.techspec.TechSpecNotFoundException;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.service.LoggingService;
import org.cougaar.util.UnaryPredicate;

public class SampleActuator extends ComponentPlugin
{
    private LoggingService log;
    private SampleAction action;
    private SampleRawActuatorData rawActuatorData;
    private ServiceBroker sb;
    
    private IncrementalSubscription actionSub;
    private IncrementalSubscription rawActuatorDataSub;
    
    private UnaryPredicate actionPred = new UnaryPredicate() {
	    public boolean execute(Object o) {
		return (o instanceof SampleAction);}};
    private UnaryPredicate rawActuatorDataPred = new UnaryPredicate() {
	    public boolean execute(Object o) {
		return (o instanceof SampleRawActuatorData);}};

    private static final Hashtable codeTbl;

    static {
	codeTbl = new Hashtable();
	codeTbl.put("COMPLETED", Action.COMPLETED);
	codeTbl.put("ABORTED", Action.ABORTED);
	codeTbl.put("FAILED", Action.FAILED);
    }
    
    public void load() {
	super.load();
	
	sb = getServiceBroker();
	log = (LoggingService)
	    sb.getService(this, LoggingService.class, null);
    }
    
    public synchronized void unload() {
    }
    
    public void setupSubscriptions() 
    {
	actionSub = 
	    (IncrementalSubscription)blackboard.subscribe(actionPred);
        rawActuatorDataSub = 
	    (IncrementalSubscription)blackboard.subscribe(rawActuatorDataPred);
	Set initialValuesOffered = null;
	try {
	    action = new SampleAction(agentId.toString(), initialValuesOffered, sb);
	    blackboard.publishAdd(action);
	    if (log.isDebugEnabled()) log.debug(action + " added.");
	    SampleRawActuatorData data = new SampleRawActuatorData(agentId.toString(), action.getPossibleValues());
	    blackboard.publishAdd(data);
	    if (log.isDebugEnabled()) log.debug(data + " added.");	
	} catch (IllegalValueException e) {
	    log.error("Illegal initialValuesOffered = "+initialValuesOffered, e);
	} catch (TechSpecNotFoundException e) {
	    log.error("TechSpec not found for SampleAction", e);
	}
    } 
    
    public synchronized void execute() {
	
	Iterator iter = rawActuatorDataSub.getChangedCollection().iterator();
	while (iter.hasNext()) {
	    rawActuatorData = (SampleRawActuatorData)iter.next();
	    if (rawActuatorData != null) {
		int command = rawActuatorData.getCommand();
		switch (command) {
		case SampleRawActuatorData.SET_VALUES_OFFERED:
		    Set valuesOffered = rawActuatorData.getValuesOffered();
		    try {
			action.setValuesOffered(valuesOffered);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
		    } catch (IllegalValueException e) {
			log.error("Illegal value in valuesOffered = "+valuesOffered,e);	
		    }
		    break;
		case SampleRawActuatorData.START:
		    Object actionValue = rawActuatorData.getActionValue();
		    try {
			action.start(actionValue);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
		    } catch (IllegalValueException e) {
			log.error("Illegal actionValue = "+actionValue,e);	
		    }	
		    break;
		case SampleRawActuatorData.STOP:
		    String codeStr = rawActuatorData.getCompletionCode();
		    Action.CompletionCode code = (Action.CompletionCode)codeTbl.get(codeStr);
		    try {
			action.stop(code);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
		    } catch (IllegalValueException e) {
			log.error("Illegal completion code = "+code,e);	
		    } catch (NoStartedActionException e) {
			log.error("stop called before start",e);	
		    }		    
		}
	    }
	}
	
        iter = actionSub.getChangedCollection().iterator();
	while (iter.hasNext()) {
	    SampleAction action = (SampleAction)iter.next();
	    if (action != null) {
                rawActuatorData.setPermittedValues(action.getPermittedValues());
                rawActuatorData.setCommand(SampleRawActuatorData.SET_PERMITTED_VALUES);
                blackboard.publishChange(rawActuatorData);
		if (log.isDebugEnabled()) 
		    log.debug(rawActuatorData + " changed.");
	    }
	}
        
    }
}


