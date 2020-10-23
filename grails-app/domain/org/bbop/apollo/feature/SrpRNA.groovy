package org.bbop.apollo.feature

class SrpRNA extends NcRNA{

    static mapping = {
        labels "SnoRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "SRP_RNA"
    static String ontologyId = "SO:0000590"
    static String alternateCvTerm = "SrpRNA"
}
