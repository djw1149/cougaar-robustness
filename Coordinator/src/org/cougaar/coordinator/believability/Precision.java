/*
 *<SOURCE_HEADER>
 *
 *<NAME>
 * $RCSfile: Precision.java,v $
 *</NAME>
 *
 *<RCS_KEYWORD>
 * $Source: /opt/rep/cougaar/robustness/Coordinator/src/org/cougaar/coordinator/believability/Precision.java,v $
 * $Revision: 1.1 $
 * $Date: 2004-02-26 15:18:24 $
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
 * Miscellaneous routines that help deasl with floating point
 * precision issues. Floating point precision issues should *not* be
 * ignored: they will cause you problems is care is not taken. 
 *
 * @author Tony Cassandra
 * @version $Revision: 1.1 $Date: 2004-02-26 15:18:24 $
 * @see
 */
public class Precision extends Object
{
    // In the original C code, these methods and their functionality
    // were done using macros, which made they relatively efficient.
    // Will the java compiler in-line these?  I don't know, but
    // depending on the complexity of the models and algorithms, the
    // encapsulation of this functionality as a full java class might
    // lead to an inefficiency that would need to be optimized.

    //--------------------------------------------------
    // public interface
    //--------------------------------------------------

    public static final double DEFAULT_POS_ZERO_TOLERANCE = 0.0000000001;
    public static final double DEFAULT_NEG_ZERO_TOLERANCE = -0.0000000001;

    //************************************************************
    /**
     * Compares a number to zero using the current precision set in
     * this module.
     */
    public static boolean isZero( double value )
    {  
        return ((value < _pos_zero_tolerance) 
                && ( value > _neg_zero_tolerance ));
    } // isZero
    //************************************************************
    /**
     * Sets the values the positive and negative tolerances used in
     * zero comparisons.  Uses just one magnitude value and  applies
     * it symmetrically to zero.  If tolerance_mag is les than zero,
     * this routine will take its absolute value to get the
     * magnitude. 
     */
    public static void setZeroTolerances( double tolerance_mag )
    {
        if ( tolerance_mag < 0.0 )
            tolerance_mag = Math.abs( tolerance_mag );

        _pos_zero_tolerance = tolerance_mag;
        _neg_zero_tolerance = -1.0 * tolerance_mag;
    }
    //************************************************************
    /**
     * Sets the values the positive and negative tolerances used in
     * zero comparisons. 
     */
    public static void setZeroTolerances( double pos_tolerance, 
                                          double neg_tolerance )
    {
        _pos_zero_tolerance = pos_tolerance;
        _neg_zero_tolerance = neg_tolerance;
    }
    //************************************************************

    //--------------------------------------------------
    // private interface
    //--------------------------------------------------

    private static double _pos_zero_tolerance = DEFAULT_POS_ZERO_TOLERANCE;
    private static double _neg_zero_tolerance = DEFAULT_NEG_ZERO_TOLERANCE;

} // class Precision