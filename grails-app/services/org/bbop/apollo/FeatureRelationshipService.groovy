package org.bbop.apollo

import grails.gorm.transactions.Transactional
import org.bbop.apollo.attributes.DBXref
import org.bbop.apollo.attributes.FeatureProperty
import org.bbop.apollo.attributes.FeatureSynonym
import org.bbop.apollo.attributes.Frameshift
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.go.GoAnnotation
import org.bbop.apollo.relationship.FeatureRelationship
import org.neo4j.driver.internal.InternalNode

@Transactional(readOnly = true)
class FeatureRelationshipService {

    List<Feature> getChildrenForFeatureAndTypes(Feature feature, String... ontologyIds) {
        def children = Feature.executeQuery("MATCH (f:Feature)-[fr:FEATURERELATIONSHIP]->(child:Feature) where f.uniqueName = ${feature.uniqueName} return child ")
        log.debug "getting children ${children}"
        def list = new ArrayList<Feature>()
        def ontologyIdCollection = ontologyIds as Collection<String>
        children?.each { InternalNode it ->
            Collection<String> labels = FeatureTypeMapper.getSOUrlForCvTermLabels(it.labels())
            log.debug "labels ${labels} vs ${ontologyIdCollection}"
            log.debug "ontology type: ${it.ontologyId}"
            if (ontologyIds.size() == 0 || (ontologyIdCollection.intersect(labels))) {
                def castFeature = FeatureTypeMapper.castNeo4jFeature(it)
                list.push(castFeature)
            }
        }
        log.debug "return list ${list}"

        return list
    }


    Feature getChildForFeature(Feature feature, String ontologyId) {
        List<Feature> featureList = getChildrenForFeatureAndTypes(feature, ontologyId)

        if (featureList.size() == 0) {
            return null
        }

        if (featureList.size() > 1) {
            log.error "More than one child feature relationships found for ${feature} and ID ${ontologyId}"
            return null
        }

        return featureList.get(0)
    }

    Feature getParentForFeature(Feature feature, String... ontologyId) {

        List<Feature> featureList = getParentsForFeature(feature, ontologyId)

        if (featureList.size() == 0) {
            return null
        }

        if (featureList.size() > 1) {
            log.error "More than one feature relationships parent found for ${feature} and ID ${ontologyId}"
            return null
        }

        return featureList.get(0)
    }

    List<Feature> getParentsForFeature(Feature feature, String... ontologyIds) {
        def parents = Feature.executeQuery("MATCH (parent:Feature)-[fr:FEATURERELATIONSHIP]->(f:Feature) where f.uniqueName = ${feature.uniqueName} return parent")
        def list = new ArrayList<Feature>()
        def ontologyIdCollection = ontologyIds as Collection<String>
        parents?.each { InternalNode it ->
            log.debug "raw labels: "+it.labels()
            Collection<String> labels = FeatureTypeMapper.getSOUrlForCvTermLabels(it.labels())
            if (ontologyIds.size() == 0 || (ontologyIdCollection.intersect(labels))) {
                log.debug "ontology type: ${it.ontologyId}"
                def castFeature = FeatureTypeMapper.castNeo4jFeature(it)
                list.push(castFeature)
            }
        }

        return list
    }

    @Transactional
    def deleteRelationships(Feature feature, String parentOntologyId, String childOntologyId) {
        deleteChildrenForTypes(feature, childOntologyId)
        deleteParentForTypes(feature, parentOntologyId)
    }

    @Transactional
    def setChildForType(Feature parentFeature, Feature childFeature) {
        List<FeatureRelationship> results = FeatureRelationship.findAllByFrom(parentFeature).findAll() {
            it.to.ontologyId == childFeature.ontologyId
        }


        if (results.size() == 1) {
            results.get(0).to = childFeature
            return true
        } else {
            if (results.size() == 0) {
                log.info "No feature relationships exist for parent ${parentFeature} and child ${childFeature}"
            }
            if (results.size() > 1) {
                log.warn "${results.size()} feature relationships exist for parent ${parentFeature} and child ${childFeature}"
            }
            return false
        }

    }

    @Transactional
    def deleteChildrenForTypes(Feature feature, String... ontologyIds) {
        def criteria = FeatureRelationship.createCriteria()

        def featureRelationships = criteria {
            eq("from", feature)
        }.findAll() {
            ontologyIds.length == 0 || it.to.ontologyId in ontologyIds
        }

        int numRelationships = featureRelationships.size()
        for (int i = 0; i < numRelationships; i++) {
            FeatureRelationship featureRelationship = featureRelationships.get(i)
            removeFeatureRelationship(featureRelationship.from, featureRelationship.to)
        }
    }

    @Transactional
    def deleteParentForTypes(Feature feature, String... ontologyIds) {
        // delete transcript -> non canonical 3' splice site child relationship
        def criteria = FeatureRelationship.createCriteria()

        criteria {
            eq("to", feature)
        }.findAll() {
            it.from.ontologyId in ontologyIds
        }.each {
            feature.removeFromChildFeatureRelationships(it)
        }

    }

    // based on Transcript.setCDS
    @Transactional
    def addChildFeature(Feature parent, Feature child, boolean replace = true) {

        // replace if of the same type
        if (replace) {
            boolean found = false
            def criteria = FeatureRelationship.createCriteria()
            criteria {
                eq("from", parent)
            }
            .findAll() {
                it.to.ontologyId == child.ontologyId
            }
            .each {
                found = true
                it.to= child
                it.save()
                return
            }

            if (found) {
                return
            }

        }



        FeatureRelationship fr = new FeatureRelationship(
                from: parent
                , to: child
        ).save(flush: true);
        parent.addToParentFeatureRelationships(fr)
        child.addToChildFeatureRelationships(fr)
        child.save(flush: true)
        parent.save(flush: true)
    }

    @Transactional
    void removeFeatureRelationship(Feature parentFeature, Feature childFeature) {

        FeatureRelationship featureRelationship = FeatureRelationship.findByParentFeatureAndChildFeature(parentFeature, childFeature)
        if (featureRelationship) {
            parentFeature.parentFeatureRelationships?.remove(featureRelationship)
            childFeature.childFeatureRelationships?.remove(featureRelationship)
            parentFeature.save(flush: true)
            childFeature.save(flush: true)
        }
    }

    // TODO: re-enable ?
//    List<Frameshift> getFeaturePropertyForTypes(Transcript transcript, List<String> strings) {
//        return (List<Frameshift>) FeatureProperty.findAllByFeaturesInListAndOntologyIdsInList([transcript], strings)
//    }

    List<Feature> getChildren(Feature feature) {
        return getChildrenForFeatureAndTypes(feature)
    }

    /**
     * Iterate to all of the children, and delete the child and thereby the feature relationship automatically.
     *
     *
     * @param feature
     * @return
     */
    @Transactional
    def deleteFeatureAndChildren(Feature feature) {

        // delete all relationships and delete all children that are features

        def children = getChildren(feature)
        children.each {
            deleteFeatureAndChildren(it)
        }

        //
//        FeatureSynonym.executeUpdate("MATCH (f:Feature)-[r]->(p:FeatureSynonym) where f.uniqueName = ${feature.uniqueName} " +
//                " delete r,p")
//        DBXref.executeUpdate("MATCH (f:Feature)-[r]->(p:DBXref) where f.uniqueName = ${feature.uniqueName} " +
//                " delete r,p")
//        FeatureProperty.executeUpdate("MATCH (f:Feature)-[r]->(p:FeatureProperty) where f.uniqueName = ${feature.uniqueName} " +
//                " delete r,p")
//        GoAnnotation.executeUpdate("MATCH (f:Feature)-[r]->(p:GoAnnotation) where f.uniqueName = ${feature.uniqueName} " +
//                " delete r,p")
//        GoAnnotation.executeUpdate("MATCH (f:Feature)-[r]->(p:GoAnnotation) where f.uniqueName = ${feature.uniqueName} " +
//                " delete r,p")
//        Feature.executeUpdate("MATCH (f:Feature)-[r]->(child:Feature) delete r,child")

        Feature.deleteAll(feature)
//        if(feature.instanceOf(Transcript.class)){
//
//        }
//        else{
//
//        }
//
//        // if grandchildren then delete those
//        for (FeatureRelationship featureRelationship in feature.parentFeatureRelationships) {
//            if (featureRelationship.childFeature?.parentFeatureRelationships) {
//                deleteFeatureAndChildren(featureRelationship.childFeature)
//            }
//        }
//
//        // create a list of relationships to remove (assume we have no grandchildren here)
//        List<FeatureRelationship> relationshipsToRemove = []
//        for (FeatureRelationship featureRelationship in feature.parentFeatureRelationships) {
//            relationshipsToRemove << featureRelationship
//        }
//
//        // actually delete those
//        relationshipsToRemove.each {
//            it.to.delete()
//            feature.removeFromParentFeatureRelationships(it)
//            it.delete()
//        }
//
//        // last, delete self or save updated relationships
//        if (!feature.parentFeatureRelationships && !feature.childFeatureRelationships) {
//            feature.delete(flush: true)
//        } else {
//            feature.save(flush: true)
//        }

    }
}
