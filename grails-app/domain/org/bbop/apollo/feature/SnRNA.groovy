package org.bbop.apollo.feature

class SnRNA extends Transcript{

    static mapping = {
        labels "SnRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "snRNA"
    static String ontologyId = "SO:0000274"
    static String alternateCvTerm = "SnRNA"

}
