package org.bbop.apollo

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import org.bbop.apollo.attributes.FeatureType
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Gene
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.services.ServiceUnitTestMixin} for usage instructions
 */
//@TestFor(FeatureTypeService)
//@Mock([Feature,FeatureType])
class FeatureTypeServiceSpec extends Specification implements ServiceUnitTest<FeatureTypeService>, DataTest {

    def setup() {
        mockDomain Feature
        mockDomain FeatureType
    }

    def cleanup() {
    }

    void "can add a feature type"() {

        given: "no feature types"
        assert FeatureType.count == 0

        when: "we add a Feature Type"
        service.createFeatureTypeForFeature(Gene.class, Gene.alternateCvTerm)
        FeatureType featureType = FeatureType.first()

        then: "we should have one"
        assert FeatureType.count == 1
        assert featureType.ontologyId == Gene.ontologyId
        assert featureType.name == Gene.cvTerm
        assert featureType.type == "sequence"
    }
}
