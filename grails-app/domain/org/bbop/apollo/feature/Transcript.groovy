package org.bbop.apollo.feature


/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class Transcript extends Feature{

    static mapping = {
        labels "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm = "transcript"
    static String ontologyId = "SO:0000673"// XX:NNNNNNN
    static String alternateCvTerm = "Transcript"

}
