package org.bbop.apollo

import org.bbop.apollo.attributes.Comment
import org.bbop.apollo.feature.CDS
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.StopCodonReadThrough
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.gwt.shared.FeatureStringEnum

import grails.gorm.transactions.Transactional
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.sequence.Strand

@Transactional
class CdsService {

    public static final String MANUALLY_SET_TRANSLATION_START = "Manually set translation start";
    public static final String MANUALLY_SET_TRANSLATION_END = "Manually set translation end";

    def featureRelationshipService
    def featurePropertyService
    def transcriptService
    def featureService
    def sequenceService
    def overlapperService
    
    void setManuallySetTranslationStart(CDS cds, boolean manuallySetTranslationStart) {
        if (manuallySetTranslationStart && isManuallySetTranslationStart(cds)) {
            return
        }
        if (!manuallySetTranslationStart && !isManuallySetTranslationStart(cds)) {
            return
        }
        if (manuallySetTranslationStart) {
            featurePropertyService.addComment(cds, MANUALLY_SET_TRANSLATION_START)
        }
        if (!manuallySetTranslationStart) {
            featurePropertyService.deleteComment(cds, MANUALLY_SET_TRANSLATION_START)
        }
    }

    boolean isManuallySetTranslationStart(CDS cds) {
        for (Comment comment : featurePropertyService.getComments(cds)) {
            if (comment.value.equals(MANUALLY_SET_TRANSLATION_START)) {
                return true
            }
        }
        return false
    }


    boolean isManuallySetTranslationEnd(CDS cds) {

        for (Comment comment : featurePropertyService.getComments(cds)) {
            if (comment.value.equals(MANUALLY_SET_TRANSLATION_END)) {
                return true
            }
        }
        return false
    }

    void setManuallySetTranslationEnd(CDS cds, boolean manuallySetTranslationEnd) {
        if (manuallySetTranslationEnd && isManuallySetTranslationEnd(cds)) {
            return
        }
        if (!manuallySetTranslationEnd && !isManuallySetTranslationEnd(cds)) {
            return
        }
        if (manuallySetTranslationEnd) {
            featurePropertyService.addComment(cds, MANUALLY_SET_TRANSLATION_END)
        }
        if (!manuallySetTranslationEnd) {
            featurePropertyService.deleteComment(cds, MANUALLY_SET_TRANSLATION_END)
        }
    }


    /**
     * TODO: is this right?  I think it should be CDS , not transcript?
     * TODO: is this just remove parents and children?
     * @param cds
     * @param stopCodonReadThrough
     * @return
     */
    def deleteStopCodonReadThrough(CDS cds, StopCodonReadThrough stopCodonReadThrough) {
        featureRelationshipService.deleteChildrenForTypes(cds, StopCodonReadThrough.ontologyId)
        featureRelationshipService.deleteParentForTypes(stopCodonReadThrough, Transcript.ontologyId)
        stopCodonReadThrough.delete()
    }

    def deleteStopCodonReadThrough(CDS cds) {
        StopCodonReadThrough stopCodonReadThrough = (StopCodonReadThrough) featureRelationshipService.getChildForFeature(cds,StopCodonReadThrough.ontologyId)
        if (stopCodonReadThrough != null) {
            deleteStopCodonReadThrough(cds, stopCodonReadThrough);
        }

    }

    def getStopCodonReadThrough(CDS cds){
        return featureRelationshipService.getChildrenForFeatureAndTypes(cds,StopCodonReadThrough.ontologyId)
    }

    StopCodonReadThrough createStopCodonReadOnCDS(CDS cds) {
        println "createing stop codon readthrough ${cds}"
        FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
        String uniqueName = cds.getUniqueName() + FeatureStringEnum.STOP_CODON_READHTHROUGH_SUFFIX.value;
        StopCodonReadThrough stopCodonReadThrough = new StopCodonReadThrough(
                uniqueName: uniqueName
                ,name: uniqueName
        ).save(failOnError: true)
        println "B"
        FeatureLocation featureLocation = new FeatureLocation(
                to: cdsFeatureLocation.to
                , from: stopCodonReadThrough
                ,fmin: cdsFeatureLocation.fmin
                ,fmax: cdsFeatureLocation.fmax
                ,strand: cdsFeatureLocation.strand
        ).save(failOnError: true)
        println "C"

        stopCodonReadThrough.setFeatureLocation(featureLocation)
        println "D"
        stopCodonReadThrough.save(fllush:true)
        println "E"

        featureRelationshipService.setChildForType(cds,stopCodonReadThrough)
        println "F"

        FeatureRelationship fr = new FeatureRelationship(
                from: cds
                , to: stopCodonReadThrough
                , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save(insert: true,failOnError: true)
        cds.addToParentFeatureRelationships(fr);
        stopCodonReadThrough.addToChildFeatureRelationships(fr)

        println "G"

        stopCodonReadThrough.save(failOnError: true)
        cds.save(flush: true,failOnError: true)
        println "H"
        def returnedStopCodonReadThrough = getStopCodonReadThrough(cds)
        println "returnedStopCodonReadThrough ${returnedStopCodonReadThrough}"



        return stopCodonReadThrough
    }


    /**
     * @deprecated
     * @param cds
     * @return
     */
    StopCodonReadThrough createStopCodonReadThrough(CDS cds) {
        println "createing stop codon readthrough ${cds}"
        FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
        String uniqueName = cds.getUniqueName() + FeatureStringEnum.STOP_CODON_READHTHROUGH_SUFFIX.value;
        StopCodonReadThrough stopCodonReadThrough = new StopCodonReadThrough(
                uniqueName: uniqueName
                ,name: uniqueName
        ).save(failOnError: true,flush: true)
        FeatureLocation featureLocation = new FeatureLocation(
                to: cdsFeatureLocation.to
                , from: stopCodonReadThrough
                ,fmin: cdsFeatureLocation.fmin
                ,fmax: cdsFeatureLocation.fmax
                ,strand: cdsFeatureLocation.strand
        ).save(failOnError: true,flush: true)

        // explicitly set this?

        stopCodonReadThrough.setFeatureLocation(featureLocation)
//        stopCodonReadThrough.featureLocation.setStrand(cds.getStrand());

        stopCodonReadThrough.save(flush: true)

        def stopCodonsLinked = StopCodonReadThrough.executeQuery(" MATCH (  sc:StopCodonReadThrough )- [fl:FEATURELOCATION] - (s:Sequence) where sc.uniqueName = ${uniqueName}  return sc,fl,s")
        println("stop codons linked ${stopCodonsLinked}")
        def stopCodonsUnlinked = StopCodonReadThrough.executeQuery(" MATCH (  sc:StopCodonReadThrough ) where sc.uniqueName = ${uniqueName}  return sc")
        println("stop codons un-linked ${stopCodonsUnlinked}")

        return stopCodonReadThrough;
    }

    /**
     * @deprecated
     * @param cds
     * @return
     */
    def setStopCodonReadThrough(CDS cds, StopCodonReadThrough stopCodonReadThrough, boolean replace = true) {
        if (replace) {
            featureRelationshipService.setChildForType(cds,stopCodonReadThrough)
        }

        FeatureRelationship fr = new FeatureRelationship(
                from: cds
                , to: stopCodonReadThrough
                , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save(insert: true,failOnError: true)
        cds.addToParentFeatureRelationships(fr);
        stopCodonReadThrough.addToChildFeatureRelationships(fr)

        stopCodonReadThrough.save(failOnError: true)
        cds.save(flush: true,failOnError: true)

    }

    def hasStopCodonReadThrough(CDS cds) {
        return getStopCodonReadThrough(cds).size() != 0
    }

    def getResiduesFromCDS(CDS cds) {
        // New implementation that infers CDS based on overlapping exons
        Transcript transcript = transcriptService.getTranscript(cds)
        List <Exon> exons = transcriptService.getSortedExons(transcript,true)
        String residues = ""
        for(Exon exon : exons) {
            if (!overlapperService.overlaps(exon,cds)) {
                continue
            }
            FeatureLocation exonFeatureLocation = FeatureLocation.findByFrom(exon)
            FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
            println "exon feature location ${exonFeatureLocation}"
            println "cds feature location ${cdsFeatureLocation}"
            int fmin = exonFeatureLocation.fmin < cdsFeatureLocation.fmin ? cdsFeatureLocation.fmin : exonFeatureLocation.fmin
            int fmax = exonFeatureLocation.fmax > cdsFeatureLocation.fmax ? cdsFeatureLocation.fmax : exonFeatureLocation.fmax
            int localStart
            int localEnd
            if (cdsFeatureLocation.strand == Strand.NEGATIVE.value) {
                localEnd = featureService.convertSourceCoordinateToLocalCoordinate(exonFeatureLocation, fmin) + 1
                localStart = featureService.convertSourceCoordinateToLocalCoordinate(exonFeatureLocation, fmax) + 1
            } 
            else {
                localStart = featureService.convertSourceCoordinateToLocalCoordinate(exonFeatureLocation, fmin)
                localEnd = featureService.convertSourceCoordinateToLocalCoordinate(exonFeatureLocation, fmax)
            }
            residues += sequenceService.getResiduesFromFeature((Feature) exon).substring(localStart, localEnd)
        }
        return residues
    }

}
