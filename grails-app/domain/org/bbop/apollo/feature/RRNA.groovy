package org.bbop.apollo.feature

class RRNA extends NcRNA{

    static mapping = {
        labels "RRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "rRNA"
    static String ontologyId = "SO:0000252"
    static String alternateCvTerm = "RRNA"
}
