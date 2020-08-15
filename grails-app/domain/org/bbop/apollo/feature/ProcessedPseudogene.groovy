package org.bbop.apollo.feature

import org.bbop.apollo.feature.Pseudogene

/**
 * Inherited from here:
 * AbstractSingleLocationBioFeature
 */
class ProcessedPseudogene extends Pseudogene{

    static constraints = {
    }

    static String cvTerm = "processed_pseudogene"
    static String ontologyId = "SO:0000043"// XX:NNNNNNN
    static String alternateCvTerm = "processed_pseudogene"
}
