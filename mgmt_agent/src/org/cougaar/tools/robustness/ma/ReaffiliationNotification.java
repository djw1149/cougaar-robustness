/*
 * <copyright>
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
package org.cougaar.tools.robustness.ma;

import org.cougaar.tools.robustness.threatalert.DefaultThreatAlert;
import org.cougaar.core.mts.MessageAddress;

/**
 * ReaffiliationNotification signifying that one or more enclave members is
 * being added/removed from robustness community.
 */
public class ReaffiliationNotification extends DefaultThreatAlert {

  private String entityType = "Node";
  /**
   * Default constructor.
   */
  public ReaffiliationNotification(MessageAddress source,
                                   String         newCommunity) {
    super();
    setSource(source);
    setContent(newCommunity);
  }

  public ReaffiliationNotification(MessageAddress source,
                                   String         newCommunity,
                                   String         entityType) {
    this (source, newCommunity);
    this.entityType = entityType;
  }

  public String getCommunity() {
    return (String)getContent();
  }

  public String getEntityType() {
    return entityType;
  }

}
