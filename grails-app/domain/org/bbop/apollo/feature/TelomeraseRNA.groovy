package org.bbop.apollo.feature

class TelomeraseRNA extends NcRNA{

    static mapping = {
        labels "TelomeraseRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "telomerase_RNA"
    static String ontologyId = "SO:0000390"
    static String alternateCvTerm = "TelomeraseRNA"
}
