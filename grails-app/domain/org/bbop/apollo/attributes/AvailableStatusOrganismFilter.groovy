package org.bbop.apollo.attributes

class AvailableStatusOrganismFilter extends OrganismFilter {

    AvailableStatus availableStatus

    static constraints = {
        availableStatus nullable: false
    }
}
