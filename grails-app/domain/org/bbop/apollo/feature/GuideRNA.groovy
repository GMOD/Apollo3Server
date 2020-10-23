package org.bbop.apollo.feature

class GuideRNA extends NcRNA{

    static mapping = {
        labels "GuideRNA", "NcRNA", "Transcript", "Feature"
    }

    static constraints = {
    }

    static String cvTerm =  "guide_RNA"
    static String ontologyId = "SO:0000602"
    static String alternateCvTerm = "GuideRNA"
}
