package org.bbop.apollo.attributes

class CannedValueOrganismFilter extends OrganismFilter {

    CannedValue cannedValue

    static constraints = {
        cannedValue nullable: false
    }
}
