/*
 * ActionRecord.java
 *
 * Created on February 10, 2004, 9:37 AM
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

import java.io.Serializable;
import org.cougaar.core.persist.NotPersistable;

/**
 * An action, timestamp, and completionCode (start, stop -- completed, aborted, failed)
 * @author  Administrator
 */
public class ActionRecord implements NotPersistable, Serializable {
    
     long start_time;
     long end_time = 0;
     Object action;
     Action.CompletionCode completionCode = null;

     ActionRecord(Object action, long start_time) {
         this.start_time = start_time;
         this.action = action;
     }

     /**
      * @return the timestamp of when the completionCode on this action occurred
      */
     public long getStartTime() { return start_time; }

     /**
      * @return the timestamp of when the completionCode on this action occurred
      */
     public long getEndTime() { return end_time; }

     /**
      * @return TRUE if the action completed
      */
     public boolean hasCompleted() { return ( end_time > 0 ); }

     // dlw - added so we can check is an Action completed its setup & is currently working
     public boolean isActive() { return ( completionCode == null ? false : completionCode.equals(Action.ACTIVE) ); }  
     
     /**
      * @return the action that occurred
      */
     public Object getAction() { return action; }

     /**
      * @return the completionCode that took place
      * @see Action#COMPLETED
      * @see Action#FAILED
      * @see Action#ABORTED
      */
     public Action.CompletionCode getCompletionCode() { return completionCode; }

     
     /** Return the completion code as a string */
     public String getCompletionCodeString() {

        if (this.completionCode == Action.COMPLETED) {return "COMPLETED";}
        if (this.completionCode == Action.ENDED_UNKNOWN) {return "ENDED UNKNOWN";}
        if (this.completionCode == Action.FAILED) {return "FAILED";}
        if (this.completionCode == Action.ABORTED) {return "ABORTED";}
        if (this.completionCode == Action.ACTIVE) {return "ACTIVE";}
        return "NULL"; //not assigned yet
     }
    
        /**
      * Set the completionCode that took place
      * @see Action#COMPLETED
      * @see Action#FAILED
      * @see Action#ABORTED
      */
     public void setCompletionCode(Action.CompletionCode completionCode, long end_time) { 
         this.completionCode = completionCode; 
         this.end_time = end_time;
     }
     
     /**
      * @return "Action=action;status=completionCode;start=timestamp;end=timestamp"
      */
     public String toString() { return "Action="+action +
                                       ";status=" + completionCode +
                                       ";start=" + start_time +
                                       ";end=" + end_time; }         
 }
