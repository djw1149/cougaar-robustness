/*
 *<SOURCE_HEADER>
 *
 *<NAME>
 * $RCSfile: BelievabilityAction.java,v $
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
import org.cougaar.coordinator.techspec.AssetType;
import org.cougaar.coordinator.techspec.ActionTechSpecInterface;
import org.cougaar.coordinator.Action;
import org.cougaar.coordinator.techspec.ActionDescription;

/**
 * The class that contains a local copy of pertinent information
 * related to the action.
 * 
 * @author Tony Cassandra
 * @author Shilpa Bondale
 * @see org.cougaar.coordinator.Action
 * @see org.cougaar.coordinator.ActionRecord
 */


class BelievabilityAction extends BeliefUpdateTrigger {

    //---------------------------------------------------------------
    // package interface
    //---------------------------------------------------------------

    /**
     * Constructor, using an Action from the blackboard
     *
     * @param action The action from the blackboard
     */
    public BelievabilityAction( Action action, ActionTechSpecInterface tech) 
    {
        super( action.getAssetID() );
        // _blackboard_action = action;
     _actuator_info = tech;
     _asset_id = action.getAssetID();

     setTriggerTimestamp( action.getValue().getEndTime() );

     _action_state_dim = action.getAssetStateDimensionName();

     if ( _action_state_dim == null )
         if ( _logger.isWarnEnabled() )
             _logger.warn( "Action.getAssetStateDimensionName() returned NULL" );

     _actuator_name = tech.getName();
     _action_record_string = action.getValue().toString();
     _action_status = action.getValue().getCompletionCodeString();
     _action_has_completed = action.getValue().hasCompleted();
     //_action_description = " ";
     _action_description = tech.getActionDescriptionForValue(action,(Object)action.getValue().getAction()).name();

     if ( _logger.isDetailEnabled() )
         _logger.detail( "Got time stamp frmo Actuator Tech Spec Handle.\n BelievabilitAction constructor done." );
    } // constructor BelievabilityAction
    

    //---------------------------------------------------------------
    // public interface
    //---------------------------------------------------------------

    /**
     * Return the believability action as a string
     */
    public String toString() {
        StringBuffer buff = new StringBuffer();

        buff.append( "BelievabilityAction:" + _action_description + "\n");
     buff.append( "\t Actuator Name:" + _actuator_name + "\n");
     buff.append( "\tAction Description:" + _action_description + "\n");
     buff.append( "\tAction State Dimension Name" + _action_state_dim + "\n");
     buff.append( "\tAction Completion Time:" + getTriggerTimestamp() + "\n");
     buff.append( "\tAction Status:" + _action_status + "\n");
     
     /*for (int dim_count = 0; dim_count < _action_state_dim.length; dim_count++){
          buff.append(_action_state_dim[dim_count].toString());
     }*/
     
        return buff.toString();
    } // method toString

    /**
     * This routine should return the asset state dimension name that
     * this trigger pertains to.
     */
    public String getStateDimensionName()
    {
     try{
         if ( _logger.isDetailEnabled() )
             _logger.detail("Getting StateDimensionName for :" 
                            + _asset_id.toString()); 
         return _action_state_dim;
     } catch (Exception e){
         return null;
     }
    } // method getStateDimensionName
    
    /**
     * A string value indicating the current status of the Believability action taken by the actuator.
     *@return status of BelievabilityAction 
     */
    public String getBelivabilityActionStatus(){
        if ( _logger.isDetailEnabled() )
            _logger.detail("Believability Action status for " 
                           + _asset_id.toString() + " is " + _action_status );
      return _action_status;
    }
    /**
     *
     *
     */
    public boolean getBelievabilityActionSuccessStatus(){
     try {
         if ((_action_has_completed) && 
          (_action_status.compareToIgnoreCase("COMPLETED") == 0)){

             if ( _logger.isDetailEnabled() )
                 _logger.detail("Believability Action  for " 
                                + _asset_id.toString() 
                                + " is  successfully completed");

          return true;
         } else {return false; } 
     } catch (Exception e){
         return false;
     }
     
    }
    
    
    /**
     * Returns the name of the actuator that this action is a part of.
     * This comes from the getName() method of the
     * ActionTechSpecInterface object.
     * @return the actuator name
     */
     public String getActuatorName( ){
      return _actuator_name;
     }
     
    /**
     * Returns the action description for the action.
     * @return the ActionDescription
     */
     public String getActionValue( ){
      return _action_description;
     }
    
    //---------------------------------------------------------------
    // private interface
    //---------------------------------------------------------------

    // The object where the action information came from
    // This may change so we want to copy information out.
    // private Action _blackboard_action;
    //private ActionTechSpecInterface _action_tech;

    private ActionTechSpecInterface _actuator_info;
    private String _action_status;
    private boolean _action_has_completed;
    private String _action_state_dim;
    private String _action_description;
    private String _action_record_string;
    private String _actuator_name;
    private AssetID _asset_id;

} // class BelievabilityAction

