package org.bbop.apollo

import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.sequence.Strand

@Integration
@Rollback
class ExonServiceIntegrationSpec extends AbstractIntegrationSpec{
    
    def exonService

    void "merge to exons for a transcript"() {

        given: "we have 2 exons attached to the same transcript"
        setupDefaultUserOrg()
        Sequence sequence = new Sequence(
            length: 300000
            ,seqChunkSize: 3
            ,start: 5
            ,end: 8
            ,name: "Group1.10"
        ).save()
        println "organisms ${Organism.count}"
        println "sequences ${Sequence.count}"
        Exon leftExon = new Exon(name: "left",uniqueName: "left").save()
        FeatureLocation leftFeatureLocation = new FeatureLocation(
                fmin: 5
                ,fmax: 10
                ,from: leftExon
                ,to: Sequence.first()
                ,strand: Strand.POSITIVE.value
        ).save()
        leftExon.addToFeatureLocations(leftFeatureLocation)
        Exon rightExon = new Exon(name: "right",uniqueName: "right").save()
        FeatureLocation rightFeatureLocation = new FeatureLocation(
                fmin: 15
                ,fmax: 20
                ,from: rightExon
                ,to: Sequence.first()
                ,strand: Strand.POSITIVE.value
        ).save()
        rightExon.addToFeatureLocations(rightFeatureLocation)
        MRNA mrna = new MRNA(name: "mrna",uniqueName: "mrna").save()
        FeatureLocation transcriptFeatureLocation = new FeatureLocation(
                fmin: 2
                ,fmax: 25
                ,from: mrna
                ,to: Sequence.first()
                ,strand: Strand.POSITIVE.value
        ).save()
        mrna.addToFeatureLocations(transcriptFeatureLocation)
        FeatureRelationship leftExonFeatureRelationship = new FeatureRelationship(
                from: mrna
                ,to: leftExon
        ).save()
        FeatureRelationship rightExonFeatureRelationship = new FeatureRelationship(
                from: mrna
                ,to: rightExon
        ).save()

        when: "we add the proper relationships"
        mrna.addToParentFeatureRelationships(leftExonFeatureRelationship)
        leftExon.addToChildFeatureRelationships(leftExonFeatureRelationship)
        mrna.addToParentFeatureRelationships(rightExonFeatureRelationship)
        rightExon.addToChildFeatureRelationships(rightExonFeatureRelationship)


        then: "everything is properly saved"
        Exon.count ==2
        MRNA.count ==1
        FeatureLocation.count == 3
        FeatureRelationship.count == 2
        mrna.parentFeatureRelationships.size()==2
        leftExon.childFeatureRelationships.size()==1
        rightExon.childFeatureRelationships.size()==1
        Exon.findByName("left").featureLocation.fmin==5
        Exon.findByName("right").featureLocation.fmin==15
        MRNA.findByName("mrna").featureLocation.fmin==2
        assert "mrna"==exonService.getTranscript(leftExon).name
        assert "mrna"==exonService.getTranscript(rightExon).name


        when: "we delete an exon2"
        exonService.deleteExon(mrna,rightExon)
        
        then: "there should be only one exon left"
        assert Exon.count==1
        assert FeatureRelationship.count==1
        assert mrna.parentFeatureRelationships.size()==1
        
//        when: "we merge the exons we should still have 2"
//        exonService.mergeExons(leftExon,rightExon)
//
//        then: "we should still have two exons has they don't overlap"
//        Exon.count==1
//        assert 0==1

    }
}
