package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.bbop.apollo.feature.Feature
import org.grails.web.json.JSONObject

/**
 * @Deprecated
 * This is simply a test class
 * TODO: Delete
 */
@Transactional
class TestController {

    def testService

    def getFeatures(String name){
        render Feature.findAllByName(name) as JSON
    }

    def getTestFeatures1(){
        render Feature.findAllByNameInList(['bill','bob','jill']) as JSON
    }

    def getTestFeatures2(){

        // note that matching ONLY feature is problematic as it fails to add feature location as it tries to initiate a proxy for feature location
        def results = Feature.executeQuery(" MATCH (f:Feature)-[fl:FeatureLocation]-(s:Sequence) where f.name in ['bill','bob','jill'] return f,fl,s")
        List<Feature> returnFeatures = []
        for(def r in results){
            returnFeatures.add(r[0] as Feature)
        }
        log.debug "results"
        log.debug results
        log.debug "return features"
        log.debug returnFeatures
        render returnFeatures as JSON
    }

    def createFeature(String name){
        render new Feature(name: name, uniqueName: UUID.randomUUID().toString()).save() as JSON
    }

    def renameFeature1(String uniqueName, String newName){
        log.debug "A.1"
        Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = ${uniqueName} set f.name = ${newName} return f")
        log.debug "B.1"
//        def feature = Feature.findByUniqueName(uniqueName)
//        feature.name = newName
//        feature.save(flush:true)
//        Feature.executeUpdate("")
//        render feature as JSON
        render new JSONObject() as JSON
    }

    def deleteTestFeatures(){
        def deletedResults = Feature.executeUpdate(" MATCH (f:Feature) where f.name in ['bill','bob','jill'] delete f return f")
        render deletedResults
    }

    def doAllParts1(){
        log.debug Feature.countByNameInList(['bill','bob','jill'])
        Feature.executeUpdate(" MATCH (f:Feature) where f.name in ['bill','bob','jill'] delete f return f")
        log.debug Feature.countByNameInList(['bill','bob','jill'])
        String uniqueName = UUID.randomUUID().toString()
        def feature = new Feature(name: "bob", uniqueName: uniqueName).save(flush: true,failOnError: true)
        log.debug "feature output: ${feature.uniqueName}"



        int updated = Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = ${uniqueName} set f.name = 'jill' return f")
        log.info "updated: ${updated}"
        def featuresInList = Feature.findAllByNameInList(['bill','bob','jill']) as JSON

        log.info featuresInList.toString()
        render featuresInList

//        testService.renameUser1(uniqueName ,"jill")


//        getTestFeatures1()
//        getTestFeatures2()
        log.info Feature.countByNameInList(['bill','bob','jill'])
//        render new JSONObject() as JSON
    }
}
