package org.bbop.apollo.feature

class MRNA extends Transcript{

    static mapping = {
        labels "MRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "mRNA"
    static String ontologyId = "SO:0000234"
    static String alternateCvTerm = "MRNA"
}
