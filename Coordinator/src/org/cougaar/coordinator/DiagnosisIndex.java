/*
 * DiagnosesIndex.java
 *
 * Created on April 13, 2004, 12:04 PM
 */

/*
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
import org.cougaar.coordinator.techspec.AssetID;
import org.cougaar.coordinator.housekeeping.IndexKey;
import org.cougaar.core.persist.NotPersistable;

public class DiagnosisIndex implements NotPersistable {

    private Hashtable entries = new Hashtable();

    /** Creates new DiagnosesIndex */
    public DiagnosisIndex() {
    }
    
    protected final static UnaryPredicate pred = new UnaryPredicate() {
            public boolean execute(Object o) {  
                return 
                    (o instanceof DiagnosisIndex);
            }
        };

    protected DiagnosesWrapper indexDiagnosis(DiagnosesWrapper dw, IndexKey key) {
        Diagnosis d = dw.getDiagnosis();
        Hashtable c = (Hashtable) entries.get(d.getAssetID());
        if (c == null) {
            c = new Hashtable();
            entries.put(d.getAssetID(), c);
            }
        return (DiagnosesWrapper) c.put(DiagnosisUtils.getSensorType(d), dw);
    }
    
    protected DiagnosesWrapper findDiagnosis(AssetID assetID, String sensorType) {
        Hashtable c = (Hashtable) findDiagnosisCollection(assetID);
        return (DiagnosesWrapper) c.get(sensorType);
    }

    protected Collection findDiagnosisCollection(AssetID assetID) {
        Hashtable c = (Hashtable) entries.get(assetID);
        if (c != null) return c.values();
        else return null;
    }

}
