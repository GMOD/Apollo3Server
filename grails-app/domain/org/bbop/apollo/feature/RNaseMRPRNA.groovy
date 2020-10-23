package org.bbop.apollo.feature

class RNaseMRPRNA extends NcRNA{

    static mapping = {
        labels "RNaseMRPRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "RNase_MRP_RNA"
    static String ontologyId = "SO:0000385"
    static String alternateCvTerm = "RNaseMRPRNA"
}
