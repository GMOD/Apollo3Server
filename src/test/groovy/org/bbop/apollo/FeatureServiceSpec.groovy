package org.bbop.apollo

import grails.converters.JSON
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.StandardTranslationTable
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.sequence.TranslationTable
import org.grails.web.json.JSONObject
import spock.lang.Specification
import spock.lang.Ignore

/**
 */
class FeatureServiceSpec extends Specification implements ServiceUnitTest<FeatureService>, DataTest {

    def setup() {
        mockDomain Sequence
        mockDomain Feature
        mockDomain MRNA
    }

    def cleanup() {
    }

    void "convert JSON to Feature Location"() {

        when: "We have a valid json object"
        JSONObject jsonObject = new JSONObject()
        Sequence sequence = new Sequence(
            name: "Chr3",
            seqChunkSize: 20,
            start: 1,
            end: 100,
            length: 99,
            uniqueName: UUID.randomUUID().toString()
        ).save(failOnError: true)
        jsonObject.put(FeatureStringEnum.FMIN.value, 73)
        jsonObject.put(FeatureStringEnum.FMAX.value, 113)
        jsonObject.put(FeatureStringEnum.STRAND.value, Strand.POSITIVE.value)


        then: "We should return a valid FeatureLocation"
        FeatureLocation featureLocation = service.convertJSONToFeatureLocation(jsonObject, sequence,null)
        assert featureLocation.to.name == "Chr3"
        assert featureLocation.fmin == 73
        assert featureLocation.fmax == 113
        assert featureLocation.strand == Strand.POSITIVE.value


    }

    void "convert JSON to Ontology ID"() {
        when: "We hav a json object of type"
        JSONObject json = JSON.parse("{name:exon, cv:{name:sequence}}")

        then: "We should be able to infer the ontology ID"
        String ontologyId = FeatureTypeMapper.convertJSONToOntologyId(json)
        assert ontologyId != null
        assert ontologyId == Exon.ontologyId
    }

    def "find the longest protein sequence"(){
        given: "an mrna sequence"
        String mrna = "GCTTTTCAAATATGCCAGTGAGAGCATATCGATTCTAGTGTAAGAAAGTTAACCAACGACTTCACACGAACGGTTTGTGAGTTACGTTTTCTTCGTTTAAAATAATCTGTTACAAATAATAGATAATGCTTTTCAAATATGCCAGTGAGAGCATATCGATTCTAGTGTAAGAAAGTTAACCAACGACTTCACACGAACGGTTTGTGAGTTACGTTTTCTTCGTTTAAAATAATCTGTTACAAATAATAGATAATATGGAATCTGCTATTGTTCATCTTGAACAAAGCGTGCAAAAGGCTGATGGAAAACTAGACATGATTGCATGGCAAATTGATGCTTTTGAAAAAGAATTTGAAGATCCTGGTAGTGAGATTTCTGTGCTTCGTCTATTACGGTCTGTTCATCAAGTCACAAAAGATTATCAGAACCTTCGGCAAGAAATATTGGAGGTTCAACAATTGCAAAAGCAACTTTCAGATTCCCTTAAAGCACAATTATCTCAAGTGCATGGACATTTTAACTTATTACGCAATAAAATAGTAGGACAAAATAAAAATCTACAATTAAAATAAGATTAAAATTTTTTATTTATATTTAAAGTATAATTTAAATATATTTTTTAAATTATACTTAATTTATAATTTTTTATTATAAAATTATTATTAATATTTTAAATCAAAAGTTATTAAAATAACAGATTAAAATTTTTTATTTATATTTAAAGTATAATTTAAATATATTTTTTAAATTATACTTAATTTATAATTTTTTATTATAAAATTATTATTAATATTTTAAATCAAAAGTTATTAAAATAACA"
        TranslationTable translationTable = new StandardTranslationTable()

        when: "we calculate the longest protein"
        def result = service.findLongestProtein(translationTable,mrna,false)
        String longestPeptide = result['longestPeptide']
        int bestStartIndex =  result['bestStartIndex'] as Integer
        boolean partialStart = result['partialStart'] as Boolean

        then: "we should get good results"
        assert !partialStart
        String expectedCDS = "ATGGAATCTGCTATTGTTCATCTTGAACAAAGCGTGCAAAAGGCTGATGGAAAACTAGACATGATTGCATGGCAAATTGATGCTTTTGAAAAAGAATTTGAAGATCCTGGTAGTGAGATTTCTGTGCTTCGTCTATTACGGTCTGTTCATCAAGTCACAAAAGATTATCAGAACCTTCGGCAAGAAATATTGGAGGTTCAACAATTGCAAAAGCAACTTTCAGATTCCCTTAAAGCACAATTATCTCAAGTGCATGGACATTTTAACTTATTACGCAATAAAATAGTAGGACAAAATAAAAATCTACAATTAAAATAA"
        assert longestPeptide == SequenceTranslationHandler.translateSequence(expectedCDS,translationTable,true,false)
        assert bestStartIndex == 254

        when: "we calculate the longest protein with the readthrough "
        result = service.findLongestProtein(translationTable,mrna,true)
        longestPeptide = result['longestPeptide']
        bestStartIndex =  result['bestStartIndex'] as Integer
        partialStart = result['partialStart'] as Boolean

        then: "we should read through the start codon"
        assert !partialStart
        String expectedCDSWithReadThrough = "ATGGAATCTGCTATTGTTCATCTTGAACAAAGCGTGCAAAAGGCTGATGGAAAACTAGACATGATTGCATGGCAAATTGATGCTTTTGAAAAAGAATTTGAAGATCCTGGTAGTGAGATTTCTGTGCTTCGTCTATTACGGTCTGTTCATCAAGTCACAAAAGATTATCAGAACCTTCGGCAAGAAATATTGGAGGTTCAACAATTGCAAAAGCAACTTTCAGATTCCCTTAAAGCACAATTATCTCAAGTGCATGGACATTTTAACTTATTACGCAATAAAATAGTAGGACAAAATAAAAATCTACAATTAAAATAAGATTAA"
        assert longestPeptide == SequenceTranslationHandler.translateSequence(expectedCDSWithReadThrough,translationTable,true,true)
        assert bestStartIndex == 254

    }


}
