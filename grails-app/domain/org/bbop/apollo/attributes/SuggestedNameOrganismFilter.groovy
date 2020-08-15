package org.bbop.apollo.attributes

class SuggestedNameOrganismFilter extends OrganismFilter {

    SuggestedName suggestedName

    static constraints = {
        suggestedName nullable: false
    }
}
