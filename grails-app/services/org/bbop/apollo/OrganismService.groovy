package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.Transactional
import groovy.io.FileType
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.history.FeatureEvent
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.TranslationTable

@Transactional
class OrganismService {

    def featureService
    def configWrapperService

    int MAX_DELETE_SIZE = 10000
    int TRANSACTION_SIZE = 30

    /**
     * If file path contains "searchDatabaseData"
     * @param path
     * @return
     */
    @NotTransactional
    String findBlatDB(String path){
        String searchDatabaseDirectory = path + "/" + FeatureStringEnum.SEARCH_DATABASE_DATA.value
        File searchFile = new File(searchDatabaseDirectory)
        if(searchFile.exists()){
            String returnFile = null
            searchFile.eachFileRecurse(FileType.FILES) {
                if(it.name.endsWith(".2bit")){
                    returnFile = it
                }
            }
            return returnFile
        }

        return null

    }

    @NotTransactional
    def deleteAllFeaturesForSequences(List<Sequence> sequences) {

        int totalDeleted = 0
        def featureCount = Feature.executeQuery("select count(f) from Feature f join f.featureLocations fl join fl.sequence s where s in (:sequenceList)", [sequenceList: sequences])[0]
        println "features to delete ${featureCount}"
        while(featureCount>0){
            def featurePairs = Feature.executeQuery("select f.id,f.uniqueName from Feature f join f.featureLocations fl join fl.sequence s where s in (:sequenceList)", [max:MAX_DELETE_SIZE,sequenceList: sequences])
            // maximum transaction size  30
            log.debug "feature sublists created ${featurePairs.size()}"
            def featureSubLists = featurePairs.collate(TRANSACTION_SIZE)
            if (!featureSubLists) {
                log.warn("Nothing to delete")
                return
            }
            log.debug "sublists size ${featureSubLists.size()}"
            int count = 0
            long startTime = System.currentTimeMillis()
            long endTime
            double totalTime
            featureSubLists.each { featureList ->
                if (featureList) {
                    def ids = featureList.collect() {
                        it[0]
                    }
                    log.info"ids ${ids.size()}"
                    def uniqueNames = featureList.collect() {
                        it[1]
                    }
                    log.debug "uniqueNames ${uniqueNames.size()}"
                    Feature.withNewTransaction{
                        def features = Feature.findAllByIdInList(ids)
                        features.each { f ->
                            f.delete()
                        }
                        def featureEvents = FeatureEvent.findAllByUniqueNameInList(uniqueNames)
                        featureEvents.each { fe ->
                            fe.delete()
                        }
                        count += featureList.size()
                        log.info "${count} / ${featurePairs.size()}  =  ${100 * count / featurePairs.size()}% "
                    }
                    log.info "deleted ${featurePairs.size()}"
                }
                endTime = System.currentTimeMillis()
                totalTime = (endTime - startTime) / 1000.0f
                startTime = System.currentTimeMillis()
                double rate = featureList.size() / totalTime
                log.info "Deleted ${rate} features / sec"
            }
            totalDeleted += featurePairs.size()

            featureCount = Feature.executeQuery("select count(f) from Feature f join f.featureLocations fl join fl.sequence s where s in (:sequenceList)", [sequenceList: sequences])[0]
            println "features remaining to delete ${featureCount} vs deleted ${totalDeleted}"
        }
        return totalDeleted

    }

    @NotTransactional
    def deleteAllFeaturesForOrganism(Organism organism) {

        int totalDeleted = 0
        println "organism ${organism}"
//        def featureCount = Feature.executeQuery("select count(f) from Feature f join f.featureLocations fl join fl.sequence s join s.organism o where o=:organism", [organism: organism])[0]
        def featureCount = Feature.executeQuery("MATCH (f:Feature)--(s:Sequence)--(o:Organism) where (o.commonName = ${organism.commonName} or o.id = ${organism.id}) return count(f) ")[0]
        println "features to delete ${featureCount}"
//        while(featureCount>0){
//            def featurePairs = Feature.executeQuery("select f.id,f.uniqueName from Feature f join f.featureLocations fl join fl.sequence s join s.organism o where o=:organism", [max:MAX_DELETE_SIZE,organism: organism])
            def featurePairs = Feature.executeQuery("MATCH (f:Feature)--(s:Sequence)--(o:Organism) where (o.commonName = ${organism.commonName} or o.id = ${organism.id}) return f.id,f.uniqueName ")
            println "features pairs ${featurePairs}"
            // use the same to delete organisms, as well
            def query = "MATCH (f:Feature)-[r]-(s:Sequence)--(o:Organism), (f)-[owners:OWNERS]-(),(f)-[fr]-(fg:Feature)-[other]-() where (o.commonName = ${organism.commonName} or o.id = ${organism.id})  delete owners,fr,f,fg,r,other return count(f)"
//        def query = "MATCH (f:Feature)-[r]-(s:Sequence)--(o:Organism), (f)-[owners:OWNERS]-(),(f)-[fr]-(fg:Feature)-[other]-() where (o.commonName = ${organism.commonName} or o.id = ${organism.id})   return count(f) "
        println "deletion query"
            println query
            def deletionResults = Feature.executeUpdate(query,[flush: true,failOnError: true])
            println "deletion results ${deletionResults}"



            // maximum transaction size  30
        if(featurePairs){
            log.debug "feature sublists created ${featurePairs.size()}"
            def featureSubLists = featurePairs.collate(TRANSACTION_SIZE)
            if (!featureSubLists) {
                log.warn("Nothing to delete for ${organism?.commonName}")
                return
            }
            log.debug "sublists size ${featureSubLists.size()}"
            int count = 0
            long startTime = System.currentTimeMillis()
            long endTime
            double totalTime
            featureSubLists.each { featureList ->

                println "input feature list: ${featureList}"
                def featureEventQuery = FeatureEvent.executeUpdate("MATCH (n:FeatureEvent)-[editor:EDITOR]-(u:User) where n.uniqueName in ${featureList} delete n,editor")
                println "output query ${featureEventQuery}"
//                if (featureList) {
//                    def ids = featureList.collect() {
//                        it[0]
//                    }
//                    log.info"ids ${ids.size()}"
//                    def uniqueNames = featureList.collect() {
//                        it[1]
//                    }
//                    log.debug "uniqueNames ${uniqueNames.size()}"
//                    Feature.withNewTransaction{
//                        def features = Feature.findAllByIdInList(ids)
//                        features.each { f ->
//                            f.delete()
//                        }
//                        def featureEvents = FeatureEvent.findAllByUniqueNameInList(uniqueNames)
//                        featureEvents.each { fe ->
//                            fe.delete()
//                        }
//                        organism.save(flush: true)
//                        count += featureList.size()
//                        log.info "${count} / ${featurePairs.size()}  =  ${100 * count / featurePairs.size()}% "
//                    }
//                    log.info "deleted ${featurePairs.size()}"
//                }
                endTime = System.currentTimeMillis()
                totalTime = (endTime - startTime) / 1000.0f
                startTime = System.currentTimeMillis()
                double rate = featureList.size() / totalTime
                log.info "Deleted ${rate} features / sec"

        }
            }
            totalDeleted += featurePairs.size()
        organism.save(flush:true)

//            featureCount = Feature.executeQuery("select count(f) from Feature f join f.featureLocations fl join fl.sequence s join s.organism o where o=:organism", [organism: organism])[0]
            featureCount = Feature.executeQuery("MATCH (f:Feature)--(s:Sequence)--(o:Organism) where (o.commonName = ${organism.commonName} or o.id = ${organism.id}) return count(f) ")[0]
            println "features remaining to delete ${featureCount} vs deleted ${totalDeleted}"
//        }
        return deletionResults

    }


    TranslationTable getTranslationTable(Organism organism) {
        if(organism?.nonDefaultTranslationTable){
            println "overriding ${organism} default translation table for ${organism.commonName} with ${organism.nonDefaultTranslationTable}"
            return SequenceTranslationHandler.getTranslationTableForGeneticCode(organism.nonDefaultTranslationTable)
        }
        // just use the default
        else{
            log.debug "using the default translation table"
            return  configWrapperService.getTranslationTable()
        }

    }
}
