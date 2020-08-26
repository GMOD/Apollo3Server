package org.bbop.apollo.feature

import org.bbop.apollo.feature.TranscriptRegion

/**
 * NOTE: superclass is NOT region . . .
 */
class Intron extends TranscriptRegion{

    static constraints = {
    }


    static String cvTerm = "intron"
    static String ontologyId = "SO:0000188"// XX:NNNNNNN
    static String alternateCvTerm = "Intron"
}
