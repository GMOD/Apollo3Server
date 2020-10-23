package org.bbop.apollo.feature

import org.bbop.apollo.feature.NcRNA

class PiRNA extends NcRNA{

    static mapping = {
        labels "PiRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "piRNA"
    static String ontologyId = "SO:0001035"
    static String alternateCvTerm = "PiRNA"
}
