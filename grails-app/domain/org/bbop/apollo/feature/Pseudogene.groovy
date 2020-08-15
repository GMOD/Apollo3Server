package org.bbop.apollo.feature

import org.bbop.apollo.feature.Gene

/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class Pseudogene extends Gene{

    static constraints = {
    }

    static String cvTerm = "pseudogene"
    static String ontologyId = "SO:0000336"// XX:NNNNNNN
    static String alternateCvTerm = "Pseudogene"
}
