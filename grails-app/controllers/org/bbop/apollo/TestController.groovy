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


    def test1() {

        println "output"
        new Feature(
                name: "bob",
                uniqueName: UUID.randomUUID().toString()
        ).save()
        new Feature(
                name: "bob",
                uniqueName: UUID.randomUUID().toString()
        ).save()
        def bobList = Feature.findAllByName("bob")
        println bobList.size()
        def returnFeatures = Feature.executeQuery(" MATCH (f:Feature) where f.name = 'bob' return f ")
        println returnFeatures.size()

        // 6e4527cb-e850-4b23-b139-ee1f16ec5ee7
//        Feature.withNewSession {
////            session.flush()
//
//        }
//        Feature lastFeature = bobList.last()
//        println "last uqniue name ${lastFeature.uniqueName}"
//        lastFeature.name = 'jill'
//        lastFeature.save()
//        lastFeature.save(flush: true)
//        Feature firstFeature = bobList.first() as Feature
//        println "uqniue name ${firstFeature.uniqueName}"
//        def update = Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = '${bobList.first().uniqueName}' set f.name = 'bill' return f")
        String statement = "MATCH (f:Feature) where f.uniqueName = '${bobList.first().uniqueName}' set f.name = 'bill' return f"
        println statement
        def update = boltDriver.session().run(statement)
        println "update: ${update.size()}"
//        currentSe.flush()

        println "post update: "

        bobList = Feature.findAllByName("bob")
        println bobList.size()
        returnFeatures = Feature.executeQuery(" MATCH (f:Feature) where f.name = 'bob' return f ")
        println returnFeatures.size()

        Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = ${bobList.last().uniqueName} set f.name = 'jill' return f")


        println "found a bill"

        println Feature.countByName("bill")
        returnFeatures = Feature.executeQuery(" MATCH (f:Feature) where f.name = 'bill' return f ")
        println returnFeatures.size()

        println "found a jill"

        println Feature.countByName("jill")
        returnFeatures = Feature.executeQuery(" MATCH (f:Feature) where f.name = 'jill' return f ")
        println returnFeatures.size()

        println "found a bob"

        println Feature.countByName("bob")
        returnFeatures = Feature.executeQuery(" MATCH (f:Feature) where f.name = 'bob' return f ")
        println returnFeatures.size()

        def deletedResults = Feature.executeUpdate(" MATCH (f:Feature) where f.name in ['bill','bob','jill'] delete f")
        println "deleted results: ${deletedResults}"

        def deletedList = Feature.findAllByNameInList(["bob","bill","jill"])
        println "post delete list ${bobList.size()} -> ${deletedList}"

        render new JSONObject() as JSON
    }

    def test2(){

        testService.deleteUsers(['bill','bob'])
        testService.addUser('bob').save(flush: true)
        println "user count 1:  ${testService.getUserCount1(['bob'])}"
        println "user count 2:  ${testService.getUserCount2(['bob'])}"
//        testService.renameUser1('bob','bill')
//        testService.renameUser2('bob','bill')
        println "user count 1 bob: ${testService.getUserCount1(['bob'])}"
        println "user count 1 bill: ${testService.getUserCount1(['bill'])}"
        println "user count 2 bob: ${testService.getUserCount2(['bob'])}"
        println "user count 2 bill: ${testService.getUserCount2(['bill'])}"

//        println "user count 1: " ${testService.getUserCount1(['bob'])}"
//        testService.renameUser2('bob','bill').save(flush: true)


        render new JSONObject()
    }

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
        println "results"
        println results
        println "return features"
        println returnFeatures
        render returnFeatures as JSON
    }

    def createFeature(String name){
        render new Feature(name: name, uniqueName: UUID.randomUUID().toString()).save() as JSON
    }

    def renameFeature1(String uniqueName, String newName){
        println "A.1"
        Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = ${uniqueName} set f.name = ${newName} return f")
        println "B.1"
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
        println Feature.countByNameInList(['bill','bob','jill'])
        Feature.executeUpdate(" MATCH (f:Feature) where f.name in ['bill','bob','jill'] delete f return f")
        println Feature.countByNameInList(['bill','bob','jill'])
        String uniqueName = UUID.randomUUID().toString()
        def feature = new Feature(name: "bob", uniqueName: uniqueName).save(flush: true,failOnError: true)
        println "feature output: ${feature.uniqueName}"


        int updated = Feature.executeUpdate("MATCH (f:Feature) where f.uniqueName = ${uniqueName} set f.name = 'jill' return f")
        println "updated: ${updated}"

//        testService.renameUser1(uniqueName ,"jill")


        getTestFeatures1()
//        getTestFeatures2()
        println Feature.countByNameInList(['bill','bob','jill'])
//        render new JSONObject() as JSON
    }
}
