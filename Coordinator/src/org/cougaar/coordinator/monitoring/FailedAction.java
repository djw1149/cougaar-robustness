/*
 * FailedAction.java
 *
 * Created on April 28, 2004, 2:43 PM
 */

package org.cougaar.coordinator.monitoring;

/**
 *
 * @author  David Wells - OBJS
 * @version 
 */

import org.cougaar.util.UnaryPredicate;

public class FailedAction {

    /** Creates new FailedAction */
    public FailedAction() {
    }

    public final static UnaryPredicate pred = new UnaryPredicate() {
        public boolean execute(Object o) {  
            return 
                (o instanceof FailedAction);
        }
    };

}