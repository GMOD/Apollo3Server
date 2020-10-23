package org.bbop.apollo.feature

class TmRNA extends NcRNA{

    static mapping = {
        labels "TmRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "tmRNA"
    static String ontologyId = "SO:0000584"
    static String alternateCvTerm = "TmRNA"
}
