package org.bbop.apollo

import grails.test.neo4j.Neo4jSpec
import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Gene
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.relationship.FeatureRelationship

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
class FeatureRelationshipServiceSpec extends Neo4jSpec implements ServiceUnitTest<FeatureRelationshipService>, DataTest{

    def setup() {
//        mockDomain Gene
//        mockDomain MRNA
//        mockDomain Feature
    }

    def cleanup() {
    }

    void "parents for feature"() {

//        then:"everything is clean "
//

        when: "A feature has parents"
        Gene gene = new Gene(
            name: "Gene1"
            ,uniqueName: UUID.randomUUID().toString()
        ).save(failOnError: true)
        MRNA mrna = new MRNA(
            name: "MRNA"
            ,uniqueName: UUID.randomUUID().toString()
        ).save(failOnError: true)
        FeatureRelationship fr=new FeatureRelationship(
            from: gene
            , to: mrna
        ).save(failOnError: true,flush: true )
        List<Feature> parents = service.getParentsForFeature(mrna,Gene.ontologyId)
        List<Feature> children = service.getChildrenForFeatureAndTypes(gene,MRNA.ontologyId)
//        Feature gene2 = parents.get(0)
//        Feature mrna2= children.get(0)


        then: "it should have parents"
        assert FeatureRelationship.count==1
        assert parents.size() ==1
        assert Gene.count == 1
//        assert gene == gene2

        assert children.size() ==1
        assert MRNA.count == 1
//        assert mrna == mrna2

        when: "we get a single parent for an ontology id"
        Feature parent = service.getParentForFeature(mrna,Gene.ontologyId)

        then: "we should find a valid parent"
        assert parent !=null

        when: "we get a single parent for NO ontology id"
        Feature parent2 = service.getParentForFeature(mrna)

        then: "we should *STILL* find a valid parent"
        assert parent2 !=null

        // NOTE: can not test hql queries
        when: "we delete a relationship"
        service.removeFeatureRelationship(gene,mrna)
        parents = service.getParentsForFeature(mrna,Gene.ontologyId)
        children = service.getChildrenForFeatureAndTypes(gene,MRNA.ontologyId)

        then: "they should both exist, but not be related"
        assert parents.size() ==0
        assert children.size() == 0
        assert FeatureRelationship.count==0
    }
}
