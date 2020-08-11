package org.bbop.apollo

import grails.core.GrailsApplication
import grails.testing.services.ServiceUnitTest
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.TranslationTable
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.domain.DomainClassUnitTestMixin} for usage instructions
 */
class TranslationTableSpec extends Specification implements ServiceUnitTest<ConfigWrapperService> {

//    GrailsApplication grailsApplication

//    void "get the appropriate translation table for code"() {
//
////        when: "we ask for the config"
////        def apolloProperties = grailsApplication.config.getProperty("apollo")
////        def translationCode = grailsApplication.config.apollo.get_translation_code
////
////        then: "it is available"
////        assert apolloProperties != null
////        assert translationCode != null
////        assert translationCode == 1
//
//
//        when: "we ask the config wrapper service"
//        def translationCode = service.getTranslationTable()
//
//        then: "we get the right one"
//        assert translationCode != null
//        assert translationCode == 1
//
//
//        when: "we ask for the coded translation table"
//
//
//        then: "we get the right one"
//
//
//    }


    // if we init with "default" does that work?
    void "is the default behavior correct?"() {

        given:
        SequenceTranslationHandler handler = new SequenceTranslationHandler()

        when: "we read a translation table"
        // be something with STOPS, etc.
        TranslationTable translationTable = handler.getDefaultTranslationTable()

        then: "we should get the correct results"
        assert translationTable != null
        assert translationTable.startCodons.size() == 1
        assert translationTable.stopCodons.size() == 3
        assert translationTable.alternateTranslationTable.size() == 1
        assert translationTable.translationTable.size() == 64
        assert translationTable.alternateTranslationTable.size() == 1
    }


    void "can I read in translation tables"() {

        given:
        File file = new File("src/main/webapp/translation_tables/ncbi_11_translation_table.txt")

        when: "we read a translation table"
        TranslationTable translationTable = SequenceTranslationHandler.readTable(file)

        then: "we should get the correct results"
        assert translationTable.startCodons.size() == 1 + 6
        assert translationTable.stopCodons.size() == 3
        assert translationTable.translationTable.size() == 64 // start codons are existing translations
        assert translationTable.alternateTranslationTable.size() == 1
    }

    void "is the init behavior correct?"() {

        given:
        SequenceTranslationHandler handler = new SequenceTranslationHandler()

        when: "we read a translation table"
        // be something with STOPS, etc.
        TranslationTable translationTable = handler.getTranslationTableForGeneticCode("2")

        then: "we should get the correct results"
        assert translationTable != null
        assert translationTable.startCodons.size() == 1 + 1 - 1
        assert translationTable.stopCodons.size() == 3 + 2 - 1
        assert translationTable.translationTable.size() == 64
        assert translationTable.alternateTranslationTable.size() == 1 - 1
    }

}
