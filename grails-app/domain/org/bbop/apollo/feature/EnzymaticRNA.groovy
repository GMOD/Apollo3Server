package org.bbop.apollo.feature

class EnzymaticRNA extends Transcript{

    static mapping = {
        labels "EnzymaticRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "enzymatic_RNA"
    static String ontologyId = "SO:0000372"
    static String alternateCvTerm = "EnzymaticRNA"
}
