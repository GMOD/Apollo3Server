package org.bbop.apollo

import grails.gorm.transactions.Transactional
import org.bbop.apollo.attributes.FeatureProperty
import org.bbop.apollo.attributes.Frameshift
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.relationship.FeatureRelationship

@Transactional(readOnly = true)
class FeatureRelationshipService {

    List<Feature> getChildrenForFeatureAndTypes(Feature feature, String... ontologyIds) {
        def list = new ArrayList<Feature>()
        if (feature?.parentFeatureRelationships != null) {
            feature.parentFeatureRelationships.each { it ->
                if (ontologyIds.size() == 0 || (it && ontologyIds.contains(it.to.ontologyId))) {
                    list.push(it.to)
                }
            }
        }

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
        def list = new ArrayList<Feature>()
        if (feature?.childFeatureRelationships != null) {
            feature.childFeatureRelationships.each { it ->
                if (ontologyIds.size() == 0 || (it && ontologyIds.contains(it.from.ontologyId))) {
                    list.push(it.from)
                }
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
        def exonRelations = feature.parentFeatureRelationships.findAll()
        return exonRelations.collect { it ->
            it.to
        }
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

        if(feature.instanceOf(Transcript.class)){
            int updated = Feature.executeUpdate("MATCH (t:Transcript)-[prop]-(),(t)-[fr:FEATURERELATIONSHIP]->(child:Feature)" +
                ",(child)-[fl:FEATURELOCATION]->(s:Sequence),(t)<-[parentrel:FEATURERELATIONSHIP]-(g:Gene)  " +
                " where t.uniqueName = '${feature.uniqueName}' delete t,prop, fr,child,fl,parentrel return t")
            // TODO: expand to more feature properties that are not feature feature locatin of relationships
            println "deleted transcript and children ${updated} and removed gene relationship"
        }
        else
        if(feature.instanceOf(Gene.class)){
            int updated = Feature.executeUpdate("MATCH (g:Gene)-[r:FEATURERELATIONSHIP]->(t:Transcript)-[fr:FEATURERELATIONSHIP]->(child:Feature)-[fl:FEATURELOCATION]->(s:Sequence)," +
                " (g)-[l:FEATURELOCATION]->(s),(t)-[tl:FEATURELOCATION]->(s)  " +
                " where g.uniqueName = '${feature.uniqueName}' delete g,r,t,fr,child,fl,l,tl return g ")
            // TODO: expand to more feature properties that are not feature feature locatin of relationships
            println "deleted gene and children ${updated}"
        }
        else{
            int updated = Feature.executeUpdate("MATCH (f:Feature)-[fl:FEATURELOCATION]->(s:Sequence) " +
                " where f.uniqueName = '${feature.uniqueName}' delete f,fl return f ")
            // it is a first-level feature, so just delete that
            // TODO: expand to more feature properties that are not feature feature locatin of relationships
            println "deleted single level feature ${updated}"


        }

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
