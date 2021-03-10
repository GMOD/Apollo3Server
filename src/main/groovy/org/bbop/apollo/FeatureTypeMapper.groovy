package org.bbop.apollo

import grails.gorm.transactions.NotTransactional
import org.bbop.apollo.feature.CDS
import org.bbop.apollo.feature.EnzymaticRNA
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Gene
import org.bbop.apollo.feature.GuideRNA
import org.bbop.apollo.feature.Intron
import org.bbop.apollo.feature.LncRNA
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.feature.MiRNA
import org.bbop.apollo.feature.NcRNA
import org.bbop.apollo.feature.NonCanonicalFivePrimeSpliceSite
import org.bbop.apollo.feature.NonCanonicalThreePrimeSpliceSite
import org.bbop.apollo.feature.PiRNA
import org.bbop.apollo.feature.ProcessedPseudogene
import org.bbop.apollo.feature.Pseudogene
import org.bbop.apollo.feature.PseudogenicRegion
import org.bbop.apollo.feature.RNaseMRPRNA
import org.bbop.apollo.feature.RNasePRNA
import org.bbop.apollo.feature.RRNA
import org.bbop.apollo.feature.RepeatRegion
import org.bbop.apollo.feature.ScRNA
import org.bbop.apollo.feature.ShineDalgarnoSequence
import org.bbop.apollo.feature.SnRNA
import org.bbop.apollo.feature.SnoRNA
import org.bbop.apollo.feature.SrpRNA
import org.bbop.apollo.feature.StopCodonReadThrough
import org.bbop.apollo.feature.TRNA
import org.bbop.apollo.feature.TelomeraseRNA
import org.bbop.apollo.feature.Terminator
import org.bbop.apollo.feature.TmRNA
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.feature.TransposableElement
import org.bbop.apollo.variant.Deletion
import org.bbop.apollo.variant.DeletionArtifact
import org.bbop.apollo.variant.Indel
import org.bbop.apollo.variant.Insertion
import org.bbop.apollo.variant.InsertionArtifact
import org.bbop.apollo.variant.MNP
import org.bbop.apollo.variant.MNV
import org.bbop.apollo.variant.SNP
import org.bbop.apollo.variant.SNV
import org.bbop.apollo.variant.Substitution
import org.bbop.apollo.variant.SubstitutionArtifact
import org.neo4j.driver.internal.InternalNode

class FeatureTypeMapper {

    // TODO: add unit tests and move to a non-transcration method
    static def getSOUrlForCvTermLabels(List<String> cvTermsArray){
        return cvTermsArray.collect{  getSOUrlForCvTerm(it)}.findAll{ it!=null}
    }

    static String getSOUrlForCvTerm(String cvTerm){
        String ontologyId = null
        try {
            ontologyId = Class.forName("org.bbop.apollo.feature.${cvTerm}")?.ontologyId
        } catch (e) {
        }
        try {
            ontologyId = ontologyId ?: Class.forName("org.bbop.apollo.variant.${cvTerm}")?.ontologyId
        } catch (e) {
        }
        return ontologyId
    }

    static String findMostSpecificLabel(Collection<String> labels) {
        def filteredLabels = labels.findAll { it != "Feature" && !it.contains("TranscriptRegion") && it != "SpliceSite" }
        if(filteredLabels.contains("MRNA")) return "MRNA"
        return filteredLabels.last()

    }

    static def castNeo4jFeature(InternalNode internalNode) {
        String label = findMostSpecificLabel(internalNode.labels())
        switch (label.toUpperCase()) {
            case MRNA.cvTerm.toUpperCase(): return internalNode as MRNA
            case MiRNA.cvTerm.toUpperCase(): return internalNode as MiRNA
            case NcRNA.cvTerm.toUpperCase(): return internalNode as NcRNA
            case GuideRNA.cvTerm.toUpperCase(): return internalNode as GuideRNA
            case RNasePRNA.cvTerm.toUpperCase(): return internalNode as RNasePRNA
            case TelomeraseRNA.cvTerm.toUpperCase(): return internalNode as TelomeraseRNA
            case SrpRNA.cvTerm.toUpperCase(): return internalNode as SrpRNA
            case LncRNA.cvTerm.toUpperCase(): return internalNode as LncRNA
            case RNaseMRPRNA.cvTerm.toUpperCase(): return internalNode as RNaseMRPRNA
            case ScRNA.cvTerm.toUpperCase(): return internalNode as ScRNA
            case PiRNA.cvTerm.toUpperCase(): return internalNode as PiRNA
            case TmRNA.cvTerm.toUpperCase(): return internalNode as TmRNA
            case EnzymaticRNA.cvTerm.toUpperCase(): return internalNode as EnzymaticRNA
            case SnoRNA.cvTerm.toUpperCase(): return internalNode as SnoRNA
            case SnRNA.cvTerm.toUpperCase(): return internalNode as SnRNA
            case RRNA.cvTerm.toUpperCase(): return internalNode as RRNA
            case TRNA.cvTerm.toUpperCase(): return internalNode as TRNA
            case Transcript.cvTerm.toUpperCase(): return internalNode as Transcript
            case Gene.cvTerm.toUpperCase(): return internalNode as Gene
            case Exon.cvTerm.toUpperCase(): return internalNode as Exon
            case CDS.cvTerm.toUpperCase(): return internalNode as CDS
            case Intron.cvTerm.toUpperCase(): return internalNode as Intron
            case Pseudogene.cvTerm.toUpperCase(): return internalNode as Pseudogene
            case PseudogenicRegion.cvTerm.toUpperCase(): return internalNode as PseudogenicRegion
            case ProcessedPseudogene.cvTerm.toUpperCase(): return internalNode as ProcessedPseudogene
            case TransposableElement.alternateCvTerm.toUpperCase():
            case TransposableElement.cvTerm.toUpperCase(): return internalNode as TransposableElement
            case Terminator.alternateCvTerm.toUpperCase():
            case Terminator.cvTerm.toUpperCase(): return internalNode as Terminator
            case ShineDalgarnoSequence.alternateCvTerm.toUpperCase():
            case ShineDalgarnoSequence.cvTerm.toUpperCase(): return internalNode as ShineDalgarnoSequence
            case RepeatRegion.alternateCvTerm.toUpperCase():
            case RepeatRegion.cvTerm.toUpperCase(): return internalNode as RepeatRegion
            case InsertionArtifact.cvTerm.toUpperCase(): return internalNode as InsertionArtifact
            case DeletionArtifact.cvTerm.toUpperCase(): return internalNode as DeletionArtifact
            case SubstitutionArtifact.cvTerm.toUpperCase(): return internalNode as SubstitutionArtifact
            case Insertion.cvTerm.toUpperCase(): return internalNode as Insertion
            case Deletion.cvTerm.toUpperCase(): return internalNode as Deletion
            case Substitution.cvTerm.toUpperCase(): return internalNode as Substitution
            case StopCodonReadThrough.cvTerm.toUpperCase(): return internalNode as StopCodonReadThrough
            case NonCanonicalFivePrimeSpliceSite.cvTerm.toUpperCase(): return internalNode as NonCanonicalFivePrimeSpliceSite
            case NonCanonicalThreePrimeSpliceSite.cvTerm.toUpperCase(): return internalNode as NonCanonicalThreePrimeSpliceSite
            case NonCanonicalFivePrimeSpliceSite.alternateCvTerm.toUpperCase(): return internalNode as NonCanonicalFivePrimeSpliceSite
            case NonCanonicalThreePrimeSpliceSite.alternateCvTerm.toUpperCase(): return internalNode as NonCanonicalThreePrimeSpliceSite
            case SNV.cvTerm.toUpperCase(): return internalNode as SNV
            case SNP.cvTerm.toUpperCase(): return internalNode as SNP
            case MNV.cvTerm.toUpperCase(): return internalNode as MNV
            case MNP.cvTerm.toUpperCase(): return internalNode as MNP
            case Indel.cvTerm.toUpperCase(): return internalNode as Indel
            default:
                throw new RuntimeException("Unable to find type for ${internalNode} with labels ${internalNode.labels()} and label ${label}.")
//        log.error("No feature type exists for ${ontologyId}")
//                return internalNode as Feature
        }

    }
}
