package org.bbop.apollo

import grails.gorm.transactions.Transactional
import org.bbop.apollo.feature.CDS
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Gene
import org.bbop.apollo.feature.NonCanonicalFivePrimeSpliceSite
import org.bbop.apollo.feature.NonCanonicalThreePrimeSpliceSite
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.variant.Allele
import org.bbop.apollo.variant.SequenceAlteration

@Transactional(readOnly = true)
class NameService {

    def transcriptService
    def letterPaddingStrategy = new LetterPaddingStrategy()
    def leftPaddingStrategy = new LeftPaddingStrategy()


    // TODO: replace with more reasonable naming schemas
    String generateUniqueName() {
        UUID.randomUUID().toString()
    }

    String generateUniqueName(Feature thisFeature, String principalName = null ) {
//        Organism organism = thisFeature?.featureLocation?.to?.organism
        Organism organism = Organism.executeQuery("MATCH (o:Organism)--(s)--(f:Feature) where f.uniqueName =${thisFeature.uniqueName} return o")?.first() as Organism
        log.debug "found organism ${organism} for feature unique name ${thisFeature}"
        if(thisFeature.name) {
            if (thisFeature.instanceOf(Transcript.class)) {
                if(!principalName){
                    Gene gene = transcriptService.getGene((Transcript) thisFeature)
                    if(!gene){
                        gene = transcriptService.getPseudogene((Transcript) thisFeature)
                    }

                    log.debug "feature properties : ${thisFeature}"
                    if(!gene){
                        principalName = thisFeature.uniqueName
                    }
                    else
                    if(!gene.name){
                        principalName = thisFeature.uniqueName
                    }
                    else{
                        principalName = gene.name
                    }
                }
                return makeUniqueTranscriptName(organism,principalName.trim()+"-")
            } else
            if (thisFeature.instanceOf(Gene.class)) {
                if(!principalName){
                    principalName = ((Gene) thisFeature).name
                }
                if(Gene.countByName(principalName.trim())==0){
                    return principalName
                }
                  return makeUniqueGeneName(organism,principalName.trim())
            }
            if (thisFeature.instanceOf(Exon.class) || thisFeature.instanceOf(NonCanonicalFivePrimeSpliceSite) || thisFeature.instanceOf(NonCanonicalThreePrimeSpliceSite) || thisFeature.instanceOf(CDS.class)) {
                return generateUniqueName()
            }
            else{
                if(!principalName){
                    principalName = thisFeature.name
                }
                if(organism) {
                    return makeUniqueFeatureName(organism, principalName.trim(), new LetterPaddingStrategy())
                }
            }
        }
        else{
            return generateUniqueName()
        }
    }


    // TODO: add proper criteria query here
    boolean isUniqueGene(Organism organism,String name){
//        Integer numberResults = Gene.findAllByName(name).findAll(){
//            it.featureLocation.to.organism == organism
//        }.size()
//        return 0 == numberResults
        return true
    }

    boolean isUnique(Organism organism,String name){
//        if(Feature.countByName(name)==0) {
//            return true
//        }
//        List results = (Feature.executeQuery("select count(f) from Feature f join f.featureLocations fl join fl.sequence s where s.organism = :org and f.name = :name ",[org:organism,name:name]))
//        Integer numberResults = Feature.findAllByName(name).findAll(){
//            it.featureLocation.to.organism == organism
//        }.size()
        Integer numberResults = Feature.executeQuery("MATCH (o:Organism)--(s)--(f:Feature) where f.name =${name} and (o.id = ${organism.id} OR o.commonName = ${organism.commonName}) return count(f)").first() as Integer
        return 0 == numberResults
    }

    String makeUniqueTranscriptName(Organism organism,String principalName){
        String name
        name = principalName + leftPaddingStrategy.pad(0)
//        def queryResult =  Transcript.executeQuery("MATCH (t:Transcript) where t.name='${name}' RETURN count(t)").first()
        int tCount = Transcript.executeQuery("MATCH (t:Transcript) where t.name='${name}' RETURN count(t)").first()
        if(tCount==0){
            log.debug "returning because tCount is 0"
            return name
        }

//        List results = (Feature.executeQuery("select f.name from Transcript f join f.featureLocations fl join fl.sequence s where s.organism = :org and f.name like :name ",[org:organism,name:principalName+'%']))
        // See https://github.com/GMOD/Apollo/issues/1276
        // only does sort over found results
        List<String> results= Feature.findAllByNameLike(principalName+"%").findAll(){
            it.featureLocation?.to?.organism == organism
        }.name

        name = principalName + leftPaddingStrategy.pad(results.size())
        int count = results.size()
        while(results.contains(name)){
            name = principalName + leftPaddingStrategy.pad(count)
            ++count
        }
        return name
    }

    String makeUniqueGeneName(Organism organism,String principalName,boolean useOriginal=false){

        if(useOriginal && isUniqueGene(organism,principalName)){
            return principalName
        }

        if(isUniqueGene(organism,principalName)){
            return principalName
        }

        String name = principalName + letterPaddingStrategy.pad(0)

        List<String> results= Gene.findAllByNameLike(principalName+"%").findAll(){
            it.featureLocation.to.organism == organism
        }.name
        int count = results.size()
        while(results.contains(name)){
            name = principalName + letterPaddingStrategy.pad(count)
            ++count
        }
        return name
    }

    String makeUniqueFeatureName(Organism organism,String principalName,PaddingStrategy paddingStrategy,boolean useOriginal=false){
        String name
        int i = 0

        if(useOriginal && isUnique(organism,principalName)){
            return principalName
        }

        if(isUnique(organism,principalName)){
            return principalName
        }

        name = principalName + paddingStrategy.pad(i++)
        while(!isUnique(organism,name)){
            name = principalName + paddingStrategy.pad(i++)
        }
        return name
    }

    /**
     * Generates name for a given variant based on its properties
     * @param variant
     * @return
     */
    String makeUniqueVariantName(SequenceAlteration variant) {
        String name
        String position = variant.featureLocation.fmin + 1
        def alternateAlleles = []
        String referenceAllele
        for (Allele allele : variant.alleles.sort { a, b -> a.id <=> b.id }) {
            if (allele.reference) {
                referenceAllele = allele.bases
            }
            else {
                alternateAlleles.add(allele.bases)
            }
        }
        name = position + " " + referenceAllele + " > " + alternateAlleles.join(",")
        log.info "Name for variant: ${name}"
        return name
    }

}
