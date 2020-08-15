package org.bbop.apollo

import grails.gorm.transactions.Transactional
import org.bbop.apollo.attributes.FeatureType
import org.bbop.apollo.feature.EnzymaticRNA
import org.bbop.apollo.feature.Gene
import org.bbop.apollo.feature.GuideRNA
import org.bbop.apollo.feature.LncRNA
import org.bbop.apollo.feature.MRNA
import org.bbop.apollo.feature.MiRNA
import org.bbop.apollo.feature.NcRNA
import org.bbop.apollo.feature.PiRNA
import org.bbop.apollo.feature.ProcessedPseudogene
import org.bbop.apollo.feature.Pseudogene
import org.bbop.apollo.feature.PseudogenicRegion
import org.bbop.apollo.feature.RNaseMRPRNA
import org.bbop.apollo.feature.RNasePRNA
import org.bbop.apollo.feature.RRNA
import org.bbop.apollo.feature.RepeatRegion
import org.bbop.apollo.feature.ScRNA
import org.bbop.apollo.feature.SnRNA
import org.bbop.apollo.feature.SnoRNA
import org.bbop.apollo.feature.SrpRNA
import org.bbop.apollo.feature.TRNA
import org.bbop.apollo.feature.TelomeraseRNA
import org.bbop.apollo.feature.Terminator
import org.bbop.apollo.feature.TmRNA
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.feature.TransposableElement

@Transactional
class FeatureTypeService {

    def createFeatureTypeForFeature(Class clazz,String display) {

        FeatureType featureType = new FeatureType(
                name: clazz.cvTerm
                ,display: display
                , type: "sequence"
                , ontologyId: clazz.ontologyId
        ).save(insert: true, flush: true)
        return featureType
    }

    def stubDefaultFeatureTypes(){
        createFeatureTypeForFeature(Gene.class,Gene.cvTerm)
        createFeatureTypeForFeature(Pseudogene.class,Pseudogene.cvTerm)
        createFeatureTypeForFeature(PseudogenicRegion.class,PseudogenicRegion.cvTerm)
        createFeatureTypeForFeature(ProcessedPseudogene.class,ProcessedPseudogene.cvTerm)
        createFeatureTypeForFeature(Transcript.class,Transcript.cvTerm)
        createFeatureTypeForFeature(MRNA.class,MRNA.cvTerm)
        createFeatureTypeForFeature(SnRNA.class,SnRNA.cvTerm)
        createFeatureTypeForFeature(SnoRNA.class,SnoRNA.cvTerm)
        createFeatureTypeForFeature(MiRNA.class,MiRNA.cvTerm)
        createFeatureTypeForFeature(TRNA.class,TRNA.cvTerm)
        createFeatureTypeForFeature(NcRNA.class,NcRNA.cvTerm)

        createFeatureTypeForFeature(GuideRNA.class, GuideRNA.cvTerm)
        createFeatureTypeForFeature(RNaseMRPRNA.class, RNasePRNA.cvTerm)
        createFeatureTypeForFeature(TelomeraseRNA.class, TelomeraseRNA.cvTerm)
        createFeatureTypeForFeature(SrpRNA.class, SrpRNA.cvTerm)
        createFeatureTypeForFeature(LncRNA.class, LncRNA.cvTerm)
        createFeatureTypeForFeature(RNaseMRPRNA.class, RNaseMRPRNA.cvTerm)
        createFeatureTypeForFeature(ScRNA.class, ScRNA.cvTerm)
        createFeatureTypeForFeature(PiRNA.class, PiRNA.cvTerm)
        createFeatureTypeForFeature(TmRNA.class, TmRNA.cvTerm)
        createFeatureTypeForFeature(EnzymaticRNA.class, EnzymaticRNA.cvTerm)

        createFeatureTypeForFeature(RRNA.class,RRNA.cvTerm)
        createFeatureTypeForFeature(RepeatRegion.class,RepeatRegion.cvTerm)
        createFeatureTypeForFeature(Terminator.class,Terminator.alternateCvTerm)
        createFeatureTypeForFeature(TransposableElement.class,TransposableElement.cvTerm)
        return true
    }
}
