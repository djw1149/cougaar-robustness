/*
 * CostBenefitDiagnosisIndex.java
 *
 * Created on April 23, 2004, 9:38 AM
 */

package org.cougaar.coordinator;
/**
 *
 * @author  David Wells - OBJS
 * @version 
 */

import java.util.Hashtable;
import java.util.Collection;

import org.cougaar.coordinator.Diagnosis;
import org.cougaar.util.UnaryPredicate;
import org.cougaar.coordinator.costBenefit.CostBenefitEvaluation;
import org.cougaar.coordinator.techspec.AssetID;
import org.cougaar.coordinator.housekeeping.IndexKey;

public class CostBenefitEvaluationIndex extends Hashtable {

    /** Creates new Class */
    public CostBenefitEvaluationIndex() {
    }
    
    protected final static UnaryPredicate pred = new UnaryPredicate() {
            public boolean execute(Object o) {  
                return 
                    (o instanceof CostBenefitEvaluationIndex);
            }
        };

    protected CostBenefitEvaluation indexCostBenefitEvaluation(CostBenefitEvaluation cbe, IndexKey key) {
        return (CostBenefitEvaluation) super.put(cbe.getAssetID(), cbe);
    }
    
    protected CostBenefitEvaluation findCostBenefitEvaluation(AssetID assetID) {
        return (CostBenefitEvaluation) super.get(assetID);
    }

}
