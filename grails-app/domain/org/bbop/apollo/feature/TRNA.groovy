package org.bbop.apollo.feature

class TRNA extends NcRNA{

    static mapping = {
        labels "TRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "tRNA"
    static String ontologyId = "SO:0000253"
    static String alternateCvTerm = "TRNA"
}
