package org.bbop.apollo

import grails.test.neo4j.Neo4jSpec
import grails.testing.gorm.DataTest
import grails.testing.gorm.DomainUnitTest
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Gene
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.relationship.FeatureRelationship

/**
 * See the API for {@link grails.test.mixin.domain.DomainClassUnitTestMixin} for usage instructions
 */
class FeatureSpec extends Neo4jSpec implements DomainUnitTest<Feature>, DataTest {

    def setup() {
    }

    def cleanup() {
    }

//    @Ignore
    void "test feature manual copy"() {

        when: "If I clone a feature"
        Sequence sequence = new Sequence(
            name: "Chr1"
            , start: 1
            , end: 1013
            , length: 1013
            , seqChunkSize: 50
        ).save(failOnError: true)


        Gene feature1 = new Gene(
            name: "Sox9a"
            , uniqueName: "ABC123"
            , sequenceLength: 17
        ).save(failOnError: true)

        FeatureLocation featureLocation = new FeatureLocation(
            fmin: 13
            , fmax: 77
            , from: feature1
            , to: sequence
        ).save(failOnError: true)

        feature1.setFeatureLocation(featureLocation)
        feature1.save(failOnError: true)


        MRNA mrna = new MRNA(
            name: "Sox9a-01"
            , uniqueName: "ABC123-mrna"
            , sequenceLength: 17
        ).save(failOnError: true)

        FeatureRelationship featureRelationship = new FeatureRelationship(
            from: feature1,
            to: mrna
        ).save(failOnError: true )

        then: "It should be identical in all properties but the id and uniquename and relationships"
        assert Feature.count == 2
        assert Gene.count == 1
        assert MRNA.count == 1
        assert FeatureLocation.count == 1
        assert Sequence.count == 1

        assert feature1.featureLocation != null

        FeatureLocation featureLocation1 = feature1.featureLocation

        assert featureLocation1.fmin == 13
        assert featureLocation1.to.name == "Chr1"
        assert featureLocation1.fmax == 77

        assert FeatureRelationship.count == 1
        FeatureRelationship fr = FeatureRelationship.first()
        fr.from.name == Feature.findByUniqueName("ABC123").name
        fr.to.name == Feature.findByUniqueName("ABC123-mrna").name

    }


}
