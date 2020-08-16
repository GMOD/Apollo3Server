package org.bbop.apollo

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import spock.lang.Specification

/**
 */
class OrganismServiceSpec extends Specification implements ServiceUnitTest<OrganismService>, DataTest {

    def setup() {
        mockDomain Organism
    }

    def cleanup() {
    }

    void "find a blat DB"() {

        when: "if we provide an empty directory"
        String tempPath1 = File.createTempDir().absolutePath
        String blatDb1 = service.findBlatDB(tempPath1)

        then: "we should return null"
        assert blatDb1 == null


        when: "we create a directory with the proper stuff"
        File tempPath2 = File.createTempDir()
        File searchDB = new File(tempPath2.absolutePath + "/" + FeatureStringEnum.SEARCH_DATABASE_DATA.value)
        assert searchDB.mkdir()
        File searchFile = new File(searchDB.absolutePath + "/testfile.2bit")
        assert searchFile.createNewFile()
        String blatDb2 = service.findBlatDB(tempPath2.absolutePath)

        then: "we should find the appropriate file"
        assert blatDb2 == searchFile.absolutePath


    }


    void "test cypher" () {

        when: "we add an organism"
        Organism organism1 = new Organism(
          commonName: 'bob'  ,
            directory: 'here'
        ).save()
        new Sequence(
            name: "bobSeq1",
            organism: organism1
        ).save()

        then: "we can get it back using Cypher"
        assert Organism.count == 1

        when: "we query using cypher"
        def organisms = Organism.createCriteria().get{
            eq 'commonName','bob'
            join 'sequences'
        }.list()
        def anOrganism = Organism.createCriteria().get{
            eq 'commonName','bob'
            join 'sequences'
        }

        then: "we find an organism "
        assert organisms.size() ==1
        assert anOrganism!=null


    }


}
