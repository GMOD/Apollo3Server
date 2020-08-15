package org.bbop.apollo.attributes

class CannedKeyOrganismFilter extends OrganismFilter {

    CannedKey cannedKey

    static constraints = {
        cannedKey nullable: false
    }
}
