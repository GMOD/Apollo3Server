package org.bbop.apollo.attributes

/**
 * Note: top-level in the sequence ontology
 */
abstract class Frameshift extends TranscriptAttribute{

    static constraints = {
    }

    static String cvTerm = "frameshift"
    static String ontologyId = "SO:0000865"// XX:NNNNNNN
    static String alternateCvTerm = "Frameshift"

    // add convenience methods
    /** Get the coordinate for the frameshift.
     *
     * @return Coordinate for the frameshift
     */
    int getCoordinate() {
        return Integer.parseInt(getValue());
    }

    /** Returns whether this frameshift is in the plus translational direction.
     *
     * @return true if the frameshift is in the plus translational direction
     */
    abstract boolean isPlusFrameshift();

    /** Get the frameshift value.
     *
     * @return Frameshift value
     */
    abstract int getFrameshiftValue();
}
