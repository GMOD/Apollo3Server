package org.bbop.apollo.location

import grails.neo4j.Relationship
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.organism.Sequence

class FeatureLocation implements Relationship<Feature,Sequence>{
    static mapping = {
        length formula: 'FMAX-FMIN'
    }
    static constraints = {
        fmin nullable: false
        fmax nullable: false
        length nullable: true
        isFminPartial nullable: true
        isFmaxPartial nullable: true
        strand nullable: true
        phase nullable: true
    }

    Integer fmin
    Integer length
    boolean isFminPartial
    Integer fmax
    boolean isFmaxPartial
    Integer strand
    Integer phase
    int rank


    /**
     * We use this as an artificial accessor in case the property has not been calculatd
     * @return
     */
    Integer calculateLength() {
        return fmax - fmin
    }


}
