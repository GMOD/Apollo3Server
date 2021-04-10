package org.bbop.apollo.feature


import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.attributes.FeatureProperty
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.attributes.FeatureSynonym
import org.bbop.apollo.Ontological

import org.bbop.apollo.provenance.Provenance
//import org.bbop.apollo.Publication
import org.bbop.apollo.attributes.Status
import org.bbop.apollo.user.User
import org.bbop.apollo.attributes.DBXref
import org.bbop.apollo.geneProduct.GeneProduct
import org.bbop.apollo.go.GoAnnotation

class Feature {

    static constraints = {
        name nullable: false
        uniqueName nullable: false
        dateCreated nullable: true
        lastUpdated nullable: true
        symbol nullable: true // TODO: should be false and unique per organism
        description nullable: true
        status nullable: true
        featureLocation nullable: true
    }

    String symbol
    String description
    String name;
    String uniqueName;
    Status status
    boolean isObsolete;
    Date dateCreated;
    Date lastUpdated ;
    FeatureLocation featureLocation
    static String ontologyId
    static String cvTerm

//    static fetchMode = [featureLocation:'eager']


    static hasMany = [
            parentFeatureRelationships: FeatureRelationship  // relationships where I am the parent feature relationship
            ,childFeatureRelationships: FeatureRelationship // relationships where I am the child feature relationship
            ,featureSynonyms: FeatureSynonym // remove?
            ,featureDBXrefs: DBXref
            ,featureProperties: FeatureProperty
            ,owners: User
            ,goAnnotations: GoAnnotation
            ,geneProducts: GeneProduct
            ,provenances: Provenance
    ]

    static mapping = {
            featureSynonyms cascade: 'all-delete-orphan'
            childFeatureRelationships cascade: 'all-delete-orphan'
            parentFeatureRelationships cascade: 'all-delete-orphan'
            goAnnotations cascade: 'all-delete-orphan'
            geneProducts cascade: 'all-delete-orphan'
            provenances cascade: 'all-delete-orphan'
            name type: 'text'
            description type: 'text'
//            featureLocation fetch:"eager", lazy:true
    }


    static belongsTo = [
            User
    ]

    User getOwner(){
        if(owners?.size()>0){
            return owners.iterator().next()
        }
        return null
    }


    boolean equals(Object other) {
        if (this.is(other)) return true
        if (getClass() != other.class) return false
        Feature castOther = ( Feature ) other;

        return  (this?.ontologyId==castOther?.ontologyId) \
                   &&  (this?.getUniqueName()==castOther?.getUniqueName())
    }

    int hashCode() {
        int result = 17;
        result = 37 * result + ( ontologyId == null ? 0 : this.ontologyId.hashCode() );
        result = 37 * result + ( getUniqueName() == null ? 0 : this.getUniqueName().hashCode() );
        return result;
    }

    Feature generateClone() {
        Feature cloned = this.getClass().newInstance()
        cloned.name = this.name
        cloned.uniqueName = this.uniqueName
        cloned.dateCreated = this.dateCreated
        cloned.lastUpdated = this.lastUpdated
        cloned.featureLocation = this.featureLocation
        cloned.parentFeatureRelationships = this.parentFeatureRelationships
        cloned.childFeatureRelationships = this.childFeatureRelationships
        cloned.featureSynonyms = this.featureSynonyms
        cloned.featureDBXrefs = this.featureDBXrefs
        cloned.featureProperties = this.featureProperties
        return cloned
    }



    /** Convenience method for retrieving the location.  Assumes that it only contains a single
     *  location so it returns the first (and hopefully only) location from the collection of
     *  locations.  Returns <code>null</code> if none are found.
     *
     * @return FeatureLocation of this object
     */
//    FeatureLocation getFeatureLocation() {
//        Collection<FeatureLocation> locs = getFeatureLocations();
//        return locs ? locs.first() : null
//    }


    /** Get the length of this feature.
     *
     * @return Length of feature
     */
    int getLength() {
        return featureLocation.calculateLength()
    }

    Integer getFmin(){
        featureLocation.fmin
    }

    Integer getFmax(){
        featureLocation.fmax
    }

    Integer getStrand(){
        featureLocation.strand
    }


    @Override
    String toString() {
        return "Feature{" +
                "id=" + id +
                ", symbol='" + symbol + '\'' +
                ", description='" + description + '\'' +
                ", name='" + name + '\'' +
                ", uniqueName='" + uniqueName + '\'' +
//                ", status=" + status +
                ", dateCreated=" + dateCreated +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
