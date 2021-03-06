/*
 * <copyright>
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


package org.cougaar.coordinator.examples.SampleDefense;

import java.util.Iterator;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import org.cougaar.coordinator.*;
import org.cougaar.coordinator.techspec.TechSpecNotFoundException;
import org.cougaar.core.blackboard.IncrementalSubscription;
import org.cougaar.core.component.ServiceBroker;
import org.cougaar.core.plugin.ComponentPlugin;
import org.cougaar.core.service.LoggingService;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.core.agent.service.alarm.Alarm;

public class SampleActuator extends ComponentPlugin
{
    private LoggingService log;
    private SampleAction action;
    private SampleRawActuatorData data;
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

    private boolean start = true;

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

    private class DelayedStartAlarm implements Alarm {
	private long detonate = -1;
	private boolean expired = false;
	
	public DelayedStartAlarm (long delay) {
	    detonate = delay + System.currentTimeMillis();
	}
	
	public long getExpirationTime () {
	    return detonate;
	}
	
	public synchronized void expire () {
	    if (!expired) {
		expired = true;
		start = true;
		blackboard.signalClientActivity();
	    }
	}
	
	public boolean hasExpired () {
	    return expired;
	}
	
	public synchronized boolean cancel () {
	    if (!expired)
		return expired = true;
	    return false;
	}
    }
    
    public void setupSubscriptions() 
    {
	actionSub = 
	    (IncrementalSubscription)blackboard.subscribe(actionPred);
        rawActuatorDataSub = 
	    (IncrementalSubscription)blackboard.subscribe(rawActuatorDataPred);
//	alarmService.addRealTimeAlarm(new DelayedStartAlarm(120000));
    } 
    
    int tsLookupCnt = 0;
    public synchronized void execute() {

	if (start == true) {
	    try {
		action = new SampleAction(agentId.toString(), sb);
		blackboard.publishAdd(action);
		if (log.isDebugEnabled()) log.debug(action + " added.");
                if (log.isDetailEnabled()) log.detail(action.dump());
                if (log.isDetailEnabled()) log.detail("TechSpec="+action.getTechSpec());
		data = new SampleRawActuatorData(agentId.toString(), 
						 action.getPossibleValues(),
						 action.getValuesOffered(),
						 action.getPermittedValues(),
						 action.getValue());
		blackboard.publishAdd(data);
		if (log.isDebugEnabled()) log.debug(data+" added.");	
		start = false;
	    } catch (TechSpecNotFoundException e) {
		if (tsLookupCnt > 10) {
		    log.warn("TechSpec not found for SampleAction.  Will retry.", e);
		    tsLookupCnt = 0;
		}
		blackboard.signalClientActivity();
	    }
	}
	
	Iterator iter = rawActuatorDataSub.getChangedCollection().iterator();
	while (iter.hasNext()) {
	    SampleRawActuatorData data = (SampleRawActuatorData)iter.next();
	    if (data != null) {
		int command = data.getCommand();
		switch (command) {
		case SampleRawActuatorData.SET_VALUES_OFFERED:
		    Set valuesOffered = data.getValuesOffered();
		    try {
			action.setValuesOffered(valuesOffered);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
			if (log.isDetailEnabled()) log.detail(action.dump());
		    } catch (IllegalValueException e) {
			log.error("Illegal value in valuesOffered = "+valuesOffered,e);	
		    }
		    break;
		case SampleRawActuatorData.START:
		    Object actionValue = data.getActionValue();
		    try {
			action.start(actionValue);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
			if (log.isDetailEnabled()) log.detail(action.dump());
		    } catch (IllegalValueException e) {
			log.error("Illegal actionValue = "+actionValue,e);	
		    }	
		    break;
		case SampleRawActuatorData.STOP:
		    String codeStr = data.getCompletionCode();
                    Action.CompletionCode code = (Action.CompletionCode)codeTbl.get(codeStr);
		    try {
			action.stop(code);
			blackboard.publishChange(action);
			if (log.isDebugEnabled()) 
			    log.debug(action + " changed.");
			if (log.isDetailEnabled()) log.detail(action.dump());
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
		Set newPV = action.getNewPermittedValues();
		if (newPV != null) {
		    try {
			action.setPermittedValues(newPV);
			action.clearNewPermittedValues();
			data.setPermittedValues(newPV);
			data.setCommand(SampleRawActuatorData.SET_PERMITTED_VALUES);
			blackboard.publishChange(data);
			if (log.isDebugEnabled()) 
			    log.debug(data + " changed.");
		    } catch (IllegalValueException e) {
			log.error("Illegal permittedValues relayed from Node Agent = "+newPV);
		    }
		}
	    }
	}
    }
}
