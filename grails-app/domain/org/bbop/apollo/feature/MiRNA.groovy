package org.bbop.apollo.feature

class MiRNA extends NcRNA{

    static mapping = {
        labels "MiRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "miRNA"
    static String ontologyId = "SO:0000276"
    static String alternateCvTerm = "MiRNA"
}
