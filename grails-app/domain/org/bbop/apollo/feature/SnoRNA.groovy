package org.bbop.apollo.feature

class SnoRNA extends NcRNA{

    static mapping = {
        labels "SnoRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "snoRNA"
    static String ontologyId = "SO:0000275"
    static String alternateCvTerm = "SnoRNA"
}
