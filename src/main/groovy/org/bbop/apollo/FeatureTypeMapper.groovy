package org.bbop.apollo


import org.bbop.apollo.attributes.FeatureType
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
import org.bbop.apollo.gwt.shared.FeatureStringEnum
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
import org.grails.web.json.JSONObject
import org.neo4j.driver.internal.InternalNode

class FeatureTypeMapper {

    // TODO: add unit tests and move to a non-transcration method
    public static final def SINGLETON_FEATURE_TYPES = [RepeatRegion.cvTerm, TransposableElement.cvTerm, Terminator.cvTerm]
    public static final RNA_FEATURE_TYPES = [
        MRNA.cvTerm, MiRNA.cvTerm, NcRNA.cvTerm, RRNA.cvTerm, SnRNA.cvTerm, SnoRNA.cvTerm,
        TRNA.cvTerm, Transcript.cvTerm,
        GuideRNA.cvTerm, RNaseMRPRNA.cvTerm, TelomeraseRNA.cvTerm, SrpRNA.cvTerm, LncRNA.cvTerm,
        RNasePRNA.cvTerm, ScRNA.cvTerm, PiRNA.cvTerm, TmRNA.cvTerm, EnzymaticRNA.cvTerm,
    ]
    public static final PSEUDOGENIC_FEATURE_TYPES = [Pseudogene.cvTerm, PseudogenicRegion.cvTerm, ProcessedPseudogene.cvTerm]
    public static final List<String> VIEWABLE_ANNOTATION_FEATURE_CV_TERM_LIST =[
        RepeatRegion.cvTerm,
        Terminator.cvTerm,
        TransposableElement.cvTerm
    ]
    public static final List<String> VIEWABLE_ANNOTATION_TRANSCRIPT_PARENT_CV_TERM_LIST =[
        Gene.cvTerm,
        Pseudogene.cvTerm,
        PseudogenicRegion.cvTerm,
        ProcessedPseudogene.cvTerm,
    ]
    public static final List<String> VIEWABLE_ANNOTATION_TRANSCRIPT_CV_TERM_LIST =[MRNA.cvTerm] + NON_CODING_ANNOTATION_TRANSCRIPT_LIST
    public static final List<String> VIEWABLE_ALTERATION_CV_TERM_LIST = [
        DeletionArtifact.cvTerm,
        InsertionArtifact.cvTerm,
        SubstitutionArtifact.cvTerm
    ]
    public static final List<String> VIEWABLE_SEQUENCE_ALTERATION_CV_TERM_LIST =[
        Deletion.cvTerm,
        Insertion.cvTerm,
        Substitution.cvTerm,
        SNV.cvTerm,
        SNP.cvTerm,
        MNV.cvTerm,
        MNP.cvTerm,
        Indel.cvTerm
    ]
    public static
    final List<String> VIEWABLE_ANNOTATION_CV_TERM_LIST = VIEWABLE_ANNOTATION_FEATURE_CV_TERM_LIST + VIEWABLE_ANNOTATION_TRANSCRIPT_PARENT_CV_TERM_LIST + VIEWABLE_SEQUENCE_ALTERATION_CV_TERM_LIST
    public static final List<String> VIEWABLE_ANNOTATION_FEATURE_LIST = [
        RepeatRegion.class.name,
        Terminator.class.name,
        TransposableElement.class.name
    ]
    public static final List<String> VIEWABLE_ANNOTATION_TRANSCIPT_PARENT_LIST = [
        Gene.class.name,
        Pseudogene.class.name,
        PseudogenicRegion.class.name,
        ProcessedPseudogene.class.name,
    ]
    public static final List<String> NON_CODING_ANNOTATION_TRANSCRIPT_LIST = [
        Transcript.class.name,
        TRNA.class.name,
        SnRNA.class.name,
        SnoRNA.class.name,
        NcRNA.class.name,
        RRNA.class.name,
        MiRNA.class.name,
        GuideRNA.class.name,
        RNaseMRPRNA.class.name,
        TelomeraseRNA.class.name,
        SrpRNA.class.name,
        LncRNA.class.name,
        RNaseMRPRNA.class.name,
        ScRNA.class.name,
        PiRNA.class.name,
        TmRNA.class.name,
        EnzymaticRNA.class.name,
    ]
    public static final List<String> VIEWABLE_ANNOTATION_TRANSCRIPT_LIST = [MRNA.class.name] + NON_CODING_ANNOTATION_TRANSCRIPT_LIST
    public static final List<String> viewableAlterationList = [
        DeletionArtifact.class.name,
        InsertionArtifact.class.name,
        SubstitutionArtifact.class.name
    ]
    public static final List<String> VIEWABLE_SEQUENCE_ALTERATION_LIST = [
        Deletion.class.name,
        Insertion.class.name,
        Substitution.class.name,
        SNV.class.name,
        SNP.class.name,
        MNV.class.name,
        MNP.class.name,
        Indel.class.name
    ]
    public static
    final List<String> VIEWABLE_ANNOTATION_LIST = VIEWABLE_ANNOTATION_FEATURE_LIST + VIEWABLE_ANNOTATION_TRANSCIPT_PARENT_LIST + VIEWABLE_SEQUENCE_ALTERATION_LIST
    public static
    final List<String> VIEWABLE_ANNOTATION_TYPES_LIST = VIEWABLE_ANNOTATION_FEATURE_LIST + VIEWABLE_ANNOTATION_TRANSCRIPT_LIST + VIEWABLE_ANNOTATION_TRANSCIPT_PARENT_LIST

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

    static boolean hasOntologyId( String cvTerm,String[] names){
        for(String name in names){
            if(cvTerm.toUpperCase().contains(name.toUpperCase())){
                return true
            }
        }
        return false
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

    // TODO: (perform on client side, slightly ugly)
    static Feature generateFeatureForType(String ontologyId) {
        switch (ontologyId) {
            case MRNA.ontologyId: return new MRNA()
            case MiRNA.ontologyId: return new MiRNA()
            case NcRNA.ontologyId: return new NcRNA()
            case GuideRNA.ontologyId: return new GuideRNA()
            case RNasePRNA.ontologyId: return new RNasePRNA()
            case TelomeraseRNA.ontologyId: return new TelomeraseRNA()
            case SrpRNA.ontologyId: return new SrpRNA()
            case LncRNA.ontologyId: return new LncRNA()
            case RNaseMRPRNA.ontologyId: return new RNaseMRPRNA()
            case ScRNA.ontologyId: return new ScRNA()
            case PiRNA.ontologyId: return new PiRNA()
            case TmRNA.ontologyId: return new TmRNA()
            case EnzymaticRNA.ontologyId: return new EnzymaticRNA()
            case SnoRNA.ontologyId: return new SnoRNA()
            case SnRNA.ontologyId: return new SnRNA()
            case RRNA.ontologyId: return new RRNA()
            case TRNA.ontologyId: return new TRNA()
            case Exon.ontologyId: return new Exon()
            case CDS.ontologyId: return new CDS()
            case Intron.ontologyId: return new Intron()
            case Gene.ontologyId: return new Gene()
            case Pseudogene.ontologyId: return new Pseudogene()
            case PseudogenicRegion.ontologyId: return new PseudogenicRegion()
            case ProcessedPseudogene.ontologyId: return new ProcessedPseudogene()
            case Transcript.ontologyId: return new Transcript()
            case TransposableElement.ontologyId: return new TransposableElement()
            case Terminator.ontologyId: return new Terminator()
            case ShineDalgarnoSequence.ontologyId: return new ShineDalgarnoSequence()
            case RepeatRegion.ontologyId: return new RepeatRegion()
            case InsertionArtifact.ontologyId: return new InsertionArtifact()
            case DeletionArtifact.ontologyId: return new DeletionArtifact()
            case SubstitutionArtifact.ontologyId: return new SubstitutionArtifact()
            case Insertion.ontologyId: return new Insertion()
            case Deletion.ontologyId: return new Deletion()
            case Substitution.ontologyId: return new Substitution()
            case NonCanonicalFivePrimeSpliceSite.ontologyId: return new NonCanonicalFivePrimeSpliceSite()
            case NonCanonicalThreePrimeSpliceSite.ontologyId: return new NonCanonicalThreePrimeSpliceSite()
            case StopCodonReadThrough.ontologyId: return new StopCodonReadThrough()
            case SNV.ontologyId: return new SNV()
            case SNP.ontologyId: return new SNP()
            case MNV.ontologyId: return new MNV()
            case MNP.ontologyId: return new MNP()
            case Indel.ontologyId: return new Indel()
            default:
                throw new RuntimeException("No feature type exists for ${ontologyId}")
        }
    }


    static String convertJSONToOntologyId(JSONObject jsonCVTerm) {
        String cvString = jsonCVTerm.getJSONObject(FeatureStringEnum.CV.value).getString(FeatureStringEnum.NAME.value)
        String cvTermString = jsonCVTerm.getString(FeatureStringEnum.NAME.value)

        if (cvString.equalsIgnoreCase(FeatureStringEnum.CV.value) || cvString.equalsIgnoreCase(FeatureStringEnum.SEQUENCE.value)) {
            switch (cvTermString.toUpperCase()) {
                case MRNA.cvTerm.toUpperCase(): return MRNA.ontologyId
                case MiRNA.cvTerm.toUpperCase(): return MiRNA.ontologyId
                case NcRNA.cvTerm.toUpperCase(): return NcRNA.ontologyId
                case GuideRNA.cvTerm.toUpperCase(): return GuideRNA.ontologyId
                case RNasePRNA.cvTerm.toUpperCase(): return RNasePRNA.ontologyId
                case TelomeraseRNA.cvTerm.toUpperCase(): return TelomeraseRNA.ontologyId
                case SrpRNA.cvTerm.toUpperCase(): return SrpRNA.ontologyId
                case LncRNA.cvTerm.toUpperCase(): return LncRNA.ontologyId
                case RNaseMRPRNA.cvTerm.toUpperCase(): return RNaseMRPRNA.ontologyId
                case ScRNA.cvTerm.toUpperCase(): return ScRNA.ontologyId
                case PiRNA.cvTerm.toUpperCase(): return PiRNA.ontologyId
                case TmRNA.cvTerm.toUpperCase(): return TmRNA.ontologyId
                case EnzymaticRNA.cvTerm.toUpperCase(): return EnzymaticRNA.ontologyId
                case SnoRNA.cvTerm.toUpperCase(): return SnoRNA.ontologyId
                case SnRNA.cvTerm.toUpperCase(): return SnRNA.ontologyId
                case RRNA.cvTerm.toUpperCase(): return RRNA.ontologyId
                case TRNA.cvTerm.toUpperCase(): return TRNA.ontologyId
                case Transcript.cvTerm.toUpperCase(): return Transcript.ontologyId
                case Gene.cvTerm.toUpperCase(): return Gene.ontologyId
                case Exon.cvTerm.toUpperCase(): return Exon.ontologyId
                case CDS.cvTerm.toUpperCase(): return CDS.ontologyId
                case Intron.cvTerm.toUpperCase(): return Intron.ontologyId
                case Pseudogene.cvTerm.toUpperCase(): return Pseudogene.ontologyId
                case PseudogenicRegion.cvTerm.toUpperCase(): return PseudogenicRegion.ontologyId
                case ProcessedPseudogene.cvTerm.toUpperCase(): return ProcessedPseudogene.ontologyId
                case TransposableElement.alternateCvTerm.toUpperCase():
                case TransposableElement.cvTerm.toUpperCase(): return TransposableElement.ontologyId
                case Terminator.alternateCvTerm.toUpperCase():
                case Terminator.cvTerm.toUpperCase(): return Terminator.ontologyId
                case ShineDalgarnoSequence.alternateCvTerm.toUpperCase():
                case ShineDalgarnoSequence.cvTerm.toUpperCase(): return ShineDalgarnoSequence.ontologyId
                case RepeatRegion.alternateCvTerm.toUpperCase():
                case RepeatRegion.cvTerm.toUpperCase(): return RepeatRegion.ontologyId
                case InsertionArtifact.cvTerm.toUpperCase(): return InsertionArtifact.ontologyId
                case DeletionArtifact.cvTerm.toUpperCase(): return DeletionArtifact.ontologyId
                case SubstitutionArtifact.cvTerm.toUpperCase(): return SubstitutionArtifact.ontologyId
                case Insertion.cvTerm.toUpperCase(): return Insertion.ontologyId
                case Deletion.cvTerm.toUpperCase(): return Deletion.ontologyId
                case Substitution.cvTerm.toUpperCase(): return Substitution.ontologyId
                case StopCodonReadThrough.cvTerm.toUpperCase(): return StopCodonReadThrough.ontologyId
                case NonCanonicalFivePrimeSpliceSite.cvTerm.toUpperCase(): return NonCanonicalFivePrimeSpliceSite.ontologyId
                case NonCanonicalThreePrimeSpliceSite.cvTerm.toUpperCase(): return NonCanonicalThreePrimeSpliceSite.ontologyId
                case NonCanonicalFivePrimeSpliceSite.alternateCvTerm.toUpperCase(): return NonCanonicalFivePrimeSpliceSite.ontologyId
                case NonCanonicalThreePrimeSpliceSite.alternateCvTerm.toUpperCase(): return NonCanonicalThreePrimeSpliceSite.ontologyId
                case SNV.cvTerm.toUpperCase(): return SNV.ontologyId
                case SNP.cvTerm.toUpperCase(): return SNP.ontologyId
                case MNV.cvTerm.toUpperCase(): return MNV.ontologyId
                case MNP.cvTerm.toUpperCase(): return MNP.ontologyId
                case Indel.cvTerm.toUpperCase(): return Indel.ontologyId
                default:
                    throw new RuntimeException("CV Term not known ${cvTermString} for CV ${FeatureStringEnum.SEQUENCE}")
            }
        } else {
            throw new RuntimeException("CV not known ${cvString}")
        }

    }

    static String getOntologyIdForType(String type) {
        JSONObject cvTerm = new JSONObject()
        if (type.toUpperCase() == Gene.cvTerm.toUpperCase()) {
            JSONObject cvTermName = new JSONObject()
            cvTermName.put(FeatureStringEnum.NAME.value, FeatureStringEnum.CV.value)
            cvTerm.put(FeatureStringEnum.CV.value, cvTermName)
            cvTerm.put(FeatureStringEnum.NAME.value, type)
        } else {
            JSONObject cvTermName = new JSONObject()
            cvTermName.put(FeatureStringEnum.NAME.value, FeatureStringEnum.SEQUENCE.value)
            cvTerm.put(FeatureStringEnum.CV.value, cvTermName)
            cvTerm.put(FeatureStringEnum.NAME.value, type)
        }
        return convertJSONToOntologyId(cvTerm)
    }

    static List<FeatureType> getFeatureTypeListForType(String type) {
        String ontologyId = getOntologyIdForType(type)
        return FeatureType.findAllByOntologyId(ontologyId)
    }
}
