package org.bbop.apollo.feature

import org.bbop.apollo.feature.NcRNA

class LncRNA extends NcRNA{

    static mapping = {
        labels "LncRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "lnc_RNA"
    static String ontologyId = "SO:0001877"
    static String alternateCvTerm = "LncRNA"
}
