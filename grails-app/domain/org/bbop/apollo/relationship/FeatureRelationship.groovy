package org.bbop.apollo.relationship

import grails.neo4j.Relationship
import org.bbop.apollo.Ontological
import org.bbop.apollo.feature.Feature

class FeatureRelationship implements  Ontological,  Relationship<Feature, Feature> {


    static constraints = {
        rank nullable: true
        value nullable: true
    }

//    Feature from;
//    Feature to;
    String value; // unused, but could be used like metadata (strength / quality of connection)
    int rank;
    static String ontologyId = "part_of"
    
//    static hasMany = [
//            featureRelationshipProperties : FeatureProperty
//            ,featureRelationshipPublications: Publication
//    ]


    boolean equals(Object other) {
        if (this.is(other)) return true
        if (getClass() != other.class) return false
        FeatureRelationship castOther = ( FeatureRelationship ) other;
        if(this?.id == castOther?.id) return true

        return  this.from ==castOther.from  \
                && this.to ==  castOther.to
    }

    int hashCode() {
        int result = 17;
        result = 37 * result + ( from == null ? 0 : this.from.hashCode() );
        result = 37 * result + ( to == null ? 0 : this.to.hashCode() );
        result = 37 * result + this.rank;
        return result;
    }

    public FeatureRelationship generateClone() {
        FeatureRelationship cloned = new FeatureRelationship();
        cloned.from = this.from;
        cloned.to = this.to;
        cloned.value = this.value;
        cloned.rank = this.rank;
        return cloned;
    }
}
