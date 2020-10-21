package org.bbop.apollo

import grails.converters.JSON
import org.apache.commons.lang.WordUtils
import org.bbop.apollo.attributes.Comment
import org.bbop.apollo.attributes.DBXref
import org.bbop.apollo.attributes.FeatureProperty
import org.bbop.apollo.attributes.FeatureSynonym
import org.bbop.apollo.feature.CDS
import org.bbop.apollo.feature.Exon
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.feature.Transcript
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.variant.InsertionArtifact
import org.bbop.apollo.variant.SequenceAlterationArtifact
import org.bbop.apollo.variant.SubstitutionArtifact

import java.text.SimpleDateFormat

class Gff3HandlerService {

    def sequenceService
    def featureRelationshipService
    def transcriptService
    def configWrapperService
    def requestHandlingService
    def featureService
    def overlapperService
    def featurePropertyService
    def geneProductService
    def provenanceService
    def goAnnotationService

    SimpleDateFormat gff3DateFormat = new SimpleDateFormat("YYYY-MM-dd")

    static final def unusedStandardAttributes = ["Alias", "Target", "Gap", "Derives_from", "Ontology_term", "Is_circular"];

    void writeNeo4jFeaturesToText(String path, def features, String source, Boolean exportSequence = false, Collection<Sequence> sequences = null) throws IOException {
        WriteObject writeObject = new WriteObject()

        writeObject.mode = Mode.WRITE
        writeObject.file = new File(path)
        writeObject.format = Format.TEXT

        log.debug "Writing neo4j features to GFF3 text"

        // TODO: use specified metadata?
        writeObject.attributesToExport.add(FeatureStringEnum.NAME.value)
        writeObject.attributesToExport.add(FeatureStringEnum.SYMBOL.value)
        writeObject.attributesToExport.add(FeatureStringEnum.SYNONYMS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DESCRIPTION.value)
        writeObject.attributesToExport.add(FeatureStringEnum.STATUS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DBXREFS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.OWNER.value)
        writeObject.attributesToExport.add(FeatureStringEnum.ATTRIBUTES.value)
        writeObject.attributesToExport.add(FeatureStringEnum.PUBMEDIDS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GENE_PRODUCT.value)
        writeObject.attributesToExport.add(FeatureStringEnum.PROVENANCE.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GO_ANNOTATIONS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GOIDS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.COMMENTS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DATE_CREATION.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DATE_LAST_MODIFIED.value)

        if (!writeObject.file.canWrite()) {
            throw new IOException("Cannot write GFF3 to: " + writeObject.file.getAbsolutePath())
        }

        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(writeObject.file, true)))
        writeObject.out = out
        out.println("##gff-version 3")

        writeNeo4jFeatures(writeObject, features, source)
        if (exportSequence) {
            writeFastaForReferenceSequences(writeObject, sequences)
            writeFastaForSequenceAlterations(writeObject, features)
        }
        out.flush()
        out.close()
    }

    void writeFeaturesToText(String path, List<Feature> features, String source, Boolean exportSequence = false, Collection<Sequence> sequences = null) throws IOException {
        WriteObject writeObject = new WriteObject()

        writeObject.mode = Mode.WRITE
        writeObject.file = new File(path)
        writeObject.format = Format.TEXT

        // TODO: use specified metadata?
        writeObject.attributesToExport.add(FeatureStringEnum.NAME.value)
        writeObject.attributesToExport.add(FeatureStringEnum.SYMBOL.value)
        writeObject.attributesToExport.add(FeatureStringEnum.SYNONYMS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DESCRIPTION.value)
        writeObject.attributesToExport.add(FeatureStringEnum.STATUS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DBXREFS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.OWNER.value)
        writeObject.attributesToExport.add(FeatureStringEnum.ATTRIBUTES.value)
        writeObject.attributesToExport.add(FeatureStringEnum.PUBMEDIDS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GENE_PRODUCT.value)
        writeObject.attributesToExport.add(FeatureStringEnum.PROVENANCE.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GO_ANNOTATIONS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.GOIDS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.COMMENTS.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DATE_CREATION.value)
        writeObject.attributesToExport.add(FeatureStringEnum.DATE_LAST_MODIFIED.value)

        if (!writeObject.file.canWrite()) {
            throw new IOException("Cannot write GFF3 to: " + writeObject.file.getAbsolutePath())
        }

        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(writeObject.file, true)))
        writeObject.out = out
        out.println("##gff-version 3")
        writeFeatures(writeObject, features, source)
        if (exportSequence) {
            writeFastaForReferenceSequences(writeObject, sequences)
            writeFastaForSequenceAlterations(writeObject, features)
        }
        out.flush()
        out.close()
    }

    void writeNeo4jFeatures(WriteObject writeObject, def features, String source) throws IOException {
        Map<Sequence, ?> featuresBySource = new HashMap<Sequence, ?>();
        log.debug("writing features " + features)
        for (def result : features) {

//            Feature feature = neo4jFeature as Feature
////            log.debug "a feature ${feature.properties}"
//            log.debug "neo4j feature ${neo4jFeature}"
//            log.debug "feature keys ${feature.keys()}"
            Sequence sourceFeature = result.sequence as Sequence
//            log.debug "source feature ${sourceFeature}"
            Collection<Feature> featureList = featuresBySource.get(sourceFeature);
            if (!featureList) {
                featureList = new ArrayList<Feature>();
                featuresBySource.put(sourceFeature, featureList);
            }
            featureList.add(result);
        }
        featuresBySource.sort { it.key }
        Set<String> geneIds = new HashSet<>()
        for (Map.Entry<Sequence, Collection> entry : featuresBySource.entrySet()) {
            writeGroupDirectives(writeObject, entry.getKey())
            for (def result : entry.getValue()) {
                writeNeo4jFeature(writeObject, result, source,geneIds)
                writeFeatureGroupEnd(writeObject.out)
                log.debug "result ${result}"
            }
        }
    }


    void writeFeatures(WriteObject writeObject, Collection<Feature> features, String source) throws IOException {
        Map<Sequence, Collection<Feature>> featuresBySource = new HashMap<Sequence, Collection<Feature>>();
        log.debug("writing features " + features)
        for (Feature feature : features) {
//            Feature feature = neo4jFeature as Feature
////            log.debug "a feature ${feature.properties}"
//            log.debug "neo4j feature ${neo4jFeature}"
//            log.debug "feature ${feature}"
            Sequence sourceFeature = feature.featureLocation.to
            log.debug "source feature ${sourceFeature}"
            Collection<Feature> featureList = featuresBySource.get(sourceFeature);
            if (!featureList) {
                featureList = new ArrayList<Feature>();
                featuresBySource.put(sourceFeature, featureList);
            }
            featureList.add(feature);
        }
        featuresBySource.sort { it.key }
        for (Map.Entry<Sequence, Collection<Feature>> entry : featuresBySource.entrySet()) {
            writeGroupDirectives(writeObject, entry.getKey());
            for (Feature feature : entry.getValue()) {
                writeFeature(writeObject, feature, source);
                writeFeatureGroupEnd(writeObject.out);
            }
        }
    }


    void writeFeatures(WriteObject writeObject, Iterator<? extends Feature> iterator, String source, boolean needDirectives) throws IOException {
        while (iterator.hasNext()) {
            Feature feature = iterator.next();
            if (needDirectives) {
                writeGroupDirectives(writeObject, feature.featureLocation.to)
                needDirectives = false;
            }
            writeFeature(writeObject, feature, source);
            writeFeatureGroupEnd(writeObject.out);
        }
    }

    static private void writeGroupDirectives(WriteObject writeObject, Sequence sourceFeature) {
        if (sourceFeature.featureLocations?.size() == 0) return;
        writeObject.out.println(String.format("##sequence-region %s %d %d", sourceFeature.name, sourceFeature.start + 1, sourceFeature.end));
    }

    static private void writeFeatureGroupEnd(PrintWriter out) {
        out.println("###");
    }

    static private void writeEmptyFastaDirective(PrintWriter out) {
        out.println("##FASTA");
    }

    private void writeNeo4jFeature(WriteObject writeObject, def result, String source,Set<String> writtenGeneIds) {
        for (GFF3Entry entry : convertNeo4jTranscriptToEntry(writeObject, result, source,writtenGeneIds)) {
            writeObject.out.println(entry.toString());
        }
    }

    private void writeFeature(WriteObject writeObject, Feature feature, String source) {
        for (GFF3Entry entry : convertToEntry(writeObject, feature, source)) {
            writeObject.out.println(entry.toString());
        }
    }

    void writeFasta(WriteObject writeObject, Collection<? extends Feature> features) {
        writeEmptyFastaDirective(writeObject.out);
        for (Feature feature : features) {
            writeFasta(writeObject.out, feature, false);
        }
    }

    void writeFasta(PrintWriter out, Feature feature) {
        writeFasta(out, feature, true);
    }

    void writeFasta(PrintWriter out, Feature feature, boolean writeFastaDirective) {
        writeFasta(out, feature, writeFastaDirective, true);
    }

    void writeFasta(PrintWriter out, Feature feature, boolean writeFastaDirective, boolean useLocation) {
        int lineLength = 60;
        if (writeFastaDirective) {
            writeEmptyFastaDirective(out);
        }
        String residues = null;
        if (useLocation) {
            residues = sequenceService.getResidueFromFeatureLocation(feature.featureLocation)
        } else {
            residues = sequenceService.getResiduesFromFeature(feature)
        }
        if (residues != null) {
            out.println(">" + feature.getUniqueName());
            int idx = 0;
            while (idx < residues.length()) {
                out.println(residues.substring(idx, Math.min(idx + lineLength, residues.length())));
                idx += lineLength;
            }
        }
    }

    void writeFastaForReferenceSequences(WriteObject writeObject, Collection<Sequence> sequences) {
        for (Sequence sequence : sequences) {
            writeFastaForReferenceSequence(writeObject, sequence)
        }
    }

    void writeFastaForReferenceSequence(WriteObject writeObject, Sequence sequence) {
        int lineLength = 60;
        String residues = null
        writeEmptyFastaDirective(writeObject.out);
        residues = sequenceService.getRawResiduesFromSequence(sequence, 0, sequence.length)
        if (residues != null) {
            writeObject.out.println(">" + sequence.name);
            int idx = 0;
            while (idx < residues.length()) {
                writeObject.out.println(residues.substring(idx, Math.min(idx + lineLength, residues.length())))
                idx += lineLength
            }
        }
    }

    void writeFastaForSequenceAlterations(WriteObject writeObject, Collection<? extends Feature> features) {
        for (Feature feature : features) {
            if (feature.instanceOf(SequenceAlterationArtifact.class)) {
                writeFastaForSequenceAlteration(writeObject, feature)
            }
        }
    }

    void writeFastaForSequenceAlteration(WriteObject writeObject, SequenceAlterationArtifact sequenceAlteration) {
        int lineLength = 60;
        String residues = null
        residues = sequenceAlteration.getAlterationResidue()
        if (residues != null) {
            writeObject.out.println(">" + sequenceAlteration.name)
            int idx = 0;
            while (idx < residues.length()) {
                writeObject.out.println(residues.substring(idx, Math.min(idx + lineLength, residues.length())))
                idx += lineLength
            }
        }
    }

    private Collection<GFF3Entry> convertToEntry(WriteObject writeObject, Feature feature, String source) {
        List<GFF3Entry> gffEntries = new ArrayList<GFF3Entry>();
        convertToEntry(writeObject, feature, source, gffEntries);
        return gffEntries;
    }

    private Collection<GFF3Entry> convertNeo4jTranscriptToEntry(WriteObject writeObject, def result, String source,Set<String> writtenGeneIds) {
        List<GFF3Entry> gffEntries = new ArrayList<GFF3Entry>();
        convertNeo4jTranscriptToEntry(writeObject, result, source, gffEntries,writtenGeneIds)
        return gffEntries;
    }

    private GFF3Entry calculateParentGFF3Entry(WriteObject writeObject, def neo4jEntry,String source,String seqId, def owners){
//        Feature feature = neo4jEntry.feature as Feature
        FeatureLocation featureLocation = neo4jEntry.location as FeatureLocation
        int start = featureLocation.getFmin()
        int end = featureLocation.fmax.equals(featureLocation.fmin) ? featureLocation.fmax + 1 : featureLocation.fmax
        String score = "."
        String strand;
        if (featureLocation.getStrand() == Strand.POSITIVE.getValue()) {
            strand = Strand.POSITIVE.getDisplay()
        } else if (featureLocation.getStrand() == Strand.NEGATIVE.getValue()) {
            strand = Strand.NEGATIVE.getDisplay()
        } else {
            strand = "."
        }
        String type = featureService.getCvTermFromNeo4jFeature(neo4jEntry.feature)

        String phase = ".";
        GFF3Entry gff3Entry = new GFF3Entry(seqId, source, type, start+1, end, score, strand, phase);
//        entry.setAttributes(extractAttributes(writeObject, feature));
        gff3Entry.setAttributes(extractNeo4jAttributes(writeObject, neo4jEntry.feature,null,owners));
        return gff3Entry
    }

    // NOTE: taking in list as we can have multiple gff3 entires created here because we split the CDS across exons
    private void calculateChildGFF3Entry(WriteObject writeObject, def childNeo4jEntry,def parentNeo4jEntry,String source,String seqId,Collection<GFF3Entry> gffEntries,def owners){
        Feature childFeature = childNeo4jEntry.feature as Feature
        FeatureLocation featureLocation = childNeo4jEntry.location as FeatureLocation
        log.debug "incoming feature location ${featureLocation as JSON}"
        int start = featureLocation.getFmin()
        int end = featureLocation.fmax.equals(featureLocation.fmin) ? featureLocation.fmax + 1 : featureLocation.fmax
        String score = "."
        String strand
        if (featureLocation.getStrand() == Strand.POSITIVE.getValue()) {
            strand = Strand.POSITIVE.getDisplay()
        } else if (featureLocation.getStrand() == Strand.NEGATIVE.getValue()) {
            strand = Strand.NEGATIVE.getDisplay()
        } else {
            strand = "."
        }
        String type = featureService.getCvTermFromNeo4jFeature(childNeo4jEntry.feature)
        log.debug "type: ${type}"


        if (type == "CDS") {
            log.debug "start ${start} and end ${end} for the CDS "
            // TODO: (1) get sorted exons 
            // TODO: (2) get CDS
            String exonQuery = "MATCH (n:CDS)--(t:Transcript)--(e:Exon)-[el]-(s:Sequence) where (n.uniqueName='${childFeature.uniqueName}' or n.id=${childFeature.id}) RETURN el "
            log.debug "output exon query: ${exonQuery}"
            log.debug "start / end ${start} / ${end}"
            def locationNodes = Feature.executeQuery(exonQuery)
            List<FeatureLocation> sortedFeatureLocationList = new ArrayList<>()
            locationNodes.each {
                sortedFeatureLocationList.add(it as FeatureLocation)
            }
            log.debug "output feature locations ${sortedFeatureLocationList} "
            sortedFeatureLocationList.sort(new Comparator<FeatureLocation>() {
                @Override
                int compare(FeatureLocation featureLocation1, FeatureLocation featureLocation2) {
                    int retVal
                    if (featureLocation1.fmin < featureLocation2.fmin) {
                        retVal = -1
                    } else if (featureLocation1.fmin > featureLocation2.fmin) {
                        retVal = 1
                    } else if (featureLocation1.fmax < featureLocation2.fmax) {
                        retVal = -1
                    } else if (featureLocation1.fmax > featureLocation2.fmax) {
                        retVal = 1
                    } else if (featureLocation1.calculateLength() != featureLocation2.calculateLength()) {
                        retVal = featureLocation1.calculateLength() < featureLocation2.calculateLength() ? -1 : 1
                    }
                    // overlapping perfectly, use strand to force consistent results
                    else {
                        retVal = featureLocation1.strand - featureLocation2.strand
                    }

//                    if (sortByStrand && featureLocation1.strand == -1) {
//                        retVal *= -1
//                    }
                    if (featureLocation1.strand == -1) {
                        retVal *= -1
                    }
                    return retVal
                }
            })
            int length = 0
            log.debug "sorted feature location list ${sortedFeatureLocationList as JSON} "
            for (FeatureLocation exonLocation : sortedFeatureLocationList) {
                log.debug "exon location ${exonLocation as JSON} overlaps ${start}, ${end}"
                if (!overlapperService.overlaps(exonLocation.fmin, exonLocation.fmax,start,  end)) {
                    log.debug "not overlapping ${exonLocation.fmin}, ${exonLocation.fmax}, ${start}, ${end}} ignoreing"
                    continue;
                }
                int fmin = exonLocation.fmin < start ? start : exonLocation.fmin
                int fmax = exonLocation.fmax > end ? end : exonLocation.fmax
                log.debug "does overlap so calculating ${fmin},${fmax}, ${exonLocation.strand}"
                String phase;
                if (length % 3 == 0) {
                    phase = "0";
                } else if (length % 3 == 1) {
                    phase = "2";
                } else {
                    phase = "1";
                }
                length += fmax - fmin;
                log.debug "adding for type: ${type}"
                GFF3Entry entry = new GFF3Entry(seqId, source, type, fmin+1 , fmax, score, strand, phase);
                entry.setAttributes(extractNeo4jAttributes(writeObject,childNeo4jEntry.feature,parentNeo4jEntry.feature,owners))
                gffEntries.add(entry);
            }
        }
        else {
            String phase = ".";
            GFF3Entry entry = new GFF3Entry(seqId, source, type, start+1, end, score, strand, phase);
            entry.setAttributes(extractNeo4jAttributes(writeObject, childNeo4jEntry.feature,parentNeo4jEntry.feature,owners))
            gffEntries.add(entry);
        }

    }

    private void convertNeo4jTranscriptToEntry(WriteObject writeObject, def result, String source, Collection<GFF3Entry> gffEntries,Set<String> writtenGeneIds) {

        //log.debug "converting feature to ${feature.name} entry of # of entries ${gffEntries.size()}"
        Sequence seq = result.sequence as Sequence
        String seqId = seq.name
        FeatureLocation featureLocation = result.location as FeatureLocation
        def owners = result.owners
        if(result.parent){
            // add a GFF3 entry for parent

            // get the ID of the parent to see if we've already written it
            String parentUniqueName = result.parent.feature.uniqueName
//            log.debug "parent unique name ${parentUniqueName}"
            if(!writtenGeneIds.contains(parentUniqueName)){
                gffEntries.add(calculateParentGFF3Entry(writeObject,result.parent,source,seqId,owners))
                writtenGeneIds.add(parentUniqueName)
            }
//            log.debug "output unqiue names ${writtenGeneIds.each { log.debug it }}"
        }

        String type = featureService.getCvTermFromNeo4jFeature(result.feature)
        int start = featureLocation.getFmin()
        int end = featureLocation.fmax.equals(featureLocation.fmin) ? featureLocation.fmax + 1 : featureLocation.fmax
        String score = "."
        String strand;
        if (featureLocation.getStrand() == Strand.POSITIVE.getValue()) {
            strand = Strand.POSITIVE.getDisplay()
        } else if (featureLocation.getStrand() == Strand.NEGATIVE.getValue()) {
            strand = Strand.NEGATIVE.getDisplay()
        } else {
            strand = "."
        }
        GFF3Entry entry = new GFF3Entry(seqId, source, type, start+1, end, score, strand);
        entry.setAttributes(extractNeo4jAttributes(writeObject, result.feature,result.parent ? result.parent.feature: null,owners))
        gffEntries.add(entry);
        def children = result.children
        if (children) {
            for (def childNode : children) {
//                FeatureLocation childFeatureLocation = childNode.location as FeatureLocation
                calculateChildGFF3Entry(writeObject,childNode,result,source,seqId,gffEntries,owners)
//                }
            }
        }

        // set owners for each node

        log.debug "output entries ${gffEntries.size()} -> ${gffEntries.toString()}"
    }

    private void convertToEntry(WriteObject writeObject, Feature feature, String source, Collection<GFF3Entry> gffEntries) {

        //log.debug "converting feature to ${feature.name} entry of # of entries ${gffEntries.size()}"

        String seqId = feature.featureLocation.to.name
        String type = featureService.getCvTermFromFeature(feature);
        int start = feature.getFmin() + 1;
        int end = feature.getFmax().equals(feature.getFmin()) ? feature.getFmax() + 1 : feature.getFmax();
        String score = ".";
        String strand;
        if (feature.getStrand() == Strand.POSITIVE.getValue()) {
            strand = Strand.POSITIVE.getDisplay()
        } else if (feature.getStrand() == Strand.NEGATIVE.getValue()) {
            strand = Strand.NEGATIVE.getDisplay()
        } else {
            strand = "."
        }
        String phase = ".";
        GFF3Entry entry = new GFF3Entry(seqId, source, type, start, end, score, strand, phase);
        entry.setAttributes(extractAttributes(writeObject, feature));
        gffEntries.add(entry);
        if (featureService.typeHasChildren(feature)) {
            for (Feature child : featureRelationshipService.getChildren(feature)) {
                if (child.instanceOf(CDS.class)) {
                    convertToEntry(writeObject, (CDS) child, source, gffEntries);
                } else {
                    convertToEntry(writeObject, child, source, gffEntries);
                }
            }
        }
    }


    private void convertToEntry(WriteObject writeObject, CDS cds, String source, Collection<GFF3Entry> gffEntries) {
        //log.debug "converting CDS to ${cds.name} entry of # of entries ${gffEntries.size()}"

        String seqId = cds.featureLocation.to.name
        String type = cds.cvTerm
        String score = ".";
        String strand;
        if (cds.getStrand() == 1) {
            strand = "+";
        } else if (cds.getStrand() == -1) {
            strand = "-";
        } else {
            strand = ".";
        }
        Transcript transcript = transcriptService.getParentTranscriptForFeature(cds)
        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        int length = 0;
        for (Exon exon : exons) {
            if (!overlapperService.overlaps(exon, cds)) {
                continue;
            }
            int fmin = exon.getFmin() < cds.getFmin() ? cds.getFmin() : exon.getFmin();
            int fmax = exon.getFmax() > cds.getFmax() ? cds.getFmax() : exon.getFmax();
            String phase;
            if (length % 3 == 0) {
                phase = "0";
            } else if (length % 3 == 1) {
                phase = "2";
            } else {
                phase = "1";
            }
            length += fmax - fmin;
            GFF3Entry entry = new GFF3Entry(seqId, source, type, fmin + 1, fmax, score, strand, phase);
            entry.setAttributes(extractAttributes(writeObject, cds));
            gffEntries.add(entry);
        }
        for (Feature child : featureRelationshipService.getChildren(cds)) {
            convertToEntry(writeObject, child, source, gffEntries);
        }
    }

    // TODO: make work
    private Map<String, String> extractNeo4jAttributes(WriteObject writeObject, def neo4jFeature,def neo4jParentFeature,def owners ) {
        Map<String, String> attributes = new HashMap<String, String>()
        Feature feature = neo4jFeature as Feature
        attributes.put(FeatureStringEnum.EXPORT_ID.value, encodeString(feature.getUniqueName()))
        if (feature.getName() != null && !isBlank(feature.getName()) && writeObject.attributesToExport.contains(FeatureStringEnum.NAME.value)) {
            attributes.put(FeatureStringEnum.EXPORT_NAME.value, encodeString(feature.getName()))
        }
        if (neo4jParentFeature!=null) {
            attributes.put(FeatureStringEnum.EXPORT_PARENT.value, encodeString(neo4jParentFeature.uniqueName as String))
        }
        String type = featureService.getCvTermFromNeo4jFeature(neo4jFeature)
        if (configWrapperService.exportSubFeatureAttrs() || type in (requestHandlingService.viewableAnnotationCvTermList + requestHandlingService.viewableAnnotationTranscriptCvTermList + requestHandlingService.viewableAlterationCvTermList)) {
            if (writeObject.attributesToExport.contains(FeatureStringEnum.SYNONYMS.value)) {
                Iterator<FeatureSynonym> synonymIter = feature.featureSynonyms.iterator()
                if (synonymIter.hasNext()) {
                    StringBuilder synonyms = new StringBuilder()
                    synonyms.append(synonymIter.next().synonym.name)
                    while (synonymIter.hasNext()) {
                        synonyms.append(",");
                        synonyms.append(encodeString(synonymIter.next().synonym.name))
                    }
                    attributes.put(FeatureStringEnum.EXPORT_ALIAS.value, synonyms.toString())
                }
            }


            //TODO: Target
            //TODO: Gap
            if (writeObject.attributesToExport.contains(FeatureStringEnum.COMMENTS.value)) {
                Iterator<Comment> commentIter = featurePropertyService.getComments(feature).iterator()
                if (commentIter.hasNext()) {
                    StringBuilder comments = new StringBuilder();
                    comments.append(encodeString(commentIter.next().value));
                    while (commentIter.hasNext()) {
                        comments.append(",");
                        comments.append(encodeString(commentIter.next().value));
                    }
                    attributes.put(FeatureStringEnum.EXPORT_NOTE.value, comments.toString());
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DBXREFS.value)) {
                Iterator<DBXref> dbxrefIter = feature.featureDBXrefs.iterator();
                if (dbxrefIter.hasNext()) {
                    StringBuilder dbxrefs = new StringBuilder();
                    DBXref dbxref = dbxrefIter.next();
                    dbxrefs.append(encodeString(dbxref.getDb().getName() + ":" + dbxref.getAccession()));
                    while (dbxrefIter.hasNext()) {
                        dbxrefs.append(",");
                        dbxref = dbxrefIter.next();
                        dbxrefs.append(encodeString(dbxref.getDb().getName()) + ":" + encodeString(dbxref.getAccession()));
                    }
                    attributes.put(FeatureStringEnum.EXPORT_DBXREF.value, dbxrefs.toString());
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DESCRIPTION.value) && feature.getDescription() != null && !isBlank(feature.getDescription())) {
                attributes.put(FeatureStringEnum.DESCRIPTION.value, encodeString(feature.getDescription()));
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.GO_ANNOTATIONS.value) && feature.goAnnotations) {
                String productString = goAnnotationService.convertGoAnnotationsToGff3String(feature.goAnnotations)
                attributes.put(FeatureStringEnum.GO_ANNOTATIONS.value, encodeString(productString))
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.PROVENANCE.value) && feature.provenances) {
                String productString = provenanceService.convertProvenancesToGff3String(feature.provenances)
                attributes.put(FeatureStringEnum.PROVENANCE.value, encodeString(productString))
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.GENE_PRODUCT.value) && feature.geneProducts) {
                String productString = geneProductService.convertGeneProductsToGff3String(feature.geneProducts)
                attributes.put(FeatureStringEnum.GENE_PRODUCT.value, encodeString(productString))
            }
            // TODO: ignore for now
//            if (writeObject.attributesToExport.contains(FeatureStringEnum.STATUS.value) && feature.getStatus() != null) {
//                attributes.put(FeatureStringEnum.STATUS.value, encodeString(feature.getStatus().value));
//            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.SYMBOL.value) && feature.getSymbol() != null && !isBlank(feature.getSymbol())) {
                attributes.put(FeatureStringEnum.SYMBOL.value, encodeString(feature.getSymbol()));
            }
            //TODO: Ontology_term
            //TODO: Is_circular
            Iterator<FeatureProperty> propertyIter = feature.featureProperties.iterator();
            if (writeObject.attributesToExport.contains(FeatureStringEnum.ATTRIBUTES.value)) {
                if (propertyIter.hasNext()) {
                    Map<String, StringBuilder> properties = new HashMap<String, StringBuilder>();
                    while (propertyIter.hasNext()) {
                        FeatureProperty prop = propertyIter.next();
                        if (prop.instanceOf(Comment.class)) {
                            // ignoring 'comment' as they are already processed earlier
                            continue
                        }
                        StringBuilder props = properties.get(prop.getTag());
                        if (props == null) {
                            if (prop.getTag() == null) {
                                // tag is null for generic properties
                                continue
                            }
                            props = new StringBuilder();
                            properties.put(prop.getTag(), props);
                        } else {
                            props.append(",");
                        }
                        props.append(encodeString(prop.getValue()));
                    }
                    for (Map.Entry<String, StringBuilder> iter : properties.entrySet()) {
                        if (iter.getKey() in unusedStandardAttributes) {
                            attributes.put(encodeString(WordUtils.capitalizeFully(iter.getKey())), iter.getValue().toString());
                        } else {
                            attributes.put(encodeString(WordUtils.uncapitalize(iter.getKey())), iter.getValue().toString());
                        }
                    }
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.OWNER.value) && owners!=null) {
                String ownersString = owners.collect { owner ->
                    encodeString(owner.username as String)
                }.join(",")
                attributes.put(FeatureStringEnum.OWNER.value.toLowerCase(), ownersString);
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DATE_CREATION.value)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(feature.dateCreated);
                attributes.put(FeatureStringEnum.DATE_CREATION.value, encodeString(formatDate(calendar.time)));
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DATE_LAST_MODIFIED.value)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(feature.lastUpdated);
                attributes.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, encodeString(formatDate(calendar.time)));
            }


            if (feature.class.name in [InsertionArtifact.class.name, SubstitutionArtifact.class.name]) {
                attributes.put(FeatureStringEnum.RESIDUES.value, feature.alterationResidue)
            }
        }
        return attributes;
    }

    private Map<String, String> extractAttributes(WriteObject writeObject, Feature feature) {
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FeatureStringEnum.EXPORT_ID.value, encodeString(feature.getUniqueName()));
        if (feature.getName() != null && !isBlank(feature.getName()) && writeObject.attributesToExport.contains(FeatureStringEnum.NAME.value)) {
            attributes.put(FeatureStringEnum.EXPORT_NAME.value, encodeString(feature.getName()));
        }
        if (!(feature.class.name in requestHandlingService.viewableAnnotationList + requestHandlingService.viewableAlterationList)) {
            def parent = featureRelationshipService.getParentForFeature(feature)
            attributes.put(FeatureStringEnum.EXPORT_PARENT.value, encodeString(parent.uniqueName));
        }
        if (configWrapperService.exportSubFeatureAttrs() || feature.class.name in requestHandlingService.viewableAnnotationList + requestHandlingService.viewableAnnotationTranscriptList + requestHandlingService.viewableAlterationList) {
            if (writeObject.attributesToExport.contains(FeatureStringEnum.SYNONYMS.value)) {
                Iterator<FeatureSynonym> synonymIter = feature.featureSynonyms.iterator();
                if (synonymIter.hasNext()) {
                    StringBuilder synonyms = new StringBuilder();
                    synonyms.append(synonymIter.next().synonym.name);
                    while (synonymIter.hasNext()) {
                        synonyms.append(",");
                        synonyms.append(encodeString(synonymIter.next().synonym.name));
                    }
                    attributes.put(FeatureStringEnum.EXPORT_ALIAS.value, synonyms.toString());
                }
            }


            //TODO: Target
            //TODO: Gap
            if (writeObject.attributesToExport.contains(FeatureStringEnum.COMMENTS.value)) {
                Iterator<Comment> commentIter = featurePropertyService.getComments(feature).iterator()
                if (commentIter.hasNext()) {
                    StringBuilder comments = new StringBuilder();
                    comments.append(encodeString(commentIter.next().value));
                    while (commentIter.hasNext()) {
                        comments.append(",");
                        comments.append(encodeString(commentIter.next().value));
                    }
                    attributes.put(FeatureStringEnum.EXPORT_NOTE.value, comments.toString());
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DBXREFS.value)) {
                Iterator<DBXref> dbxrefIter = feature.featureDBXrefs.iterator();
                if (dbxrefIter.hasNext()) {
                    StringBuilder dbxrefs = new StringBuilder();
                    DBXref dbxref = dbxrefIter.next();
                    dbxrefs.append(encodeString(dbxref.getDb().getName() + ":" + dbxref.getAccession()));
                    while (dbxrefIter.hasNext()) {
                        dbxrefs.append(",");
                        dbxref = dbxrefIter.next();
                        dbxrefs.append(encodeString(dbxref.getDb().getName()) + ":" + encodeString(dbxref.getAccession()));
                    }
                    attributes.put(FeatureStringEnum.EXPORT_DBXREF.value, dbxrefs.toString());
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DESCRIPTION.value) && feature.getDescription() != null && !isBlank(feature.getDescription())) {
                attributes.put(FeatureStringEnum.DESCRIPTION.value, encodeString(feature.getDescription()));
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.GO_ANNOTATIONS.value) && feature.goAnnotations) {
                String productString = goAnnotationService.convertGoAnnotationsToGff3String(feature.goAnnotations)
                attributes.put(FeatureStringEnum.GO_ANNOTATIONS.value, encodeString(productString))
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.PROVENANCE.value) && feature.provenances) {
                String productString = provenanceService.convertProvenancesToGff3String(feature.provenances)
                attributes.put(FeatureStringEnum.PROVENANCE.value, encodeString(productString))
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.GENE_PRODUCT.value) && feature.geneProducts) {
                String productString = geneProductService.convertGeneProductsToGff3String(feature.geneProducts)
                attributes.put(FeatureStringEnum.GENE_PRODUCT.value, encodeString(productString))
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.STATUS.value) && feature.getStatus() != null) {
                attributes.put(FeatureStringEnum.STATUS.value, encodeString(feature.getStatus().value));
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.SYMBOL.value) && feature.getSymbol() != null && !isBlank(feature.getSymbol())) {
                attributes.put(FeatureStringEnum.SYMBOL.value, encodeString(feature.getSymbol()));
            }
            //TODO: Ontology_term
            //TODO: Is_circular
            Iterator<FeatureProperty> propertyIter = feature.featureProperties.iterator();
            if (writeObject.attributesToExport.contains(FeatureStringEnum.ATTRIBUTES.value)) {
                if (propertyIter.hasNext()) {
                    Map<String, StringBuilder> properties = new HashMap<String, StringBuilder>();
                    while (propertyIter.hasNext()) {
                        FeatureProperty prop = propertyIter.next();
                        if (prop.instanceOf(Comment.class)) {
                            // ignoring 'comment' as they are already processed earlier
                            continue
                        }
                        StringBuilder props = properties.get(prop.getTag());
                        if (props == null) {
                            if (prop.getTag() == null) {
                                // tag is null for generic properties
                                continue
                            }
                            props = new StringBuilder();
                            properties.put(prop.getTag(), props);
                        } else {
                            props.append(",");
                        }
                        props.append(encodeString(prop.getValue()));
                    }
                    for (Map.Entry<String, StringBuilder> iter : properties.entrySet()) {
                        if (iter.getKey() in unusedStandardAttributes) {
                            attributes.put(encodeString(WordUtils.capitalizeFully(iter.getKey())), iter.getValue().toString());
                        } else {
                            attributes.put(encodeString(WordUtils.uncapitalize(iter.getKey())), iter.getValue().toString());
                        }
                    }
                }
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.OWNER.value) && feature.getOwner()) {
                String ownersString = feature.owners.collect { owner ->
                    encodeString(owner.username)
                }.join(",")
                // Note: how to do this using history directly, but only the top-level visible object gets annotated (e.g., the mRNA)
                // also, this is a separate query to the history table for each GFF3, so very slow
//                def owners = FeatureEvent.findAllByUniqueName(feature.uniqueName).editor.unique()
//                String ownersString = owners.collect{ owner ->
//                    encodeString(owner.username)
//                }.join(",")
                attributes.put(FeatureStringEnum.OWNER.value.toLowerCase(), ownersString);
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DATE_CREATION.value)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(feature.dateCreated);
                attributes.put(FeatureStringEnum.DATE_CREATION.value, encodeString(formatDate(calendar.time)));
            }
            if (writeObject.attributesToExport.contains(FeatureStringEnum.DATE_LAST_MODIFIED.value)) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(feature.lastUpdated);
                attributes.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, encodeString(formatDate(calendar.time)));
            }


            if (feature.class.name in [InsertionArtifact.class.name, SubstitutionArtifact.class.name]) {
                attributes.put(FeatureStringEnum.RESIDUES.value, feature.alterationResidue)
            }
        }
        return attributes;
    }

    String formatDate(Date date) {
        return gff3DateFormat.format(date)
    }

    static private String encodeString(String str) {
        return str ? str.replaceAll(",", "%2C").replaceAll("\n", "%0A").replaceAll("=", "%3D").replaceAll(";", "%3B").replaceAll("\t", "%09") : ""
    }


    enum Mode {
        READ,
        WRITE
    }

    enum Format {
        TEXT,
        GZIP
    }

    private boolean isBlank(String attributeValue) {
        return attributeValue == ""
    }

    private class WriteObject {
        File file;
        PrintWriter out;
        Mode mode;
        Set<String> attributesToExport = new HashSet<>();
        Format format;
    }

}
