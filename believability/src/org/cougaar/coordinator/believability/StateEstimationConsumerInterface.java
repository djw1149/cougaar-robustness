/*
 *<SOURCE_HEADER>
 *
 *<NAME>
 * $RCSfile: StateEstimationConsumerInterface.java,v $
 *</NAME>
 *
 *<RCS_KEYWORD>
 * $Source: /opt/rep/cougaar/robustness/believability/src/org/cougaar/coordinator/believability/Attic/StateEstimationConsumerInterface.java,v $
 * $Revision: 1.14 $
 * $Date: 2004-07-12 19:30:46 $
 *</RCS_KEYWORD>
 *
 *<COPYRIGHT>
 * The following source code is protected under all standard copyright
 * laws.
 *</COPYRIGHT>
 *
 *</SOURCE_HEADER>
 */

package org.cougaar.coordinator.believability;

/**
 * Used to accept new state estimates and take the appropriate action.
 *
 * @author Tony Cassandra
 * @version $Revision: 1.14 $Date: 2004-07-12 19:30:46 $
 *
 */
public interface StateEstimationConsumerInterface
{

    //------------------------------------------------------------
    // public interface
    //------------------------------------------------------------

    public void consumeEstimate( StateEstimation se );

} // class StateEstimationConsumerInterface
