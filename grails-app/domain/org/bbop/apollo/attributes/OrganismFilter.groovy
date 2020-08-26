package org.bbop.apollo.attributes

import org.bbop.apollo.organism.Organism

abstract class OrganismFilter {

    Organism organism

    static constraints = {
        organism nullable: false
    }
}
