package org.bbop.apollo.feature

class RNasePRNA extends NcRNA{

    static mapping = {
        labels "RNasePRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "RNase_P_RNA"
    static String ontologyId = "SO:0000386"
    static String alternateCvTerm = "RNasePRNA"
}
