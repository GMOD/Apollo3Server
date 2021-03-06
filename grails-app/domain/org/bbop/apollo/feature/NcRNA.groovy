package org.bbop.apollo.feature

class NcRNA extends Transcript {

    static mapping = {
        labels "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm = "ncRNA"
    static String ontologyId = "SO:0000655"
    static String alternateCvTerm = "NcRNA"
}
