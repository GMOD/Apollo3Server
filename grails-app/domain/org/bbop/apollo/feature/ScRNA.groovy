package org.bbop.apollo.feature

class ScRNA extends NcRNA{

    static mapping = {
        labels "ScRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "scRNA"
    static String ontologyId = "SO:0000013"
    static String alternateCvTerm = "ScRNA"
}
