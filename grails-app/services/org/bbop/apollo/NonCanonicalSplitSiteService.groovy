package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.NonCanonicalFivePrimeSpliceSite
import org.bbop.apollo.feature.NonCanonicalThreePrimeSpliceSite
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.variant.SequenceAlterationArtifact

//@GrailsCompileStatic
@Transactional
class NonCanonicalSplitSiteService {

    def featureRelationshipService
    def transcriptService
    def featureService
    def sequenceService

    /** Delete an non canonical 5' splice site.  Deletes both the transcript -> non canonical 5' splice site and
     *  non canonical 5' splice site -> transcript relationships.
     *
     * @param nonCanonicalFivePrimeSpliceSite - NonCanonicalFivePrimeSpliceSite to be deleted
     */
    void deleteNonCanonicalFivePrimeSpliceSite(Transcript transcript, NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {

        featureRelationshipService.deleteChildrenForTypes(transcript, NonCanonicalFivePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalFivePrimeSpliceSite, Transcript.ontologyId)
        nonCanonicalFivePrimeSpliceSite.delete(flush: true)
    }

    void deleteNonCanonicalThreePrimeSpliceSite(Transcript transcript, NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {
        featureRelationshipService.deleteChildrenForTypes(transcript, NonCanonicalThreePrimeSpliceSite.ontologyId)
        featureRelationshipService.deleteParentForTypes(nonCanonicalThreePrimeSpliceSite, Transcript.ontologyId)
        nonCanonicalThreePrimeSpliceSite.delete(flush: true)
    }

    /** Delete all non canonical 5' splice site.  Deletes all transcript -> non canonical 5' splice sites and
     *  non canonical 5' splice sites -> transcript relationships.
     *
     */
    void deleteAllNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            deleteNonCanonicalFivePrimeSpliceSite(transcript, spliceSite);
        }
    }

    /** Retrieve all the non canonical 5' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 5' splice sites.  Non canonical 5' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 5' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 5' splice sites associated with this transcript
     */
    Collection<NonCanonicalFivePrimeSpliceSite> getNonCanonicalFivePrimeSpliceSites(Transcript transcript) {
        return (Collection<NonCanonicalFivePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript, NonCanonicalFivePrimeSpliceSite.ontologyId)
    }

    /** Retrieve all the non canonical 3' splice sites associated with this transcript.  Uses the configuration to determine
     *  which children are non canonical 3' splice sites.  Non canonical 3' splice site objects are generated on the fly.
     *  The collection will be empty if there are no non canonical 3' splice sites associated with the transcript.
     *
     * @return Collection of non canonical 3' splice sites associated with this transcript
     */
    Collection<NonCanonicalThreePrimeSpliceSite> getNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
//        return (Collection<NonCanonicalThreePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript,FeatureStringEnum.NONCANONICALTHREEPRIMESPLICESITE)
        return (Collection<NonCanonicalThreePrimeSpliceSite>) featureRelationshipService.getChildrenForFeatureAndTypes(transcript, NonCanonicalThreePrimeSpliceSite.ontologyId)
    }

    /** Delete all non canonical 3' splice site.  Deletes all transcript -> non canonical 3' splice sites and
     *  non canonical 3' splice sites -> transcript relationships.
     *
     */
    void deleteAllNonCanonicalThreePrimeSpliceSites(Transcript transcript) {
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
//            featureRelationshipService.deleteRelationships(transcript,NonCanonicalThreePrimeSpliceSite.ontologyId,Transcript.ontologyId)
            deleteNonCanonicalThreePrimeSpliceSite(transcript, spliceSite)
        }
    }


    void findNonCanonicalAcceptorDonorSpliceSites(Transcript transcript) {

//        transcript.attach()

        deleteAllNonCanonicalFivePrimeSpliceSites(transcript)
        deleteAllNonCanonicalThreePrimeSpliceSites(transcript)

        FeatureLocation transcriptFeatureLocation = FeatureLocation.executeQuery(" MATCH (t:Transcript)-[fl:FEATURELOCATION]-(s:Sequence) where t.uniqueName = ${transcript.uniqueName} return fl " )[0] as FeatureLocation

        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        int fmin = transcriptFeatureLocation.fmin
        int fmax = transcriptFeatureLocation.fmax
        Sequence sequence = Sequence.executeQuery("MATCH (s:Sequence)--(t:Transcript) where t.uniqueName = ${transcript.uniqueName} return s")[0] as Sequence
        Strand strand = transcriptFeatureLocation.strand == -1 ? Strand.NEGATIVE : Strand.POSITIVE

        String residues = sequenceService.getGenomicResiduesFromSequenceWithAlterations(sequence, fmin, fmax, strand);

        if (transcriptFeatureLocation.getStrand() == -1) {
            residues = residues.reverse()
        }

        List<SequenceAlterationArtifact> sequenceAlterationList = new ArrayList<>()
        sequenceAlterationList.addAll(featureService.getAllSequenceAlterationsForFeature(transcript))

        for (Exon exon : exons) {
//            FeatureLocation exonFeatureLocation = FeatureLocation.findByFrom(exon)
            FeatureLocation exonFeatureLocation = FeatureLocation.executeQuery(" MATCH (e:Exon)-[fl:FEATURELOCATION]-(s:Sequence) where e.uniqueName = ${exon.uniqueName} return fl " )[0] as FeatureLocation
            int fivePrimeSpliceSitePosition = -1;
            int threePrimeSpliceSitePosition = -1;
            boolean validFivePrimeSplice = false;
            boolean validThreePrimeSplice = false;
            for (String donor : SequenceTranslationHandler.getSpliceDonorSites()) {
                for (String acceptor : SequenceTranslationHandler.getSpliceAcceptorSites()) {
                    int local11 = exonFeatureLocation.fmin - donor.length() - transcriptFeatureLocation.fmin
                    int local22 = exonFeatureLocation.fmin - transcriptFeatureLocation.fmin
                    int local33 = exonFeatureLocation.fmax - transcriptFeatureLocation.fmin
                    int local44 = exonFeatureLocation.fmax + donor.length() - transcriptFeatureLocation.fmin

                    int local1 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local11, sequenceAlterationList)
                    int local2 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local22, sequenceAlterationList)
                    int local3 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local33, sequenceAlterationList)
                    int local4 = featureService.convertSourceToModifiedLocalCoordinate(transcript, local44, sequenceAlterationList)


                    if (exonFeatureLocation.getStrand() == -1) {
                        int tmp1 = local1
                        int tmp2 = local2
                        local1 = local3
                        local2 = local4
                        local3 = tmp1
                        local4 = tmp2
                    }
                    if (local1 >= 0 && local2 < residues.length()) {
                        String acceptorSpliceSiteSequence = residues.substring(local1, local2)
                        acceptorSpliceSiteSequence = transcriptFeatureLocation.getStrand() == -1 ? acceptorSpliceSiteSequence.reverse() : acceptorSpliceSiteSequence
                        if (acceptorSpliceSiteSequence.toLowerCase() == acceptor) {
                            validThreePrimeSplice = true
                        } else {
                            threePrimeSpliceSitePosition = exon.getStrand() == -1 ? local1 : local2;
                        }
                    }

                    if (local3 >= 0 && local4 < residues.length()) {
                        String donorSpliceSiteSequence = residues.substring(local3, local4)
                        donorSpliceSiteSequence = transcriptFeatureLocation.getStrand() == -1 ? donorSpliceSiteSequence.reverse() : donorSpliceSiteSequence
                        if (donorSpliceSiteSequence.toLowerCase() == donor) {
                            validFivePrimeSplice = true
                        } else {
                            fivePrimeSpliceSitePosition = exonFeatureLocation.getStrand() == -1 ? local3 : local4;
                        }
                    }
                }
            }
            if (!validFivePrimeSplice && fivePrimeSpliceSitePosition != -1) {
                def loc = fivePrimeSpliceSitePosition + transcriptFeatureLocation.fmin
                addNonCanonicalFivePrimeSpliceSite(transcript, createNonCanonicalFivePrimeSpliceSite(transcript, loc));
            }
            if (!validThreePrimeSplice && threePrimeSpliceSitePosition != -1) {
                def loc = threePrimeSpliceSitePosition + transcriptFeatureLocation.fmin
                addNonCanonicalThreePrimeSpliceSite(transcript, createNonCanonicalThreePrimeSpliceSite(transcript, loc));
            }
        }

        for (NonCanonicalFivePrimeSpliceSite spliceSite : getNonCanonicalFivePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
        for (NonCanonicalThreePrimeSpliceSite spliceSite : getNonCanonicalThreePrimeSpliceSites(transcript)) {
            if (spliceSite.getDateCreated() == null) {
                spliceSite.setDateCreated(new Date());
            }
            spliceSite.setLastUpdated(new Date());
        }
    }

    /** Add a non canonical 5' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalFivePrimeSpliceSite - Non canonical 5' splice site to be added
     */
    void addNonCanonicalFivePrimeSpliceSite(Transcript transcript, NonCanonicalFivePrimeSpliceSite nonCanonicalFivePrimeSpliceSite) {
//        CVTerm partOfCvterm = cvTermService.partOf

        // add non canonical 5' splice site
        FeatureRelationship fr = new FeatureRelationship(
//                type: cvTermService.partOf
            from: transcript
            , to: nonCanonicalFivePrimeSpliceSite
            , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save();
        transcript.addToParentFeatureRelationships(fr);
        nonCanonicalFivePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    /** Add a non canonical 3' splice site.  Sets the splice site's transcript to this transcript object.
     *
     * @param nonCanonicalThreePrimeSpliceSite - Non canonical 3' splice site to be added
     */
    void addNonCanonicalThreePrimeSpliceSite(Transcript transcript, NonCanonicalThreePrimeSpliceSite nonCanonicalThreePrimeSpliceSite) {

        // add non canonical 3' splice site
        FeatureRelationship fr = new FeatureRelationship(
//                type: cvTermService.partOf
            from: transcript
            , to: nonCanonicalThreePrimeSpliceSite
            , rank: 0 // TODO: Do we need to rank the order of any other transcripts?
        ).save();
        transcript.addToParentFeatureRelationships(fr);
        nonCanonicalThreePrimeSpliceSite.addToChildFeatureRelationships(fr);
    }

    private NonCanonicalFivePrimeSpliceSite createNonCanonicalFivePrimeSpliceSite(Transcript transcript, int position) {
        String uniqueName = transcript.getUniqueName() + "-non_canonical_five_prime_splice_site-" + position;
        NonCanonicalFivePrimeSpliceSite spliceSite = new NonCanonicalFivePrimeSpliceSite(
            uniqueName: uniqueName
            , name: uniqueName
        ).save(flush: true)

        spliceSite.setFeatureLocation(new FeatureLocation(
            strand: transcript.strand
            , to: transcript.featureLocation.to
            , fmin: position
            , fmax: position
            , from: spliceSite
        ).save(flush: true));
        return spliceSite;
    }


    private NonCanonicalThreePrimeSpliceSite createNonCanonicalThreePrimeSpliceSite(Transcript transcript, int position) {
        String uniqueName = transcript.getUniqueName() + "-non_canonical_three_prime_splice_site-" + position;
        NonCanonicalThreePrimeSpliceSite spliceSite = new NonCanonicalThreePrimeSpliceSite(
            uniqueName: uniqueName
            , name: uniqueName
        ).save(flush: true )
        spliceSite.setFeatureLocation(new FeatureLocation(
            strand: transcript.strand
            , to: transcript.featureLocation.to
            , fmin: position
            , fmax: position
            , from: spliceSite
        ).save(flush: true));
        return spliceSite;
    }

}
