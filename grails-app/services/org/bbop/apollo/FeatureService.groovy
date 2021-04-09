package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.bbop.apollo.alteration.SequenceAlterationInContext
import org.bbop.apollo.attributes.*
import org.bbop.apollo.cv.CV
import org.bbop.apollo.cv.CVTerm
import org.bbop.apollo.feature.*
import org.bbop.apollo.geneProduct.GeneProduct
import org.bbop.apollo.go.GoAnnotation
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.location.FeatureLocation
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.provenance.Provenance
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.sequence.SequenceTranslationHandler
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.sequence.TranslationTable
import org.bbop.apollo.user.User
import org.bbop.apollo.variant.*
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONException
import org.grails.web.json.JSONObject
import org.neo4j.driver.internal.InternalNode

@Transactional(readOnly = true)
class FeatureService {

    def nameService
    def configWrapperService
    def featureService
    def transcriptService
    def exonService
    def cdsService
    def nonCanonicalSplitSiteService
    def featureRelationshipService
    def featurePropertyService
    def sequenceService
    def permissionService
    def overlapperService
    def organismService
    def goAnnotationService
    def geneProductService
    def provenanceService

    final String MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE = "Manually associate transcript to gene"
    final String MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE = "Manually dissociate transcript from gene"
    final String MANUALLY_ASSOCIATE_FEATURE_TO_GENE = "Manually associate feature to gene"
    final String MANUALLY_DISSOCIATE_FEATURE_FROM_GENE = "Manually dissociate feature from gene"


    @Transactional
    FeatureLocation convertJSONToFeatureLocation(JSONObject jsonLocation, Sequence sequence, Feature feature, int defaultStrand = Strand.POSITIVE.value) throws JSONException {
        FeatureLocation featureLocation = new FeatureLocation()
        if (jsonLocation.has(FeatureStringEnum.ID.value)) {
            featureLocation.setId(jsonLocation.getLong(FeatureStringEnum.ID.value))
        }
        featureLocation.setFmin(jsonLocation.getInt(FeatureStringEnum.FMIN.value))
        featureLocation.setFmax(jsonLocation.getInt(FeatureStringEnum.FMAX.value))
        if (jsonLocation.getInt(FeatureStringEnum.STRAND.value) == Strand.POSITIVE.value || jsonLocation.getInt(FeatureStringEnum.STRAND.value) == Strand.NEGATIVE.value) {
            featureLocation.setStrand(jsonLocation.getInt(FeatureStringEnum.STRAND.value))
        } else {
            featureLocation.setStrand(defaultStrand)
        }
        featureLocation.to = sequence
        featureLocation.from = feature
        if (feature) {
            feature.featureLocation = featureLocation
        }
        return featureLocation;
    }

    /** Get features that overlap a given location.
     *
     * @param location - FeatureLocation that the features overlap
     * @param compareStrands - Whether to compare strands in overlap
     * @return Collection of Feature objects that overlap the FeatureLocation
     */
    Collection<Transcript> getOverlappingTranscripts(FeatureLocation location, boolean compareStrands = true) {
        List<Transcript> transcriptList = new ArrayList<>()
        List<Transcript> overlappingFeaturesList = getOverlappingFeatures(location, compareStrands)

        for (Feature eachFeature : overlappingFeaturesList) {
            Feature feature = Feature.get(eachFeature.id)
            if (feature.instanceOf(Transcript.class)) {
                transcriptList.add((Transcript) feature)
            }
        }

        return transcriptList
    }

    /** Get features that overlap a given location.
     *
     * @param location - FeatureLocation that the features overlap
     * @param compareStrands - Whether to compare strands in overlap
     * @return Collection of Feature objects that overlap the FeatureLocation
     */
    Collection<InternalNode> getOverlappingNeo4jFeatures(FeatureLocation location, boolean compareStrands = true) {

//    if (compareStrands) {
//      //Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and fl.strand = :strand and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax ))",[fmin:location.fmin,fmax:location.fmax,strand:location.strand,sequence:location.sequence])
//      Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and fl.strand = :strand and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax) or (fl.fmin >= :fmin and fl.fmax <= :fmax))", [fmin: location.fmin, fmax: location.fmax, strand: location.strand, sequence: location.sequence])
//        return []
        log.debug "input location ${location as JSON}"
        log.debug "compare strands ${compareStrands}"

//        Collection<Feature> features = (Collection<Feature>) Feature.createCriteria().listDistinct {
//            featureLocation {
//                eq "to", location.to
//                if (compareStrands) {
//                    eq "strand", location.strand
//                }
//                or {
//                    and {
//                        lte "fmin", location.fmin
//                        gt "fmax", location.fmax
//                    }
//                    and {
//                        lte "fmin", location.fmax
//                        gte "fmax", location.fmax
//                    }
//                    and {
//                        gte "fmin", location.fmax
//                        lte "fmax", location.fmax
//                    }
//                }
//            }
//        }
//        log.debug "output overlapping features ${features}"

        Organism organism = location.to.organism
        log.debug "organism ${organism}"
        log.debug "organism JSON ${organism as JSON}"

        String neo4jFeatureString = "MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence)-[fl:FEATURELOCATION]-(f:Feature)\n" +
            "WHERE (o.commonName='${organism.commonName}' or o.id = ${organism.id})" +
            (compareStrands ? " AND fl.strand = ${location.strand} " : "") +
            " AND ( (fl.fmin <= ${location.fmin} AND fl.fmax > ${location.fmax}) OR ( fl.fmin <= ${location.fmax} AND fl.fmax >=${location.fmax} ) OR ( fl.fmin >=${location.fmax} AND fl.fmax <= ${location.fmax} ) )" +
            "RETURN f"
        log.debug "neo4j String ${neo4jFeatureString}"
        return Feature.executeQuery(neo4jFeatureString)
//        log.debug "neo4j output features ${neo4jFeatures}"
//
//        List<Feature> features = new ArrayList<>()
//        neo4jFeatures.each {
//            features.add( it as Feature)
//        }
////        neo4jFeatures as List<Feature>
//        log.debug "output features  ${features}"
//
//        return features
//    } else {
//      //Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax ))",[fmin:location.fmin,fmax:location.fmax,sequence:location.sequence])
//      Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax) or (fl.fmin >= :fmin and fl.fmax <= :fmax))", [fmin: location.fmin, fmax: location.fmax, sequence: location.sequence])
//    }
    }

    /** Get features that overlap a given location.
     *
     * @param location - FeatureLocation that the features overlap
     * @param compareStrands - Whether to compare strands in overlap
     * @return Collection of Feature objects that overlap the FeatureLocation
     */
    Collection<Feature> getOverlappingFeatures(FeatureLocation location, boolean compareStrands = true) {

//    if (compareStrands) {
//      //Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and fl.strand = :strand and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax ))",[fmin:location.fmin,fmax:location.fmax,strand:location.strand,sequence:location.sequence])
//      Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and fl.strand = :strand and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax) or (fl.fmin >= :fmin and fl.fmax <= :fmax))", [fmin: location.fmin, fmax: location.fmax, strand: location.strand, sequence: location.sequence])
//        return []
        log.debug "input location ${location as JSON}"
        log.debug "compare strands ${compareStrands}"

//        Collection<Feature> features = (Collection<Feature>) Feature.createCriteria().listDistinct {
//            featureLocation {
//                eq "to", location.to
//                if (compareStrands) {
//                    eq "strand", location.strand
//                }
//                or {
//                    and {
//                        lte "fmin", location.fmin
//                        gt "fmax", location.fmax
//                    }
//                    and {
//                        lte "fmin", location.fmax
//                        gte "fmax", location.fmax
//                    }
//                    and {
//                        gte "fmin", location.fmax
//                        lte "fmax", location.fmax
//                    }
//                }
//            }
//        }
//        log.debug "output overlapping features ${features}"


        Organism organism = location.to.organism
//        String queryString = "MATCH (o:Organism)-[fl:FEATURELOCATION]-(s:Sequence) where fl = ${location} return o"
//        println "ORGANISM QUERY STRING ${queryString}"
//        Organism organism = Organism.executeQuery(queryString)[0] as Organism
//        Organism organism = Organism.executeQuery("MATCH (o:Organism)-[fl:FEATURELOCATION]-(s:Sequence) where fl = ${location} return o")[0] as Organism
        println "organism ${organism}"
        println "organism JSON ${organism as JSON}"

        String neo4jFeatureString = "MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence)-[fl:FEATURELOCATION]-(f:Feature)\n" +
            "WHERE (o.commonName='${organism.commonName}' or o.id = ${organism.id})" +
            (compareStrands ? " AND fl.strand = ${location.strand} " : "") +
            " AND ( (fl.fmin <= ${location.fmin} AND fl.fmax > ${location.fmax}) OR ( fl.fmin <= ${location.fmax} AND fl.fmax >=${location.fmax} ) OR ( fl.fmin >=${location.fmax} AND fl.fmax <= ${location.fmax} ) )" +
            "RETURN f"
        log.debug "neo4j String ${neo4jFeatureString}"
        def neo4jFeatures = Feature.executeQuery(neo4jFeatureString)
        log.debug "neo4j output features ${neo4jFeatures}"

        List<Feature> features = new ArrayList<>()
        neo4jFeatures.each {
            features.add(it as Feature)
        }
//        neo4jFeatures as List<Feature>
        log.debug "output features  ${features}"

        return features
//    } else {
//      //Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax ))",[fmin:location.fmin,fmax:location.fmax,sequence:location.sequence])
//      Feature.executeQuery("select distinct f from Feature f join f.featureLocations fl where fl.sequence = :sequence and ((fl.fmin <= :fmin and fl.fmax > :fmin) or (fl.fmin <= :fmax and fl.fmax >= :fmax) or (fl.fmin >= :fmin and fl.fmax <= :fmax))", [fmin: location.fmin, fmax: location.fmax, sequence: location.sequence])
//    }
    }

    /**
     * See if there is overlapping sequence alteration for a range
     *
     * Case 1:
     * - fmin / fmax on either side of alteration
     *
     * Case 2:
     * - fmin / fmax both within the alteration
     *
     * Case 3:
     * - fmin / fmax stradles min side of alteration
     *
     * Case 4:
     * - fmin / fmax stradles max side of alteration
     *
     * @param sequence
     * @param fmin Inclusive fmin
     * @param fmax Exclusive fmax
     * @return
     */
    def getOverlappingSequenceAlterations(Sequence sequence, int fmin, int fmax) {
        def alterations = Feature.executeQuery(
            "SELECT DISTINCT sa FROM SequenceAlterationArtifact sa JOIN sa.featureLocations fl WHERE fl.sequence = :sequence AND ((fl.fmin <= :fmin AND fl.fmax > :fmin) OR (fl.fmin <= :fmax AND fl.fmax >= :fmax) OR (fl.fmin >= :fmin AND fl.fmax <= :fmax))",
            [fmin: fmin, fmax: fmax, sequence: sequence]
        )

        if (alterations) {
            for (a in alterations) {
                log.debug "locations: ${a.fmin}->${a.fmax} for ${fmin}->${fmax}"
            }
        }


        return alterations
    }

    @Transactional
    void setSequenceForChildFeatures(Feature feature, Sequence sequence = null) {
        if (sequence) {
            FeatureLocation featureLocation = feature.featureLocation
            featureLocation.to = sequence
            featureLocation.save(flush: true)
        } else {
            FeatureLocation featureLocation = feature.featureLocation
            sequence = featureLocation.to
        }

        for (FeatureRelationship fr : feature.getParentFeatureRelationships()) {
            setSequenceForChildFeatures(fr.to, sequence);
        }
    }

    @Transactional
    def setOwner(Feature feature, User owner) {
        if (owner && feature) {
            log.debug "setting owner for feature ${feature} to ${owner}"
            feature.addToOwners(owner)
        } else {
            log.warn "user ${owner} or feature ${feature} is null so not setting"
        }
    }

    @Transactional
    def addOwnersByString(def username, Feature... features) {

        User owner = User.findByUsername(username as String)
        if (owner && features) {
            println "setting owner for feature ${features} to ${owner}"
//            features.each {
//                it.addToOwners(owner)
//            }
        } else {
            log.warn "user ${owner} or feature ${features} is null so not setting"
        }
    }

    /**
     * From Gene.addTranscript
     * @return
     */


    @Transactional
    def generateTranscript(JSONObject jsonTranscript, Sequence sequence, boolean suppressHistory, boolean useCDS, boolean useName = false) {
        log.debug "jsonTranscript: ${jsonTranscript.toString()}"
        Gene gene = jsonTranscript.has(FeatureStringEnum.PARENT_ID.value) ? (Gene) Feature.findByUniqueName(jsonTranscript.getString(FeatureStringEnum.PARENT_ID.value)) : null;
        Transcript transcript = null
        boolean readThroughStopCodon = false

        User owner = permissionService.getCurrentUser(jsonTranscript)
        // if the gene is set, then don't process, just set the transcript for the found gene
        if (gene) {
            // Scenario I - if 'parent_id' attribute is given then find the gene
            transcript = (Transcript) convertJSONToFeature(jsonTranscript, sequence);
            if (transcript.getFmin() < 0 || transcript.getFmax() < 0) {
                throw new AnnotationException("Feature cannot have negative coordinates")
            }

            setOwner(transcript, owner);

            CDS cds = transcriptService.getCDS(transcript)
            log.debug "had a CDS ${cds}"
            if (cds) {
                readThroughStopCodon = cdsService.getStopCodonReadThrough(cds) ? true : false
            }

            if (!useCDS || cds == null) {
                log.debug "generating CDS ${cds}"
                calculateCDS(transcript, readThroughStopCodon)
                cds = transcriptService.getCDS(transcript)
                log.debug "generatED CDS ${cds}"
                log.debug "generatED CDS locaiton ${cds.featureLocation as JSON}"
            } else {
                log.debug "not generated a CDS for some reason "
                log.debug "inferred CDS locaiton ${cds.featureLocation as JSON}"
                // if there are any sequence alterations that overlaps this transcript then
                // recalculate the CDS to account for these changes
                def sequenceAlterations = getSequenceAlterationsForFeature(transcript)
                if (sequenceAlterations.size() > 0) {
                    calculateCDS(transcript)
                }
            }

            addTranscriptToGene(gene, transcript);
            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
            if (!suppressHistory) {
                transcript.name = nameService.generateUniqueName(transcript)
            }
            // setting back the original name for transcript
            if (useName && jsonTranscript.has(FeatureStringEnum.NAME.value)) {
                transcript.name = jsonTranscript.get(FeatureStringEnum.NAME.value)
            }
        } else {

            boolean createGeneFromJSON = false
            if (jsonTranscript.containsKey(FeatureStringEnum.PARENT.value) && jsonTranscript.containsKey(FeatureStringEnum.PROPERTIES.value)) {
                for (def property : jsonTranscript.getJSONArray(FeatureStringEnum.PROPERTIES.value)) {
                    if (property.containsKey(FeatureStringEnum.NAME.value)
                        && property.get(FeatureStringEnum.NAME.value) == FeatureStringEnum.COMMENT.value
                        && property.get(FeatureStringEnum.VALUE.value) == MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE) {
                        createGeneFromJSON = true
                        break
                    }
                }
            }

            log.debug "creating genes from JSON ${createGeneFromJSON}"

            if (!createGeneFromJSON) {
                log.debug "Gene from parent_id doesn't exist; trying to find overlapping isoform"
                // Scenario II - find an overlapping isoform and if present, add current transcript to its gene
                FeatureLocation featureLocation = convertJSONToFeatureLocation(jsonTranscript.getJSONObject(FeatureStringEnum.LOCATION.value), sequence, null)
//                Collection<Feature> overlappingFeatures = getOverlappingFeatures(featureLocation).findAll() {
                log.debug "input JSON ${jsonTranscript as JSON}"
                log.debug "input JSON location ${featureLocation as JSON}"
                Collection<InternalNode> overlappingFeatures = getOverlappingNeo4jFeatures(featureLocation).findAll() {
                    log.debug "it as ${it}"
                    String type = getCvTermFromNeo4jFeature(it)
                    log.debug "type = ${type}"
//                    it = Feature.get(it.id)
//                    it instanceof Gene
                    return type == 'gene'
                }

                log.debug "number of overlapping genes ${overlappingFeatures?.size()}"
                log.debug "overlapping genes: ${overlappingFeatures.name}"
                List<Gene> overlappingFeaturesToCheck = new ArrayList<Gene>()
                try {
                    overlappingFeatures.each {
                        Gene eachGene = it as Gene
                        if (!checkForComment(eachGene, MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE) && !checkForComment(eachGene, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)) {
                            overlappingFeaturesToCheck.add(eachGene)
                        }
                    }
                } catch (e) {
                    log.error e
                }

//                log.debug "overlapping features to check: ${overlappingFeaturesToCheck?.size()} -> ${overlappingFeaturesToCheck}"

                for (Gene eachFeature : overlappingFeaturesToCheck) {
                    // get the proper object instead of its proxy, due to lazy loading
//                    Feature feature = eachFeature
                    log.debug "evaluating overlap of feature ${eachFeature.name} of class ${eachFeature.class.name} and gene: ${gene}"

//                    if (!gene && feature instanceof Gene && !(feature instanceof Pseudogene)) {
                    // TODO: note that instanteof casts it to a Non-Canonical prime, etc., should use type instead
//                    if (!gene) {
//                    if (!gene && feature.instanceOf(Gene.class) && !feature.instanceOf(Pseudogene.class)) {
                    if (!gene) {
                        Gene tmpGene = (Gene) Gene.findById(eachFeature.id);
                        log.debug "found an overlapping gene ${tmpGene} . . and type ${tmpGene.class.name}"
                        // removing name from transcript JSON since its naming will be based off of the overlapping gene
                        Transcript tmpTranscript
                        if (jsonTranscript.has(FeatureStringEnum.NAME.value)) {
                            String originalName = jsonTranscript.get(FeatureStringEnum.NAME.value)
                            jsonTranscript.remove(FeatureStringEnum.NAME.value)
                            tmpTranscript = (Transcript) convertJSONToFeature(jsonTranscript, sequence);
                            jsonTranscript.put(FeatureStringEnum.NAME.value, originalName)
                            setSequenceForChildFeatures(tmpTranscript)
                        } else {
                            tmpTranscript = (Transcript) convertJSONToFeature(jsonTranscript, sequence);
                            setSequenceForChildFeatures(tmpTranscript)
                        }

                        if (tmpTranscript.getFmin() < 0 || tmpTranscript.getFmax() < 0) {
                            throw new AnnotationException("Feature cannot have negative coordinates");
                        }

                        //this one is working, but was marked as needing improvement
                        setOwner(tmpTranscript, owner);

                        CDS cds = transcriptService.getCDS(tmpTranscript)
                        if (cds) {
                            log.debug "HAS A CDS: ${cds}"
                            readThroughStopCodon = cdsService.getStopCodonReadThrough(cds) ? true : false
                        }

                        if (!useCDS || cds == null) {
                            log.debug "CDS is null: ${cds} or useCDS is false ${useCDS}"
                            calculateCDS(tmpTranscript, readThroughStopCodon)
                            log.debug "CDS is CALCULATED: ${cds} "
                        } else {
                            // if there are any sequence alterations that overlaps this transcript then
                            // recalculate the CDS to account for these changes
                            def sequenceAlterations = getSequenceAlterationsForFeature(tmpTranscript)
                            if (sequenceAlterations.size() > 0) {
                                calculateCDS(tmpTranscript)
                            }
                        }

//                        log.debug "output CDS: ${tmpTranscript.childFeatureRelationships}"
                        CDS foundCDS = transcriptService.getCDS(tmpTranscript)
                        log.debug "final found CDS ${foundCDS}"
                        log.debug "final found CDS location ${foundCDS.featureLocation}"

                        if (!suppressHistory) {
                            tmpTranscript.name = nameService.generateUniqueName(tmpTranscript, tmpGene?.name)
                        }

                        // setting back the original name for transcript
                        if (useName && jsonTranscript.has(FeatureStringEnum.NAME.value)) {
                            tmpTranscript.name = jsonTranscript.get(FeatureStringEnum.NAME.value)
                        }

                        if (tmpTranscript && tmpGene && overlapperService.overlaps(tmpTranscript, tmpGene)) {
                            log.debug "There is an overlap, adding to an existing gene"
                            transcript = tmpTranscript;
                            gene = tmpGene;
                            addTranscriptToGene(gene, transcript)
                            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
                            transcript.save()

                            if (jsonTranscript.has(FeatureStringEnum.PARENT.value)) {
                                // use metadata of incoming transcript's gene
                                JSONObject jsonGene = jsonTranscript.getJSONObject(FeatureStringEnum.PARENT.value)
                                if (jsonGene.has(FeatureStringEnum.DBXREFS.value)) {
                                    // parse dbxrefs
                                    JSONArray dbxrefs = jsonGene.getJSONArray(FeatureStringEnum.DBXREFS.value)
                                    for (JSONObject dbxref : dbxrefs) {
                                        String dbString = dbxref.get(FeatureStringEnum.DB.value).name
                                        String accessionString = dbxref.get(FeatureStringEnum.ACCESSION.value)
                                        // TODO: needs improvement
                                        boolean exists = false
                                        tmpGene.featureDBXrefs.each {
                                            if (it.db.name == dbString && it.accession == accessionString) {
                                                exists = true
                                            }
                                        }
                                        if (!exists) {
                                            addNonPrimaryDbxrefs(tmpGene, dbString, accessionString)
                                        }
                                    }
                                }
                                tmpGene.save()

                                if (jsonGene.has(FeatureStringEnum.PROPERTIES.value)) {
                                    // parse properties
                                    JSONArray featureProperties = jsonGene.getJSONArray(FeatureStringEnum.PROPERTIES.value)
                                    for (JSONObject featureProperty : featureProperties) {
                                        String tagString = featureProperty.get(FeatureStringEnum.TYPE.value).name
                                        String valueString = featureProperty.get(FeatureStringEnum.VALUE.value)
                                        // TODO: needs improvement
                                        boolean exists = false
                                        tmpGene.featureProperties.each {
                                            if (it.instanceOf(Comment.class)) {
                                                exists = true
                                            } else if (it.tag == tagString && it.value == valueString) {
                                                exists = true
                                            }
                                        }
                                        if (!exists) {
                                            if (tagString == FeatureStringEnum.COMMENT.value) {
                                                // if FeatureProperty is a comment
                                                featurePropertyService.addComment(tmpGene, valueString)
                                            } else {
                                                addNonReservedProperties(tmpGene, tagString, valueString)
                                            }
                                        }
                                    }
                                }
                                tmpGene.save()
                            }
                            gene.save(insert: false, flush: true)
                            break;
                        } else {
                            featureRelationshipService.deleteFeatureAndChildren(tmpTranscript)
                            log.debug "There is no overlap, we are going to return a NULL gene and a NULL transcript "
                        }
                    } else {
                        log.error "Feature is not an instance of a gene or is a pseudogene"
                    }
                }
            }
        }
        if (gene == null) {
            log.debug "gene is null"
            // Scenario III - create a de-novo gene
            JSONObject jsonGene = new JSONObject();
            if (jsonTranscript.has(FeatureStringEnum.PARENT.value)) {
                // Scenario IIIa - use the 'parent' attribute, if provided, from transcript JSON
                jsonGene = JSON.parse(jsonTranscript.getString(FeatureStringEnum.PARENT.value)) as JSONObject
                jsonGene.put(FeatureStringEnum.CHILDREN.value, new JSONArray().put(jsonTranscript))
            } else {
                // Scenario IIIb - use the current mRNA's featurelocation for gene
                jsonGene.put(FeatureStringEnum.CHILDREN.value, new JSONArray().put(jsonTranscript));
                jsonGene.put(FeatureStringEnum.LOCATION.value, jsonTranscript.getJSONObject(FeatureStringEnum.LOCATION.value));
                String cvTermString = FeatureStringEnum.GENE.value
                jsonGene.put(FeatureStringEnum.TYPE.value, convertCVTermToJSON(FeatureStringEnum.CV.value, cvTermString));
            }

            String geneName = null
            if (jsonGene.has(FeatureStringEnum.NAME.value)) {
                geneName = jsonGene.get(FeatureStringEnum.NAME.value)
            } else if (jsonTranscript.has(FeatureStringEnum.NAME.value)) {
                geneName = jsonTranscript.getString(FeatureStringEnum.NAME.value)
            } else {
//                geneName = nameService.makeUniqueFeatureName(sequence.organism, sequence.name, new LetterPaddingStrategy(), false)
                geneName = nameService.makeUniqueGeneName(sequence.organism, sequence.name, false)
            }
            if (!suppressHistory) {
//                geneName = nameService.makeUniqueFeatureName(sequence.organism, geneName, new LetterPaddingStrategy(), true)
                geneName = nameService.makeUniqueGeneName(sequence.organism, geneName, true)
            }
            // set back to the original gene name
            if (jsonTranscript.has(FeatureStringEnum.GENE_NAME.value)) {
                geneName = jsonTranscript.getString(FeatureStringEnum.GENE_NAME.value)
            }
            jsonGene.put(FeatureStringEnum.NAME.value, geneName)

            gene = (Gene) convertJSONToFeature(jsonGene, sequence);
            setSequenceForChildFeatures(gene, sequence);


            if (gene.getFmin() < 0 || gene.getFmax() < 0) {
                throw new AnnotationException("Feature cannot have negative coordinates");
            }
            transcript = transcriptService.getTranscripts(gene).iterator().next();
            CDS cds = transcriptService.getCDS(transcript)
            if (cds) {
                readThroughStopCodon = cdsService.getStopCodonReadThrough(cds) ? true : false
            }

            if (!useCDS || cds == null) {
                log.debug "no gene, CALCULATING CDS"
                calculateCDS(transcript, readThroughStopCodon)
                CDS calculatedCDS = transcriptService.getCDS(transcript)
                println "final CDS ${calculatedCDS}"
                println "final CDS location ${calculatedCDS.featureLocation as JSON}"
                calculatedCDS.save(flush: true)
                calculatedCDS.featureLocation.save(flush: true)
            } else {
                // if there are any sequence alterations that overlaps this transcript then
                // recalculate the CDS to account for these changes
                def sequenceAlterations = getSequenceAlterationsForFeature(transcript)
                if (sequenceAlterations.size() > 0) {
                    calculateCDS(transcript)
                }
            }
            removeExonOverlapsAndAdjacenciesForFeature(gene)
            if (!suppressHistory) {
                transcript.name = nameService.generateUniqueName(transcript)
            }
            // set back the original transcript name
            if (useName && jsonTranscript.has(FeatureStringEnum.NAME.value)) {
                transcript.name = jsonTranscript.getString(FeatureStringEnum.NAME.value)
            }

            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript);
            gene.save(flush: true)
            transcript.save(flush: true)

            // doesn't work well for testing
            setOwner(gene, owner);
            setOwner(transcript, owner);
        }
        return transcript;
    }

// TODO: this is kind of a hack for now
    JSONObject convertCVTermToJSON(String cv, String cvTerm) {
        JSONObject jsonCVTerm = new JSONObject();
        JSONObject jsonCV = new JSONObject();
        jsonCVTerm.put(FeatureStringEnum.CV.value, jsonCV);
        jsonCV.put(FeatureStringEnum.NAME.value, cv);
        jsonCVTerm.put(FeatureStringEnum.NAME.value, cvTerm);
        return jsonCVTerm;
    }

    /**
     * TODO: Should be the same result as the older method, need to check:
     *
     *         if (transcript.getGene() != null) {return transcript.getGene();}return transcript;
     * @param feature
     * @return
     */

    Feature getTopLevelFeature(Feature feature) {
        Collection<Feature> parents = feature?.childFeatureRelationships*.from
        if (parents) {
            return getTopLevelFeature(parents.iterator().next());
        } else {
            return feature;
        }
    }


    @Transactional
    def removeExonOverlapsAndAdjacenciesForFeature(Feature feature) {
        if (feature.instanceOf(Gene.class)) {
            for (Transcript transcript : transcriptService.getTranscripts((Gene) feature)) {
                removeExonOverlapsAndAdjacencies(transcript);
            }
        } else if (feature.instanceOf(Transcript.class)) {
            removeExonOverlapsAndAdjacencies((Transcript) feature);
        }
    }

    @Transactional
    def addTranscriptToGene(Gene gene, Transcript transcript) {
        removeExonOverlapsAndAdjacencies(transcript);
        // no feature location, set location to transcript's
        if (gene.featureLocation == null) {
            FeatureLocation transcriptFeatureLocation = transcript.featureLocation
            FeatureLocation featureLocation = new FeatureLocation()
//            featureLocation.properties = transcriptFeatureLocation.properties
            featureLocation.strand = transcriptFeatureLocation.strand
            featureLocation.fmin = transcriptFeatureLocation.fmin
            featureLocation.fmax = transcriptFeatureLocation.fmax
//            featureLocation.id = null
            featureLocation.to = transcript.featureLocation.to
            featureLocation.from = gene
            featureLocation.save(flush: true)
            gene.featureLocation = featureLocation
            gene.save(flush: true)
//            gene.addToFeatureLocations(featureLocation);
        } else {
            // if the transcript's bounds are beyond the gene's bounds, need to adjust the gene's bounds
            if (transcript.getFeatureLocation().getFmin() < gene.getFeatureLocation().getFmin()) {
                gene.featureLocation.setFmin(transcript.featureLocation.fmin);
            }
            if (transcript.getFeatureLocation().getFmax() > gene.getFeatureLocation().getFmax()) {
                gene.featureLocation.setFmax(transcript.featureLocation.fmax);
            }
        }

        // add transcript
        FeatureRelationship featureRelationship = new FeatureRelationship(
            from: gene
            , to: transcript
        ).save(failOnError: true, flush: true)
        gene.addToParentFeatureRelationships(featureRelationship)
        transcript.addToChildFeatureRelationships(featureRelationship)


        updateGeneBoundaries(gene)

        gene.save(flush: true)
        transcript.save(flush: true)

//        getSession().indexFeature(transcript);

        // event fire
//        TODO: determine event model?
//        fireAnnotationChangeEvent(transcript, gene, AnnotationChangeEvent.Operation.ADD);
    }

    /**
     * TODO:  this is an N^2  search of overlapping exons
     * @param transcript
     * @return
     */
    @Transactional
    def removeExonOverlapsAndAdjacencies(Transcript transcript) throws AnnotationException {
        List<Exon> sortedExons = transcriptService.getSortedExons(transcript, false)
        if (!sortedExons || sortedExons?.size() <= 1) {
            return;
        }
        Collections.sort(sortedExons, new FeaturePositionComparator<Exon>(false))
        log.debug "initial number of exons ${sortedExons.size()}"
        int inc = 1;
        for (int i = 0; i < sortedExons.size() - 1; i += inc) {
            inc = 1;
            Exon leftExon = sortedExons.get(i);
            for (int j = i + 1; j < sortedExons.size(); ++j) {
                Exon rightExon = sortedExons.get(j);
                if (overlapperService.overlaps(leftExon, rightExon) || isAdjacentTo(leftExon.getFeatureLocation(), rightExon.getFeatureLocation())) {
                    try {
                        exonService.mergeExons(leftExon, rightExon);
                        sortedExons = transcriptService.getSortedExons(transcript, false)
                        log.debug "merging exons -> ${sortedExons.size()}"
                        // we have to reload the sortedExons again and start over
                        ++inc;
                    } catch (AnnotationException e) {
                        // we should probably just re-throw this
                        log.error(e.toString())
                        throw e
                    }
                }
            }
        }
    }

/** Checks whether this AbstractSimpleLocationBioFeature is adjacent to the FeatureLocation.
 *
 * @param location - FeatureLocation to check adjacency against
 * @return true if there is adjacency
 */
    boolean isAdjacentTo(FeatureLocation leftLocation, FeatureLocation location) {
        return isAdjacentTo(leftLocation, location, true);
    }

    boolean isAdjacentTo(FeatureLocation leftFeatureLocation, FeatureLocation rightFeatureLocation, boolean compareStrands) {
        if (leftFeatureLocation.sequence != rightFeatureLocation.sequence) {
            return false;
        }
        int thisFmin = leftFeatureLocation.getFmin();
        int thisFmax = leftFeatureLocation.getFmax();
        int thisStrand = leftFeatureLocation.getStrand();
        int otherFmin = rightFeatureLocation.getFmin();
        int otherFmax = rightFeatureLocation.getFmax();
        int otherStrand = rightFeatureLocation.getStrand();
        boolean strandsOverlap = compareStrands ? thisStrand == otherStrand : true;
        if (strandsOverlap &&
            (thisFmax == otherFmin ||
                thisFmin == otherFmax)) {
            return true;
        }
        return false;
    }


    @Transactional
    def calculateCDS(Transcript transcript) {
        // NOTE: isPseudogene call seemed redundant with isProtenCoding
        CDS cds = transcriptService.getCDS(transcript)
        calculateCDS(transcript, cdsService.hasStopCodonReadThrough(cds))
//        if (transcriptService.isProteinCoding(transcript) && (transcriptService.getGene(transcript) == null)) {
////            calculateCDS(editor, transcript, transcript.getCDS() != null ? transcript.getCDS().getStopCodonReadThrough() != null : false);
////            calculateCDS(transcript, transcript.getCDS() != null ? transcript.getCDS().getStopCodonReadThrough() != null : false);
//            calculateCDS(transcript, transcriptService.getCDS(transcript) != null ? transcriptService.getStopCodonReadThrough(transcript) != null : false);
//        }
    }


    @Transactional
    def calculateCDS(Transcript transcript, boolean readThroughStopCodon) {
        println "calculating CDS"
        CDS cds = transcriptService.getCDS(transcript);
        println "got CDS ${cds} from transcript ${transcript} ${readThroughStopCodon}"
        if (cds == null) {
            println "cds is null, so calculating longest ORF, ${transcript as JSON} , ${readThroughStopCodon}"
            setLongestORF(transcript, readThroughStopCodon);
            return;
        }
        boolean manuallySetStart = cdsService.isManuallySetTranslationStart(cds);
        boolean manuallySetEnd = cdsService.isManuallySetTranslationEnd(cds);
        println "manually start and end ${manuallySetStart} ${manuallySetEnd}"
        if (manuallySetStart && manuallySetEnd) {
            return;
        }
        FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
        if (!manuallySetStart && !manuallySetEnd) {
            println "no manual start ot end"
            setLongestORF(transcript, readThroughStopCodon);
        } else if (manuallySetStart) {
            setTranslationStart(transcript, cdsFeatureLocation.strand.equals(-1) ? cdsFeatureLocation.fmax - 1 : cds.fmin, true, readThroughStopCodon)
        } else {
            setTranslationEnd(transcript, cdsFeatureLocation.strand.equals(-1) ? cds.fmin : cdsFeatureLocation.fmax - 1, true)
        }
    }

/**
 * Calculate the longest ORF for a transcript.  If a valid start codon is not found, allow for partial CDS start/end.
 * Calls setLongestORF(Transcript, TranslationTable, boolean) with the translation table and whether partial
 * ORF calculation extensions are allowed from the configuration associated with this editor.
 *
 * @param transcript - Transcript to set the longest ORF to
 */
    @Transactional
    void setLongestORF(Transcript transcript) {
        log.debug "setLongestORF(transcript) ${transcript}"
        setLongestORF(transcript, false);
    }

/**
 * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationStart - Coordinate of the start of translation
 */
    @Transactional
    void setTranslationStart(Transcript transcript, int translationStart) {
        log.debug "setTranslationStart"
        setTranslationStart(transcript, translationStart, false);
    }

/**
 * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationStart - Coordinate of the start of translation
 * @param setTranslationEnd - if set to true, will search for the nearest in frame stop codon
 */
    @Transactional
    void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd) throws AnnotationException {
        log.debug "setTranslationStart(transcript,translationStart,translationEnd)"
        setTranslationStart(transcript, translationStart, setTranslationEnd, false);
    }

/**
 * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationStart - Coordinate of the start of translation
 * @param setTranslationEnd - if set to true, will search for the nearest in frame stop codon
 * @param readThroughStopCodon - if set to true, will read through the first stop codon to the next
 */
    @Transactional
    void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd, boolean readThroughStopCodon) throws AnnotationException {
        log.debug "setTranslationStart(transcript,translationStart,translationEnd,readThroughStopCodon)"
        setTranslationStart(transcript, translationStart, setTranslationEnd, setTranslationEnd ? organismService.getTranslationTable(transcript.featureLocation.to.organism) : null, readThroughStopCodon);
    }

    /** Convert local coordinate to source feature coordinate.
     *
     * @param localCoordinate - Coordinate to convert to source coordinate
     * @return Source feature coordinate, -1 if local coordinate is longer than feature's length or negative
     */
    int convertLocalCoordinateToSourceCoordinate(Feature feature, int localCoordinate) {
        log.debug "convertLocalCoordinateToSourceCoordinate"

        if (localCoordinate < 0 || localCoordinate > feature.getLength()) {
            return -1;
        }
        if (feature.getFeatureLocation().getStrand() == -1) {
            return feature.getFeatureLocation().getFmax() - localCoordinate - 1;
        } else {
            return feature.getFeatureLocation().getFmin() + localCoordinate;
        }
    }

    int convertLocalCoordinateToSourceCoordinateForTranscript(Transcript transcript, int localCoordinate) {
        // Method converts localCoordinate to sourceCoordinate in reference to the Transcript
        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        int sourceCoordinate = -1;
        if (exons.size() == 0) {
            return convertLocalCoordinateToSourceCoordinate(transcript, localCoordinate);
        }
        int currentLength = 0;
        int currentCoordinate = localCoordinate;
        for (Exon exon : exons) {
            println "exon ${exon}"
//            println "feature location ${exon.featureLocation}"
            FeatureLocation exonFeatureLocation = FeatureLocation.findByFrom(exon)
            FeatureLocation transcriptFeatureLocation = FeatureLocation.findByFrom(transcript)
            println "exon feature location ${exonFeatureLocation}"
            println "exon feature location length ${exonFeatureLocation.calculateLength()}"

            int exonLength = exonFeatureLocation.calculateLength()
            if (currentLength + exonLength >= localCoordinate) {
                if (transcriptFeatureLocation.getStrand() == Strand.NEGATIVE.value) {
                    sourceCoordinate = exonFeatureLocation.fmax - currentCoordinate - 1;
                } else {
                    sourceCoordinate = exonFeatureLocation.fmin + currentCoordinate;
                }
                break;
            }
            currentLength += exonLength;
            currentCoordinate -= exonLength;
        }
        return sourceCoordinate;
    }

    int convertLocalCoordinateToSourceCoordinateForCDS(CDS cds, int localCoordinate) {
        // Method converts localCoordinate to sourceCoordinate in reference to the CDS
        Transcript transcript = transcriptService.getTranscript(cds)
        FeatureLocation transcriptFeatureLocation = FeatureLocation.findByFrom(transcript)
        FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
        if (!transcript) {
            return convertLocalCoordinateToSourceCoordinate(cds, localCoordinate);
        }
        int offset = 0;
        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        if (exons.size() == 0) {
            log.debug "FS::convertLocalCoordinateToSourceCoordinateForCDS() - No exons for given transcript"
            return convertLocalCoordinateToSourceCoordinate(cds, localCoordinate)
        }
        if (transcriptFeatureLocation.strand == Strand.NEGATIVE.value) {
            exons.reverse()
        }
        for (Exon exon : exons) {
            FeatureLocation exonFeatureLocation = FeatureLocation.findByFrom(exon)
            if (!overlapperService.overlaps(cds, exon)) {
                offset += exonFeatureLocation.calculateLength();
                continue;
            } else if (overlapperService.overlaps(cds, exon)) {
                if (exonFeatureLocation.fmin >= cdsFeatureLocation.fmin && exonFeatureLocation.fmax <= cdsFeatureLocation.fmax) {
                    // exon falls within the boundaries of the CDS
                    continue
                } else {
                    // exon doesn't overlap completely with the CDS
                    if (exonFeatureLocation.fmin < cdsFeatureLocation.fmin && exonFeatureLocation.strand == Strand.POSITIVE.value) {
                        offset += cdsFeatureLocation.fmin - exonFeatureLocation.fmin
                    } else if (exonFeatureLocation.fmax > cdsFeatureLocation.fmax && exonFeatureLocation.strand == Strand.NEGATIVE.value) {
                        offset += exonFeatureLocation.fmax - cdsFeatureLocation.fmax
                    }
                }
            }

            if (exonFeatureLocation.getStrand() == Strand.NEGATIVE.value) {
                offset += exonFeatureLocation.getFmax() - exonFeatureLocation.getFmax();
            } else {
                offset += exonFeatureLocation.getFmin() - exonFeatureLocation.getFmin();
            }
            break;
        }
        return convertLocalCoordinateToSourceCoordinateForTranscript(transcript, localCoordinate + offset);
    }

/**
 * Set the translation start in the transcript.  Sets the translation start in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationStart - Coordinate of the start of translation
 * @param setTranslationEnd - if set to true, will search for the nearest in frame stop codon
 * @param translationTable - Translation table that defines the codon translation
 * @param readThroughStopCodon - if set to true, will read through the first stop codon to the next
 */
    @Transactional
    void setTranslationStart(Transcript transcript, int translationStart, boolean setTranslationEnd, TranslationTable translationTable, boolean readThroughStopCodon) throws AnnotationException {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            featureRelationshipService.addChildFeature(transcript, cds)
//            transcript.setCDS(cds);
        }
        // if the end is set, then we make sure we are going to set a legal coordinate
        if (cdsService.isManuallySetTranslationEnd(cds)) {
            if (transcript.strand == Strand.NEGATIVE.value) {
                if (translationStart <= cds.featureLocation.fmin) {
                    throw new AnnotationException("Translation start ${translationStart} must be upstream of the end ${cds.featureLocation.fmin} (larger)")
                }
            } else {
                if (translationStart >= cds.featureLocation.fmax) {
                    throw new AnnotationException("Translation start ${translationStart} must be upstream of the end ${cds.featureLocation.fmax} (smaller) ")
                }
            }
        }
        FeatureLocation transcriptFeatureLocation = FeatureLocation.findByFeature(transcript)
        if (transcriptFeatureLocation.strand == Strand.NEGATIVE.value) {
            setFmax(cds, translationStart + 1)
        } else {
            setFmin(cds, translationStart)
        }
        cdsService.setManuallySetTranslationStart(cds, true);
//        cds.deleteStopCodonReadThrough();
        cdsService.deleteStopCodonReadThrough(cds)
//        featureRelationshipService.deleteRelationships()

        if (setTranslationEnd && translationTable != null) {
            String mrna = getResiduesWithAlterationsAndFrameshifts(transcript)
            if (mrna == null || mrna.equals("null")) {
                return;
            }
            int stopCodonCount = 0;
            for (int i = convertSourceCoordinateToLocalCoordinateForTranscript(transcript, translationStart); i < transcript.getLength(); i += 3) {
                if (i < 0 || i + 3 > mrna.length()) {
                    break;
                }
                String codon = mrna.substring(i, i + 3);

                if (translationTable.getStopCodons().contains(codon)) {
                    if (readThroughStopCodon && ++stopCodonCount < 2) {
//                        StopCodonReadThrough stopCodonReadThrough = cdsService.getStopCodonReadThrough(cds);
                        StopCodonReadThrough stopCodonReadThrough = (StopCodonReadThrough) featureRelationshipService.getChildForFeature(cds, StopCodonReadThrough.ontologyId)
                        if (stopCodonReadThrough == null) {
                            stopCodonReadThrough = cdsService.createStopCodonReadOnCDS(cds)
//                            stopCodonReadThrough = cdsService.createStopCodonReadThrough(cds);
//                            cdsService.setStopCodonReadThrough(cds, stopCodonReadThrough)
//                            cds.setStopCodonReadThrough(stopCodonReadThrough);
                            if (cds.strand == Strand.NEGATIVE.value) {
                                stopCodonReadThrough.featureLocation.setFmin(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i + 2));
                                stopCodonReadThrough.featureLocation.setFmax(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i) + 1);
                            } else {
                                stopCodonReadThrough.featureLocation.setFmin(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i));
                                stopCodonReadThrough.featureLocation.setFmax(convertModifiedLocalCoordinateToSourceCoordinate(transcript, i + 2) + 1);
                            }
                        }
                        continue;
                    }
                    if (transcript.strand == Strand.NEGATIVE.value) {
                        cds.featureLocation.setFmin(convertLocalCoordinateToSourceCoordinateForTranscript(transcript, i + 2));
                    } else {
                        cds.featureLocation.setFmax(convertLocalCoordinateToSourceCoordinateForTranscript(transcript, i + 3));
                    }
                    return;
                }
            }
            if (transcript.strand == Strand.NEGATIVE.value) {
                cds.featureLocation.setFmin(transcript.getFmin());
                cds.featureLocation.setIsFminPartial(true);
            } else {
                cds.featureLocation.setFmax(transcript.getFmax());
                cds.featureLocation.setIsFmaxPartial(true);
            }
        }

        Date date = new Date();
        cds.setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

/** Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
 *  Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationEnd - Coordinate of the end of translation
 */
/*
public void setTranslationEnd(Transcript transcript, int translationEnd) {
    CDS cds = transcript.getCDS();
    if (cds == null) {
        cds = createCDS(transcript);
        transcript.setCDS(cds);
    }
    if (transcript.getStrand() == -1) {
        cds.setFmin(translationEnd + 1);
    }
    else {
        cds.setFmax(translationEnd);
    }
    setManuallySetTranslationEnd(cds, true);
    cds.deleteStopCodonReadThrough();

    // event fire
    fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

}
*/

/**
 * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation end in
 * @param translationEnd - Coordinate of the end of translation
 */
    @Transactional
    void setTranslationEnd(Transcript transcript, int translationEnd) throws AnnotationException {
        setTranslationEnd(transcript, translationEnd, false);
    }

/**
 * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation end in
 * @param translationEnd - Coordinate of the end of translation
 * @param setTranslationStart - if set to true, will search for the nearest in frame start
 */
    @Transactional
    void setTranslationEnd(Transcript transcript, int translationEnd, boolean setTranslationStart) throws AnnotationException {
        setTranslationEnd(transcript, translationEnd, setTranslationStart,
            setTranslationStart ? organismService.getTranslationTable(transcript.featureLocation.to.organism) : null
        );
    }

/**
 * Set the translation end in the transcript.  Sets the translation end in the underlying CDS feature.
 * Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation end in
 * @param translationEnd - Coordinate of the end of translation
 * @param setTranslationStart - if set to true, will search for the nearest in frame start codon
 * @param translationTable - Translation table that defines the codon translation
 */
    @Transactional
    void setTranslationEnd(Transcript transcript, int translationEnd, boolean setTranslationStart, TranslationTable translationTable) throws AnnotationException {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript, cds);
        }
        // if the start is set, then we make sure we are going to set a legal coordinate
        if (cdsService.isManuallySetTranslationStart(cds)) {
            log.info "${transcript.strand} min ${cds.featureLocation.fmin} vs transl end ${translationEnd}"
            if (transcript.strand == Strand.NEGATIVE.value) {
                if (translationEnd >= cds.featureLocation.fmax) {
                    throw new AnnotationException("Translation end ${translationEnd} must be downstream of the start ${cds.featureLocation.fmax} (smaller)")
                }
            } else {
                if (translationEnd <= cds.featureLocation.fmin) {
                    throw new AnnotationException("Translation end ${translationEnd} must be downstream of the start ${cds.featureLocation.fmin} (larger)")
                }
            }
        }

        if (transcript.strand == Strand.NEGATIVE.value) {
            cds.featureLocation.setFmin(translationEnd);
        } else {
            cds.featureLocation.setFmax(translationEnd + 1);
        }
        cdsService.setManuallySetTranslationEnd(cds, true);
        cdsService.deleteStopCodonReadThrough(cds);
        if (setTranslationStart && translationTable != null) {
            String mrna = getResiduesWithAlterationsAndFrameshifts(transcript);
            if (mrna == null || mrna.equals("null")) {
                return;
            }
            for (int i = convertSourceCoordinateToLocalCoordinateForTranscript(transcript, translationEnd) - 3; i >= 0; i -= 3) {
                if (i - 3 < 0) {
                    break;
                }
                String codon = mrna.substring(i, i + 3);
                if (translationTable.getStartCodons().contains(codon)) {
                    if (transcript.strand == Strand.NEGATIVE.value) {
                        cds.featureLocation.setFmax(convertLocalCoordinateToSourceCoordinateForTranscript(transcript, i + 3));
                    } else {
                        cds.featureLocation.setFmin(convertLocalCoordinateToSourceCoordinateForTranscript(transcript, i + 2));
                    }
                    return;
                }
            }
            if (transcript.strand == Strand.NEGATIVE.value) {
                cds.featureLocation.setFmin(transcript.getFmin());
                cds.featureLocation.setIsFminPartial(true);
            } else {
                cds.featureLocation.setFmax(transcript.getFmax());
                cds.featureLocation.setIsFmaxPartial(true);
            }
        }

        Date date = new Date();
        cds.setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire TODO: ??
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    @Transactional
    void setTranslationFmin(Transcript transcript, int translationFmin) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript, cds);
        }
        setFmin(cds, translationFmin);
        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);
    }

    @Transactional
    void setTranslationFmax(Transcript transcript, int translationFmax) {
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            cds = transcriptService.createCDS(transcript);
            transcriptService.setCDS(transcript, cds);
        }
        setFmax(cds, translationFmax);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

/**
 * Set the translation start and end in the transcript.  Sets the translation start and end in the underlying CDS
 * feature.  Instantiates the CDS object for the transcript if it doesn't already exist.
 *
 * @param transcript - Transcript to set the translation start in
 * @param translationStart - Coordinate of the start of translation
 * @param translationEnd - Coordinate of the end of translation
 * @param manuallySetStart - whether the start was manually set
 * @param manuallySetEnd - whether the end was manually set
 */
    @Transactional
    void setTranslationEnds(Transcript transcript, int translationStart, int translationEnd, boolean manuallySetStart, boolean manuallySetEnd) {
        setTranslationFmin(transcript, translationStart);
        setTranslationFmax(transcript, translationEnd);
        cdsService.setManuallySetTranslationStart(transcriptService.getCDS(transcript), manuallySetStart);
        cdsService.setManuallySetTranslationEnd(transcriptService.getCDS(transcript), manuallySetEnd);

        Date date = new Date();
        transcriptService.getCDS(transcript).setLastUpdated(date);
        transcript.setLastUpdated(date);

        // event fire
//        fireAnnotationChangeEvent(transcript, transcript.getGene(), AnnotationChangeEvent.Operation.UPDATE);

    }

    /** Get the residues for a feature with any alterations and frameshifts.
     *
     * @param feature - Feature to retrieve the residues for
     * @return Residues for the feature with any alterations and frameshifts
     */
    String getResiduesWithAlterationsAndFrameshifts(Feature feature) {
        if (!(feature.instanceOf(CDS.class))) {
            return getResiduesWithAlterations(feature, getSequenceAlterationsForFeature(feature))
        }
        Transcript transcript = (Transcript) featureRelationshipService.getParentForFeature(feature, Transcript.ontologyId)
        Collection<SequenceAlterationArtifact> alterations = getFrameshiftsAsAlterations(transcript);
        List<SequenceAlterationArtifact> allSequenceAlterationList = getSequenceAlterationsForFeature(feature)
        alterations.addAll(allSequenceAlterationList);
        return getResiduesWithAlterations(feature, alterations)
    }

    /**
     // TODO: should be a single query here, currently 194 ms
     * Get all sequenceAlterations associated with a feature.
     * Basically I want to include all upstream alterations on a sequence for that feature
     * @param feature
     * @return
     */
    List<SequenceAlterationArtifact> getAllSequenceAlterationsForFeature(Feature feature) {
//        Sequence sequence = Sequence.executeQuery("select s from Feature  f join f.featureLocations fl join fl.sequence s where f = :feature ", [feature: feature]).first()
//        Sequence.createCriteria().listDistinct {
//        }
//        def sequences = Sequence.createCriteria().listDistinct {
//            featureLocations {
//                eq 'feature', feature
//            }
//        }
//        def sequence = []
        // TODO: should not need to assert this yet

//        assert sequences && sequences.size()>0
////        def featureLocations = FeatureLocation.createCriteria().list{
////            eq "feature", feature
////            join 'sequence'
////        }
//        def featureLocations2 = FeatureLocation.findAllByFeature(feature,[fetch:[organism:'join']])
//        log.debug "feature locations ${sequences}"
//        log.debug "feature locations 2: ${featureLocations2}"
//        if(sequences){
//            SequenceAlterationArtifact.executeQuery("select sa from SequenceAlterationArtifact sa join sa.featureLocations fl join fl.sequence s where s = :sequence order by fl.fmin asc ", [sequence: sequences.first()])
//        SequenceAlterationArtifact.executeQuery("select sa from SequenceAlterationArtifact sa join sa.featureLocations fl join fl.sequence s where s = :sequence order by fl.fmin asc ", [sequence: sequences.first()])
//        Collection<SequenceAlterationArtifact> features = (Collection<SequenceAlterationArtifact>) SequenceAlterationArtifact.createCriteria().listDistinct {
//            featureLocations {
//                eq "sequence", sequence
//                order fmin
//            }
//        }
//        return features
//        }
//        else{
        return []
//        }
    }

    // TODO: implement
    List<SequenceAlterationArtifact> getFrameshiftsAsAlterations(Transcript transcript) {
        List<SequenceAlterationArtifact> frameshifts = new ArrayList<SequenceAlterationArtifact>();
        CDS cds = transcriptService.getCDS(transcript);
        if (cds == null) {
            return frameshifts;
        }
//        Sequence sequence = cds.getFeatureLocation().to
        Sequence sequence = Sequence.executeQuery("MATCH (f:Feature)--(s:Sequence) where f.uniqueName=${transcript.uniqueName} return s")[0] as Sequence
        List<Frameshift> frameshiftList = transcriptService.getFrameshifts(transcript)
        for (Frameshift frameshift : frameshiftList) {
            if (frameshift.isPlusFrameshift()) {
                // a plus frameshift skips bases during translation, which can be mapped to a deletion for the
                // the skipped bases
//                Deletion deletion = new Deletion(cds.getOrganism(), "Deletion-" + frameshift.getCoordinate(), false,
//                        false, new Timestamp(new Date().getTime()), cds.getConfiguration());

                FeatureLocation featureLocation = new FeatureLocation(
                    fmin: frameshift.coordinate
                    , fmax: frameshift.coordinate + frameshift.frameshiftValue
                    , strand: cds.featureLocation.strand
                    , to: sequence
                )

                DeletionArtifact deletion = new DeletionArtifact(
                    uniqueName: FeatureStringEnum.DELETION_PREFIX.value + frameshift.coordinate
                )

                featureLocation.from = deletion
                deletion.setFeatureLocation(featureLocation)

                frameshifts.add(deletion);
                featureLocation.save(flush: true)
                deletion.save(flush: true)
                frameshift.save(flush: true)

//                deletion.setFeatureLocation(frameshift.getCoordinate(),
//                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        cds.getFeatureLocation().getStrand(), sourceFeature);


            } else {
                // a minus frameshift goes back bases during translation, which can be mapped to an insertion for the
                // the repeated bases
                InsertionArtifact insertion = new InsertionArtifact(
                    uniqueName: FeatureStringEnum.INSERTION_PREFIX.value + frameshift.coordinate
                ).save()

//                Insertion insertion = new Insertion(cds.getOrganism(), "Insertion-" + frameshift.getCoordinate(), false,
//                        false, new Timestamp(new Date().getTime()), cds.getConfiguration());

                FeatureLocation featureLocation = new FeatureLocation(
                    fmin: frameshift.coordinate
                    , fmax: frameshift.coordinate + frameshift.frameshiftValue
                    , strand: cds.featureLocation.strand
                    , to: sequence
                ).save()

                insertion.setFeatureLocation(featureLocation)
                featureLocation.from = insertion

//                insertion.setFeatureLocation(frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(),
//                        cds.getFeatureLocation().getStrand(), sourceFeature);

                String alterationResidues = sequenceService.getRawResiduesFromSequence(sequence, frameshift.getCoordinate() + frameshift.getFrameshiftValue(), frameshift.getCoordinate())
                insertion.alterationResidue = alterationResidues
                // TODO: correct?
//                insertion.setResidues(sequence.getResidues().substring(
//                        frameshift.getCoordinate() + frameshift.getFrameshiftValue(), frameshift.getCoordinate()));
                frameshifts.add(insertion);

                insertion.save()
                featureLocation.save(flush: true)
                frameshift.save(flush: true)
            }
        }
        return frameshifts;
    }

/*
 * Calculate the longest ORF for a transcript.  If a valid start codon is not found, allow for partial CDS start/end.
 *
 * @param transcript - Transcript to set the longest ORF to
 * @param translationTable - Translation table that defines the codon translation
 * @param allowPartialExtension - Where partial ORFs should be used for possible extension
 *
 */


    @Transactional
    void setLongestORF(Transcript transcript, boolean readThroughStopCodon) {
//        Organism organism = transcript.featureLocation.to.organism
        Organism organism = Organism.executeQuery("MATCH (t:Transcript)-[]-(s:Sequence)-[]-(o:Organism) where t.uniqueName = ${transcript.uniqueName} return o limit 1")[0] as Organism
        TranslationTable translationTable = organismService.getTranslationTable(organism)
        String mrna = getResiduesWithAlterationsAndFrameshifts(transcript)

        println "set longest ORF ${organism}, ${translationTable} ${mrna?.size()} -> ${mrna} and readthrough ${readThroughStopCodon}"
        if (!mrna) {
            println "mrna not found, so returning nothing"
            return
        }
        String longestPeptide = ""
        int bestStartIndex = -1
        int bestStopIndex = -1
        int startIndex = -1
        int stopIndex = -1
        boolean partialStart = false
        boolean partialStop = false

        if (mrna.length() > 3) {
            for (String startCodon : translationTable.getStartCodons()) {
                // find the first start codon
                startIndex = mrna.indexOf(startCodon)
                while (startIndex >= 0) {
                    String mrnaSubstring = mrna.substring(startIndex)
                    String aa = SequenceTranslationHandler.translateSequence(mrnaSubstring, translationTable, true, readThroughStopCodon)
                    if (aa.length() > longestPeptide.length()) {
                        longestPeptide = aa
                        bestStartIndex = startIndex
                    }
                    startIndex = mrna.indexOf(startCodon, startIndex + 1)
                }
            }

            // Just in case the 5' end is missing, check to see if a longer
            // translation can be obtained without looking for a start codon
            startIndex = 0
            while (startIndex < 3) {
                String mrnaSubstring = mrna.substring(startIndex)
                String aa = SequenceTranslationHandler.translateSequence(mrnaSubstring, translationTable, true, readThroughStopCodon)
                if (aa.length() > longestPeptide.length()) {
                    partialStart = true
                    longestPeptide = aa
                    bestStartIndex = startIndex
                }
                startIndex++
            }
        }

        // check for partial stop
        if (!longestPeptide.substring(longestPeptide.length() - 1).equals(TranslationTable.STOP)) {
            partialStop = true
            bestStopIndex = -1
        } else {
            stopIndex = bestStartIndex + (longestPeptide.length() * 3)
            partialStop = false
            bestStopIndex = stopIndex
        }

        println "bestStartIndex: ${bestStartIndex} bestStopIndex: ${bestStopIndex}; partialStart: ${partialStart} partialStop: ${partialStop} readThroughStop ${readThroughStopCodon}"

        println "is an instance of an mRNA ${MRNA.class} ${transcript.class} -> cvTerm: ${transcript.cvTerm} ${transcript.alternateCvTerm} "
        println "equality 2: ${transcript.instanceOf(MRNA.class)} vs ${FeatureTypeMapper.hasOntologyId(transcript.cvTerm,MRNA.cvTerm,MRNA.alternateCvTerm)}"

        if (FeatureTypeMapper.hasOntologyId(transcript.cvTerm,MRNA.cvTerm,MRNA.alternateCvTerm)) {
//        if (transcript.instanceOf(MRNA.class)) {
            println "is an MRNA version 2"
            CDS cds = transcriptService.getCDS(transcript)
            println "cds ${cds}"
            if (cds == null) {
                println "creating CDS "
                cds = transcriptService.createCDS(transcript);
                println "created a CDS ${cds}"
                println "created a CDS per location ${cds.featureLocation as JSON}"
                transcriptService.setCDS(transcript, cds);
                println "set CDS ${cds} on transcript ${transcript}"
            }

            int fmin = convertModifiedLocalCoordinateToSourceCoordinate(transcript, bestStartIndex)
            log.debug "best fmin ${fmin}"

            FeatureLocation cdsFeatureLocation = FeatureLocation.findByFrom(cds)
            FeatureLocation transcriptFeatureLocation = FeatureLocation.findByFrom(transcript)

            int fmax = -1
            if (bestStopIndex >= 0) {
                log.debug "bestStopIndex >= 0"
                fmax = convertModifiedLocalCoordinateToSourceCoordinate(transcript, bestStopIndex)
                if (cdsFeatureLocation.strand == Strand.NEGATIVE.value) {
                    int tmp = fmin
                    fmin = fmax + 1
                    fmax = tmp + 1
                }
                cdsFeatureLocation.fmin = fmin
                cdsFeatureLocation.fmax = fmax
                log.debug "bestStopIndex >=0 0 setting fmin and famx to ${fmin} and ${fmax} respectively, ${cds.strand}"
            } else {
                log.debug "bestStopIndex < 0"
                fmax = transcriptFeatureLocation.strand == Strand.NEGATIVE.value ? transcriptFeatureLocation.fmin : transcriptFeatureLocation.fmax
                if (cdsFeatureLocation.strand == Strand.NEGATIVE.value) {
                    int tmp = fmin
                    fmin = fmax
                    fmax = tmp + 1
                }
                cdsFeatureLocation.fmin = fmin
                cdsFeatureLocation.fmax = fmax
                log.debug "bestStopIndex < 0 setting fmin and famx to ${fmin} and ${fmax} respectively, ${cdsFeatureLocation.strand}"
            }
            log.debug "looking at strands for ${cds}"


            log.debug "cds ${cds}"
            log.debug "result CDS locatin is ${cdsFeatureLocation as JSON}"

            Boolean fminPartial
            Boolean fmaxPartial
            if (cdsFeatureLocation.strand == Strand.NEGATIVE.value) {
                cdsFeatureLocation.setIsFminPartial(partialStop)
                cdsFeatureLocation.setIsFmaxPartial(partialStart)
                fminPartial = partialStop
                fmaxPartial = partialStart
            } else {
                cdsFeatureLocation.setIsFminPartial(partialStart)
                cdsFeatureLocation.setIsFmaxPartial(partialStop)
                fminPartial = partialStart
                fmaxPartial = partialStop
            }
//
            cdsFeatureLocation.save(flush: true, failonError: true)


            String inputQuery = "MATCH (t:Transcript)--(cds:CDS)-[fl:FEATURELOCATION]-(s) where t.uniqueName='${transcript.uniqueName}' and cds.uniqueName='${cds.uniqueName}' " +
                " set fl.fmin=${fmin},fl.fmax=${fmax},fl.isMaxPartial=${fmaxPartial},fl.isMinPartial=${fminPartial} RETURN cds,fl "
            println "input query"
            println inputQuery
            def returnValue = FeatureLocation.executeUpdate(inputQuery)
            println "${returnValue}"

            // re-query CDS
            // reload?
//            cds = CDS.findByUniqueName(cds.uniqueName)

            println "Final CDS fmin: ${cdsFeatureLocation.fmin} fmax: ${cdsFeatureLocation.fmax} for ${cds}"
            println "setting the read through stop codon ${readThroughStopCodon}"
            cdsService.deleteStopCodonReadThrough(cds)
            println "deleted if existing sto codon read through ${readThroughStopCodon}"
            if (readThroughStopCodon) {
                println "deleting existing one"
                String aa = SequenceTranslationHandler.translateSequence(getResiduesWithAlterationsAndFrameshifts(cds), translationTable, true, true);
                int firstStopIndex = aa.indexOf(TranslationTable.STOP);
                println "first stop index ${firstStopIndex} of ${TranslationTable.STOP} vs ${aa.length()}"
                if (firstStopIndex < aa.length() - 1) {
                    println "first stop is less than length -1 so creating the stop codon read through "
                    StopCodonReadThrough stopCodonReadThrough = cdsService.createStopCodonReadOnCDS(cds);
//                    StopCodonReadThrough stopCodonReadThrough = cdsService.createStopCodonReadThrough(cds);
//                    cdsService.setStopCodonReadThrough(cds, stopCodonReadThrough)

                    println "it is now set ${stopCodonReadThrough}"
                    println "retrieving ${cdsService.getStopCodonReadThrough(cds)}"

                    int offset = transcriptFeatureLocation.strand == -1 ? -2 : 0;
                    setFmin(stopCodonReadThrough, convertModifiedLocalCoordinateToSourceCoordinate(cds, firstStopIndex * 3) + offset);
                    setFmax(stopCodonReadThrough, convertModifiedLocalCoordinateToSourceCoordinate(cds, firstStopIndex * 3) + 3 + offset);
                }
            }
            else {
                println "no read through stop codon so not adding it back"
            }
            cdsService.setManuallySetTranslationStart(cds, false);
            cdsService.setManuallySetTranslationEnd(cds, false);
        }
    }


    @Transactional
    Feature convertJSONToFeature(JSONObject jsonFeature, Sequence sequence) {
        Feature returnFeature
        try {
            JSONObject type = jsonFeature.getJSONObject(FeatureStringEnum.TYPE.value);
            String ontologyId = FeatureTypeMapper.convertJSONToOntologyId(type)
            if (!ontologyId) {
                log.warn "Feature type not set for ${type}"
                return null
            }

            returnFeature = FeatureTypeMapper.generateFeatureForType(ontologyId)
            if (jsonFeature.has(FeatureStringEnum.ID.value)) {
                returnFeature.setId(jsonFeature.getLong(FeatureStringEnum.ID.value));
            }

            if (jsonFeature.has(FeatureStringEnum.UNIQUENAME.value)) {
                returnFeature.setUniqueName(jsonFeature.getString(FeatureStringEnum.UNIQUENAME.value));
            } else {
                returnFeature.setUniqueName(nameService.generateUniqueName());
            }
            if (jsonFeature.has(FeatureStringEnum.NAME.value)) {
                returnFeature.setName(jsonFeature.getString(FeatureStringEnum.NAME.value));
            } else {
                // since name attribute cannot be null, using the feature's own uniqueName
                returnFeature.name = returnFeature.uniqueName
            }
            if (jsonFeature.has(FeatureStringEnum.SYMBOL.value)) {
                returnFeature.setSymbol(jsonFeature.getString(FeatureStringEnum.SYMBOL.value));
            }
            if (jsonFeature.has(FeatureStringEnum.DESCRIPTION.value)) {
                returnFeature.setDescription(jsonFeature.getString(FeatureStringEnum.DESCRIPTION.value));
            }
            if (returnFeature.instanceOf(DeletionArtifact.class)) {
                int deletionLength = jsonFeature.location.fmax - jsonFeature.location.fmin
                returnFeature.deletionLength = deletionLength
            }

            if (returnFeature.instanceOf(SequenceAlteration.class)) {
                if (jsonFeature.has(FeatureStringEnum.REFERENCE_ALLELE.value)) {
                    Allele allele = new Allele(bases: jsonFeature.getString(FeatureStringEnum.REFERENCE_ALLELE.value), reference: true)
                    allele.save()
                    returnFeature.addToAlleles(allele)
                    returnFeature.save(failOnError: true)
                    allele.variant = returnFeature
                    allele.save()
                }
                if (jsonFeature.has(FeatureStringEnum.ALTERNATE_ALLELES.value)) {
                    JSONArray alternateAllelesArray = jsonFeature.getJSONArray(FeatureStringEnum.ALTERNATE_ALLELES.value)
                    for (int i = 0; i < alternateAllelesArray.length(); i++) {
                        JSONObject alternateAlleleJsonObject = alternateAllelesArray.getJSONObject(i)
                        String bases = alternateAlleleJsonObject.getString(FeatureStringEnum.BASES.value)
                        Allele allele = new Allele(bases: bases, variant: returnFeature)
                        allele.save()

                        // Processing properties of an Allele
                        if (alternateAllelesArray.getJSONObject(i).has(FeatureStringEnum.ALLELE_INFO.value)) {
                            JSONArray alleleInfoArray = alternateAllelesArray.getJSONObject(i).getJSONArray(FeatureStringEnum.ALLELE_INFO.value)
                            for (int j = 0; j < alleleInfoArray.length(); j++) {
                                JSONObject info = alleleInfoArray.getJSONObject(j)
                                String tag = info.getString(FeatureStringEnum.TAG.value)
                                String value = info.getString(FeatureStringEnum.VALUE.value)
                                AlleleInfo alleleInfo = new AlleleInfo(tag: tag, value: value, allele: allele).save()
                                allele.addToAlleleInfo(alleleInfo)
                            }
                        }

                        returnFeature.addToAlleles(allele)
                    }
                }
                returnFeature.save(flush: true)

                // Processing proerties of a variant
                if (jsonFeature.has(FeatureStringEnum.VARIANT_INFO.value)) {
                    JSONArray variantInfoArray = jsonFeature.getJSONArray(FeatureStringEnum.VARIANT_INFO.value)
                    for (int i = 0; i < variantInfoArray.size(); i++) {
                        JSONObject variantInfoObject = variantInfoArray.get(i)
                        VariantInfo variantInfo = new VariantInfo(tag: variantInfoObject.get(FeatureStringEnum.TAG.value), value: variantInfoObject.get(FeatureStringEnum.VALUE.value))
                        variantInfo.variant = returnFeature
                        variantInfo.save()
                        returnFeature.addToVariantInfo(variantInfo)
                    }
                }
            }

            returnFeature.save(flush: true)

            if (jsonFeature.has(FeatureStringEnum.LOCATION.value)) {
                JSONObject jsonLocation = jsonFeature.getJSONObject(FeatureStringEnum.LOCATION.value);
                FeatureLocation featureLocation
                if (FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(type.getString(FeatureStringEnum.NAME.value))) {
                    featureLocation = convertJSONToFeatureLocation(jsonLocation, sequence, returnFeature, Strand.NONE.value)
                } else {
                    featureLocation = convertJSONToFeatureLocation(jsonLocation, sequence, returnFeature)
                }
                featureLocation.to = sequence
                featureLocation.from = returnFeature
                featureLocation.save(flush: true)
                returnFeature.featureLocation = featureLocation;
            }

            if (returnFeature.instanceOf(DeletionArtifact.class)) {
                sequenceService.setResiduesForFeatureFromLocation((DeletionArtifact) returnFeature)
            } else if (jsonFeature.has(FeatureStringEnum.RESIDUES.value) && returnFeature.instanceOf(SequenceAlterationArtifact.class)) {
                sequenceService.setResiduesForFeature(returnFeature, jsonFeature.getString(FeatureStringEnum.RESIDUES.value))
            }

            if (jsonFeature.has(FeatureStringEnum.CHILDREN.value)) {
                JSONArray children = jsonFeature.getJSONArray(FeatureStringEnum.CHILDREN.value);
                log.debug "jsonFeature ${jsonFeature} has ${children?.size()} children"
                for (int i = 0; i < children.length(); ++i) {
                    JSONObject childObject = children.getJSONObject(i)
                    Feature child = convertJSONToFeature(childObject, sequence);
                    // if it retuns null, we ignore it
                    if (child) {
                        child.save(failOnError: true)
                        FeatureRelationship fr = new FeatureRelationship()
                        fr.from = returnFeature
                        fr.to = child
                        fr.save(failOnError: true)
                        returnFeature.addToParentFeatureRelationships(fr)
                        child.addToChildFeatureRelationships(fr)
                        child.save()
                    }
                    returnFeature.save()
                }
            }
            if (jsonFeature.has(FeatureStringEnum.TIMEACCESSION.value)) {
                returnFeature.setDateCreated(new Date(jsonFeature.getInt(FeatureStringEnum.TIMEACCESSION.value)));
            } else {
                returnFeature.setDateCreated(new Date());
            }
            if (jsonFeature.has(FeatureStringEnum.TIMELASTMODIFIED.value)) {
                returnFeature.setLastUpdated(new Date(jsonFeature.getInt(FeatureStringEnum.TIMELASTMODIFIED.value)));
            } else {
                returnFeature.setLastUpdated(new Date());
            }
            if (jsonFeature.has(FeatureStringEnum.EXPORT_ALIAS.value.toLowerCase())) {
                for (String synonymString in jsonFeature.getJSONArray(FeatureStringEnum.EXPORT_ALIAS.value.toLowerCase())) {
                    Synonym synonym = new Synonym(
                        name: synonymString
                    ).save()
                    FeatureSynonym featureSynonym = new FeatureSynonym(
                        feature: returnFeature,
                        synonym: synonym
                    ).save()
                    returnFeature.addToFeatureSynonyms(featureSynonym)
                }
            }
            if (configWrapperService.storeOrigId()) {
                if (jsonFeature.has(FeatureStringEnum.ORIG_ID.value)) {
                    FeatureProperty featureProperty = new FeatureProperty()
                    featureProperty.setTag(FeatureStringEnum.ORIG_ID.value)
                    featureProperty.setValue(jsonFeature.getString(FeatureStringEnum.ORIG_ID.value))
                    featureProperty.setFeature(returnFeature)
                    featureProperty.save()
                    returnFeature.addToFeatureProperties(featureProperty)
                }
            }

            // push notes into comment field if at the top-level
            if (jsonFeature.has(FeatureStringEnum.EXPORT_NOTE.value.toLowerCase())) {
                JSONArray exportNoteArray = jsonFeature.getJSONArray(FeatureStringEnum.EXPORT_NOTE.value.toLowerCase())
//                String propertyValue = property.get(FeatureStringEnum.VALUE.value)
                int rank = 0
                for (String noteString in exportNoteArray) {
                    Comment comment = new Comment(
                        feature: returnFeature,
                        rank: rank++,
                        value: noteString
                    ).save()
                    returnFeature.addToFeatureProperties(comment)
                }
            }


            // handle status here
            if (jsonFeature.has(FeatureStringEnum.STATUS.value)) {
                String propertyValue = jsonFeature.get(FeatureStringEnum.STATUS.value)
//                String propertyValue = property.get(FeatureStringEnum.VALUE.value)
                AvailableStatus availableStatus = AvailableStatus.findByValue(propertyValue)
                if (availableStatus) {
                    Status status = new Status(
                        value: availableStatus.value,
                        feature: returnFeature
                    ).save(failOnError: true)
                    returnFeature.status = status
                    returnFeature.save()
                } else {
                    log.warn "Ignoring status ${propertyValue} as its not defined."
                }
            }

            if (jsonFeature.has(FeatureStringEnum.PROPERTIES.value)) {
                JSONArray properties = jsonFeature.getJSONArray(FeatureStringEnum.PROPERTIES.value)
                for (int i = 0; i < properties.length(); ++i) {
                    JSONObject property = properties.getJSONObject(i);
                    JSONObject propertyType = property.getJSONObject(FeatureStringEnum.TYPE.value)
                    String propertyName = null
                    if (property.has(FeatureStringEnum.NAME.value)) {
                        propertyName = property.get(FeatureStringEnum.NAME.value)
                    } else if (propertyType.has(FeatureStringEnum.NAME.value)) {
                        propertyName = propertyType.get(FeatureStringEnum.NAME.value)
                    }
                    String propertyValue = property.get(FeatureStringEnum.VALUE.value)

                    FeatureProperty featureProperty = null
                    if (propertyName == FeatureStringEnum.STATUS.value) {
                        // property of type 'Status'
                        AvailableStatus availableStatus = AvailableStatus.findByValue(propertyValue)
                        if (availableStatus) {
                            Status status = new Status(
                                value: availableStatus.value,
                                feature: returnFeature
                            ).save(failOnError: true)
                            returnFeature.status = status
                            returnFeature.save()
                        } else {
                            log.warn "Ignoring status ${propertyValue} as its not defined."
                        }
                    } else if (propertyName) {
                        if (propertyName == FeatureStringEnum.COMMENT.value) {
                            // property of type 'Comment'
                            featureProperty = new Comment()
                        } else {
                            featureProperty = new FeatureProperty()
                        }

                        if (propertyType.has(FeatureStringEnum.NAME.value)) {
                            CV cv = CV.findByName(propertyType.getJSONObject(FeatureStringEnum.CV.value).getString(FeatureStringEnum.NAME.value))
                            CVTerm cvTerm = CVTerm.findByNameAndCv(propertyType.getString(FeatureStringEnum.NAME.value), cv)
                            featureProperty.setType(cvTerm);
                        } else {
                            log.warn "No proper type for the CV is set ${propertyType as JSON}"
                        }
                        featureProperty.setTag(propertyName)
                        featureProperty.setValue(propertyValue)
                        featureProperty.setFeature(returnFeature);

                        int rank = 0;
                        for (FeatureProperty fp : returnFeature.getFeatureProperties()) {
                            if (fp.getType().equals(featureProperty.getType())) {
                                if (fp.getRank() > rank) {
                                    rank = fp.getRank()
                                }
                            }
                        }
                        featureProperty.setRank(rank + 1)
                        featureProperty.save()
                        returnFeature.addToFeatureProperties(featureProperty)
                    }
                }
            }
            // from a GFF3 OGS
            if (jsonFeature.has(FeatureStringEnum.EXPORT_DBXREF.value.toLowerCase())) {
                JSONArray dbxrefs = jsonFeature.getJSONArray(FeatureStringEnum.EXPORT_DBXREF.value.toLowerCase());
                for (String dbxrefString in dbxrefs) {
                    def (dbString, accessionString) = dbxrefString.split(":")
//                    JSONObject db = dbxref.getJSONObject(FeatureStringEnum.DB.value)
                    DB newDB = DB.findOrSaveByName(dbString)
                    DBXref newDBXref = DBXref.findOrSaveByDbAndAccession(newDB, accessionString).save()
                    returnFeature.addToFeatureDBXrefs(newDBXref)
                    returnFeature.save()
                }
            }

            if (jsonFeature.has(FeatureStringEnum.DBXREFS.value)) {
                JSONArray dbxrefs = jsonFeature.getJSONArray(FeatureStringEnum.DBXREFS.value);
                for (int i = 0; i < dbxrefs.length(); ++i) {
                    JSONObject dbxref = dbxrefs.getJSONObject(i);
                    JSONObject db = dbxref.getJSONObject(FeatureStringEnum.DB.value);


                    DB newDB = DB.findOrSaveByName(db.getString(FeatureStringEnum.NAME.value))
                    DBXref newDBXref = DBXref.findOrSaveByDbAndAccession(
                        newDB,
                        dbxref.getString(FeatureStringEnum.ACCESSION.value)
                    ).save()
                    returnFeature.addToFeatureDBXrefs(newDBXref)
                    returnFeature.save()
                }
            }
            // TODO: gene_product
            // only coming from GFF3
            if (jsonFeature.has(FeatureStringEnum.GENE_PRODUCT.value)) {
                String geneProductString = jsonFeature.getString(FeatureStringEnum.GENE_PRODUCT.value)
                log.debug "gene product array ${geneProductString}"
                List<GeneProduct> geneProducts = geneProductService.convertGff3StringToGeneProducts(geneProductString)
                log.debug "gene products outputs ${geneProducts}: ${geneProducts.size()}"
                geneProducts.each {
                    it.feature = returnFeature
                    it.save()
                    returnFeature.addToGeneProducts(it)
                }
                returnFeature.save()
            }
            // TODO: provenance
            if (jsonFeature.has(FeatureStringEnum.PROVENANCE.value)) {
                String provenanceString = jsonFeature.getString(FeatureStringEnum.PROVENANCE.value)
                log.debug "provenance array ${provenanceString}"
                List<Provenance> listOfProvenances = provenanceService.convertGff3StringToProvenances(provenanceString)
                log.debug "gene products outputs ${listOfProvenances}: ${listOfProvenances.size()}"
                listOfProvenances.each {
                    it.feature = returnFeature
                    it.save()
                    returnFeature.addToProvenances(it)
                }
                returnFeature.save()
            }
            // TODO: go_annotation
            if (jsonFeature.has(FeatureStringEnum.GO_ANNOTATIONS.value)) {
                String goAnnotationString = jsonFeature.getString(FeatureStringEnum.GO_ANNOTATIONS.value)
                log.debug "go annotations array ${goAnnotationString}"
                List<GoAnnotation> goAnnotations = goAnnotationService.convertGff3StringToGoAnnotations(goAnnotationString)
                log.debug "gene products outputs ${goAnnotations}: ${goAnnotations.size()}"
                goAnnotations.each {
                    it.feature = returnFeature
                    it.save()
                    returnFeature.addToGoAnnotations(it)
                }
                returnFeature.save()
            }
        }
        catch (JSONException e) {
            log.error("Exception creating Feature from JSON ${jsonFeature}", e)
            return null;
        }
        return returnFeature;
    }



    String getCvTermFromNeo4jFeature(def feature) {
        log.debug "cv term ${feature}"
        String specificType = FeatureTypeMapper.findMostSpecificLabel(feature.labels())
        log.debug "specific type ${specificType}"
        return Class.forName("org.bbop.apollo.feature.${specificType}")?.cvTerm
    }

    String getCvTermFromFeature(Feature feature) {
        String cvTerm = feature.cvTerm
        return cvTerm
    }

    boolean isJsonTranscript(JSONObject jsonObject) {
        JSONObject typeObject = jsonObject.getJSONObject(FeatureStringEnum.TYPE.value)
        String typeString = typeObject.getString(FeatureStringEnum.NAME.value)
        if (typeString == MRNA.cvTerm) {
            return true
        } else {
            return false
        }
    }




    @Transactional
    void updateGeneBoundaries(Gene gene) {
        log.debug "updateGeneBoundaries"
        if (gene == null) {
            return;
        }
        int geneFmax = Integer.MIN_VALUE;
        int geneFmin = Integer.MAX_VALUE;
        for (Transcript t : transcriptService.getTranscripts(gene)) {
            if (t.getFmin() < geneFmin) {
                geneFmin = t.getFmin();
            }
            if (t.getFmax() > geneFmax) {
                geneFmax = t.getFmax();
            }
        }
        gene.featureLocation.setFmin(geneFmin);
        gene.featureLocation.setFmax(geneFmax);
        gene.setLastUpdated(new Date());
    }

    @Transactional
    def mergeIsoformBoundaries(Feature feature1, Feature feature2) {
        int featureFmax = feature1.fmax > feature2.fmax ? feature1.fmax : feature2.fmax
        int featureFmin = feature1.fmin < feature2.fmin ? feature1.fmin : feature2.fmin
        featureService.setFmin(feature1, featureFmin)
        featureService.setFmax(feature1, featureFmax)
        featureService.setFmin(feature2, featureFmin)
        featureService.setFmax(feature2, featureFmax)
    }


    @Transactional
    def setFmin(Feature feature, int fmin) {
        feature.getFeatureLocation().setFmin(fmin);
    }

    @Transactional
    def setFmax(Feature feature, int fmax) {
        feature.getFeatureLocation().setFmax(fmax);
    }

    /** Convert source feature coordinate to local coordinate.
     * @deprecated
     * @param sourceCoordinate - Coordinate to convert to local coordinate
     * @return Local coordinate, -1 if source coordinate is <= fmin or >= fmax
     */
    int convertSourceCoordinateToLocalCoordinate(Feature feature, int sourceCoordinate) {
        // TODO:
        return convertSourceCoordinateToLocalCoordinate(feature.featureLocation.fmin, feature.featureLocation.fmax, Strand.getStrandForValue(feature.featureLocation.strand), sourceCoordinate)
    }

    /** Convert source feature coordinate to local coordinate.
     * @deprecated
     * @param sourceCoordinate - Coordinate to convert to local coordinate
     * @return Local coordinate, -1 if source coordinate is <= fmin or >= fmax
     */
    int convertSourceCoordinateToLocalCoordinate(FeatureLocation featureLocation, int sourceCoordinate) {
        return convertSourceCoordinateToLocalCoordinate(featureLocation.fmin, featureLocation.fmax, Strand.getStrandForValue(featureLocation.strand), sourceCoordinate)
    }

    int convertSourceCoordinateToLocalCoordinate(int fmin, int fmax, Strand strand, int sourceCoordinate) {
        if (sourceCoordinate < fmin || sourceCoordinate > fmax) {
            return -1;
        }
        if (strand == Strand.NEGATIVE) {
            return fmax - 1 - sourceCoordinate;
        } else {
            return sourceCoordinate - fmin;
        }
    }

    int convertSourceCoordinateToLocalCoordinateForTranscript(Transcript transcript, int sourceCoordinate) {
        List<Exon> exons = transcriptService.getSortedExons(transcript, true)
        int localCoordinate = -1
        int currentCoordinate = 0
        for (Exon exon : exons) {
            if (exon.fmin <= sourceCoordinate && exon.fmax >= sourceCoordinate) {
                //sourceCoordinate falls within the exon
                if (exon.strand == Strand.NEGATIVE.value) {
                    localCoordinate = currentCoordinate + (exon.fmax - sourceCoordinate) - 1;
                } else {
                    localCoordinate = currentCoordinate + (sourceCoordinate - exon.fmin);
                }
            }
            currentCoordinate += exon.getLength();
        }
        return localCoordinate
    }


    int convertSourceCoordinateToLocalCoordinateForCDS(Feature feature, int sourceCoordinate) {
        def exons = []
        CDS cds
        if (feature.instanceOf(Transcript.class)) {
            exons = transcriptService.getSortedExons(feature, true)
            cds = transcriptService.getCDS(feature)
        } else if (feature.instanceOf(CDS.class)) {
            Transcript transcript = transcriptService.getTranscript(feature)
            exons = transcriptService.getSortedExons(transcript, true)
            cds = feature
        }

        int localCoordinate = 0

        if (!(cds.fmin <= sourceCoordinate && cds.fmax >= sourceCoordinate)) {
            return -1
        }
        int x = 0
        int y = 0
        if (feature.strand == Strand.POSITIVE.value) {
            for (Exon exon : exons) {
                if (overlapperService.overlaps(exon, cds, true) && exon.fmin >= cds.fmin && exon.fmax <= cds.fmax) {
                    // complete overlap
                    x = exon.fmin
                    y = exon.fmax
                } else if (overlapperService.overlaps(exon, cds, true)) {
                    // partial overlap
                    if (exon.fmin < cds.fmin && exon.fmax < cds.fmax) {
                        x = cds.fmin
                        y = exon.fmax
                    } else {
                        //exon.fmin > cds.fmin && exon.fmax > cds.fmax
                        x = exon.fmin
                        y = cds.fmax
                    }
                } else {
                    // no overlap
                    continue
                }

                if (x <= sourceCoordinate && y >= sourceCoordinate) {
                    localCoordinate += sourceCoordinate - x
                    return localCoordinate
                } else {
                    localCoordinate += y - x
                }
            }
        } else {
            for (Exon exon : exons) {
                if (overlapperService.overlaps(exon, cds, true) && exon.fmin >= cds.fmin && exon.fmax <= cds.fmax) {
                    // complete overlap
                    x = exon.fmax
                    y = exon.fmin
                } else if (overlapperService.overlaps(exon, cds, true)) {
                    // partial overlap
                    //x = cds.fmax
                    //y = exon.fmin
                    if (exon.fmin <= cds.fmin && exon.fmax <= cds.fmax) {
                        x = exon.fmax
                        y = cds.fmin
                    } else {
                        //exon.fmin > cds.fmin && exon.fmax > cds.fmax
                        x = cds.fmax
                        y = exon.fmin
                    }
                } else {
                    // no overlap
                    continue
                }
                if (y <= sourceCoordinate && x >= sourceCoordinate) {
                    localCoordinate += (x - sourceCoordinate) - 1
                    return localCoordinate
                } else {
                    localCoordinate += (x - y)
                }
            }
        }
    }


    void removeFeatureRelationship(Transcript transcript, Feature feature) {

        FeatureRelationship featureRelationship = FeatureRelationship.findByParentFeatureAndChildFeature(transcript, feature)
        if (featureRelationship) {
            FeatureRelationship.deleteAll()
        }
    }


    Feature convertNeo4jFeatureToFeature(def neo4jObject, boolean includeSequence = false) {

        def neo4jFeature = neo4jObject.feature
        if (!neo4jFeature.keys()) return null
        def neo4jLocation = neo4jObject.location
        def neo4jOwners = neo4jObject.owners
        def neo4jSequence = neo4jObject.sequence

        def neo4jChildren = neo4jObject.children
        def neo4jParent = neo4jObject.parent


        Feature feature = new Feature()
//        log.debug "ID ${neo4jFeature.id()}"
//        log.debug "labels ${neo4jFeature.labels().join(",")}"
//        log.debug "keys ${neo4jFeature.keys().join(",")}"
//        if (neo4jFeature.id()!=null) {
//            feature.id = neo4jFeature.id()
//        }
//        def types = neo4jFeature.labels()
//        String type = types.last() // TODO: find a better way for this to get the most specific type
//        jsonFeature.put(FeatureStringEnum.TYPE.value, generateJSONFeatureStringForType(neo4jFeature.ontologyId))
//        log.debug "keys: ${neo4jFeature.keys()}"
        feature.uniqueName = neo4jFeature.get(FeatureStringEnum.UNIQUENAME.value).asString()
//        jsonFeature.put(FeatureStringEnum.UNIQUENAME.value, neo4jFeature.get(FeatureStringEnum.UNIQUENAME.value).asString())
        if (neo4jFeature.get(FeatureStringEnum.NAME.value) != null) {
            feature.name = neo4jFeature.get(FeatureStringEnum.NAME.value).asString()
//            jsonFeature.put(FeatureStringEnum.NAME.value, neo4jFeature.get(FeatureStringEnum.NAME.value).asString())
        }
//        if (neo4jFeature.symbol) {
//            jsonFeature.put(FeatureStringEnum.SYMBOL.value, neo4jFeature.symbol)
//        }
//        if (neo4jFeature.status) {
//            jsonFeature.put(FeatureStringEnum.STATUS.value, neo4jFeature.status.value)
//        }
//        if (neo4jFeature.description) {
//            jsonFeature.put(FeatureStringEnum.DESCRIPTION.value, neo4jFeature.description)
//        }
//        if (neo4jFeature.featureSynonyms) {
//            jsonFeature.put(FeatureStringEnum.SYNONYMS.value, neo4jFeature.featureSynonyms.synonym.name)
//        }

        long start = System.currentTimeMillis()
//        String finalOwnerString = generateOwnerString(neo4jObject)
        if (neo4jOwners) {
            String finalOwnerString = neo4jOwners.collect { it.get(FeatureStringEnum.USERNAME.value).asString() }.join(" ")
//        String finalOwnerString = "asdfasdf"
//            feature.owners =
//            for(def owner in neo4jOwners){
//                feature.addToOwners(owner)
//                log.debug "owner ${owner} "
//                log.debug "value ${owner.get(FeatureStringEnum.USERNAME.value)} "
//            }
//        neo4jOwners.each {
//            log.debug it
//        }

//            log.debug "final owner string ${finalOwnerString}"
//            jsonFeature.put(FeatureStringEnum.OWNER.value.toLowerCase(), finalOwnerString)
//            feature.
        }

        long durationInMilliseconds = System.currentTimeMillis() - start

        start = System.currentTimeMillis()


//        if (neo4jObject.featureLocation) {
//            Sequence sequence = neo4jObject.featureLocation.to
//        if(neo4jSequence){
//            jsonFeature.put(FeatureStringEnum.SEQUENCE.value, neo4jSequence.get(FeatureStringEnum.NAME.value).asString())
//        }
//        }
//        log.debug "added sequence name : . . . ${jsonFeature.sequence}"

//        if (neo4jObject.goAnnotations) {
//            JSONArray goAnnotationsArray = goAnnotationService.convertAnnotationsToJson(neo4jObject.goAnnotations)
//            jsonFeature.put(FeatureStringEnum.GO_ANNOTATIONS.value, goAnnotationsArray)
//        }

        durationInMilliseconds = System.currentTimeMillis() - start


        start = System.currentTimeMillis();

        // get children
//        List<Feature> childFeatures = featureRelationshipService.getChildrenForFeatureAndTypes(neo4jObject)


        durationInMilliseconds = System.currentTimeMillis() - start;
//        log.debug"has a child ${neo4jChildren} . . ${neo4jChildren == null} ${neo4jChildren[0].feature}"
        if (neo4jChildren != null && neo4jChildren[0].feature != null) {
//            JSONArray children = new JSONArray();
//            jsonFeature.put(FeatureStringEnum.CHILDREN.value, children);
            for (def childNode : neo4jChildren) {
//                log.debug "each child labels ${child.labels().join(", ")}"
//                log.debug "each child keys ${child.keys().join(", ")}"
////                Feature childFeature = f
////                children.put(convertFeatureToJSON(childFeature, includeSequence));
                Feature child = convertNeo4jFeatureToFeature(childNode.feature, includeSequence)
                FeatureRelationship parentFeatureRelationship = new FeatureRelationship(from: feature, to: child)
                feature.addToParentFeatureRelationships(parentFeatureRelationship);
            }
        }


        start = System.currentTimeMillis()
        // get parents
//        log.debug "neo4j parents ${neo4jParent}"
//        List<Feature> parentFeatures = featureRelationshipService.getParentsForFeature(neo4jObject)

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "parents ${durationInMilliseconds}"
        if (neo4jParent != null && neo4jParent.feature != null) {
//            Feature parent = parentFeatures.iterator().next();
            def parentTypes = neo4jParent.feature.labels()
            def parentType = parentTypes.last()
            Feature parent = convertNeo4jFeatureToFeature(neo4jParent.feature, includeSequence)
            FeatureRelationship childFeatureRelationship = new FeatureRelationship(from: parent, to: feature)
            feature.addToChildFeatureRelationships(childFeatureRelationship);

//            jsonFeature.put(FeatureStringEnum.PARENT_ID.value, neo4jParent.feature.get(FeatureStringEnum.ID.value).asString());
//            jsonFeature.put(FeatureStringEnum.PARENT_NAME.value, neo4jParent.feature.get(FeatureStringEnum.NAME.value).asString());
////            jsonFeature.put(FeatureStringEnum.PARENT_TYPE.value, generateJSONFeatureStringForType(parent.ontologyId));
//            jsonFeature.put(FeatureStringEnum.PARENT_TYPE.value, parentType);
        }


        start = System.currentTimeMillis()

//        Collection<FeatureLocation> featureLocations = neo4jObject.getFeatureLocations();
        if (neo4jLocation) {

//            FeatureLocation gsolFeatureLocation = neo4jLocation.iterator().next();
//            if (gsolFeatureLocation != null) {
//            feature.put(FeatureStringEnum.LOCATION.value, convertNeo4jFeatureLocationToJSON(neo4jLocation))
//            jsonFeature.put(FeatureStringEnum.LOCATION.value, convertNeo4jFeatureLocationToJSON(neo4jLocation))
            feature.setFeatureLocation(convertNeo4jFeatureLocationToFeatureLocation(neo4jLocation))
//            }
        }

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "featloc ${durationInMilliseconds}"

//        if (neo4jObject instanceof SequenceAlteration) {
//            JSONArray alternateAllelesArray = new JSONArray()
//            neo4jObject.alleles.each { allele ->
//                JSONObject alleleObject = new JSONObject()
//                alleleObject.put(FeatureStringEnum.BASES.value, allele.bases)
////                if (allele.alleleFrequency) {
////                    alternateAlleleObject.put(FeatureStringEnum.ALLELE_FREQUENCY.value, String.valueOf(allele.alleleFrequency))
////                }
////                if (allele.provenance) {
////                    alternateAlleleObject.put(FeatureStringEnum.PROVENANCE.value, allele.provenance);
////                }
//                if (allele.alleleInfo) {
//                    JSONArray alleleInfoArray = new JSONArray()
//                    allele.alleleInfo.each { alleleInfo ->
//                        JSONObject alleleInfoObject = new JSONObject()
//                        alleleInfoObject.put(FeatureStringEnum.TAG.value, alleleInfo.tag)
//                        alleleInfoObject.put(FeatureStringEnum.VALUE.value, alleleInfo.value)
//                        alleleInfoArray.add(alleleInfoObject)
//                    }
//                    alleleObject.put(FeatureStringEnum.ALLELE_INFO.value, alleleInfoArray)
//                }
//                if (allele.reference) {
//                    jsonFeature.put(FeatureStringEnum.REFERENCE_ALLELE.value, alleleObject)
//                } else {
//                    alternateAllelesArray.add(alleleObject)
//                }
//            }
//
//            jsonFeature.put(FeatureStringEnum.ALTERNATE_ALLELES.value, alternateAllelesArray)
//
//            if (neo4jObject.variantInfo) {
//                JSONArray variantInfoArray = new JSONArray()
//                neo4jObject.variantInfo.each { variantInfo ->
//                    JSONObject variantInfoObject = new JSONObject()
//                    variantInfoObject.put(FeatureStringEnum.TAG.value, variantInfo.tag)
//                    variantInfoObject.put(FeatureStringEnum.VALUE.value, variantInfo.value)
//                    variantInfoArray.add(variantInfoObject)
//                }
//                jsonFeature.put(FeatureStringEnum.VARIANT_INFO.value, variantInfoArray)
//            }
//        }

//        if (neo4jObject instanceof SequenceAlterationArtifact) {
//            SequenceAlterationArtifact sequenceAlteration = (SequenceAlterationArtifact) neo4jObject
//            if (sequenceAlteration.alterationResidue) {
//                jsonFeature.put(FeatureStringEnum.RESIDUES.value, sequenceAlteration.alterationResidue);
//            }
//        } else
//        if (includeSequence) {
//            String residues = sequenceService.getResiduesFromFeature(neo4jObject)
//            if (residues) {
//                jsonFeature.put(FeatureStringEnum.RESIDUES.value, residues);
//            }
//        }

        //e.g. properties: [{value: "demo", type: {name: "owner", cv: {name: "feature_property"}}}]
//        Collection<FeatureProperty> gsolFeatureProperties = neo4jObject.getFeatureProperties();
//
//        JSONArray properties = new JSONArray();
//        jsonFeature.put(FeatureStringEnum.PROPERTIES.value, properties);
//        if (gsolFeatureProperties) {
//            for (FeatureProperty property : gsolFeatureProperties) {
//                JSONObject jsonProperty = new JSONObject();
//                JSONObject jsonPropertyType = new JSONObject()
//                if (property instanceof Comment) {
//                    JSONObject jsonPropertyTypeCv = new JSONObject()
//                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
//                    jsonProperty.put(FeatureStringEnum.NAME.value, FeatureStringEnum.COMMENT.value);
//                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
//                    properties.put(jsonProperty);
//                    continue
//                }
//                if (property.tag == "justification") {
//                    JSONObject jsonPropertyTypeCv = new JSONObject()
//                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
//                    jsonProperty.put(FeatureStringEnum.NAME.value, "justification");
//                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
//                    properties.put(jsonProperty);
//                    continue
//                }
//                jsonPropertyType.put(FeatureStringEnum.NAME.value, property.type)
//                JSONObject jsonPropertyTypeCv = new JSONObject()
//                jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType)
//                jsonProperty.put(FeatureStringEnum.NAME.value, property.getTag())
//                jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue())
//                properties.put(jsonProperty)
//            }
//        }
//        JSONObject ownerProperty = JSON.parse("{value: ${finalOwnerString}, type: {name: 'owner', cv: {name: 'feature_property'}}}") as JSONObject
//        properties.put(ownerProperty)


//        Collection<DBXref> gsolFeatureDbxrefs = neo4jObject.getFeatureDBXrefs()
//        if (gsolFeatureDbxrefs) {
//            JSONArray dbxrefs = new JSONArray();
//            jsonFeature.put(FeatureStringEnum.DBXREFS.value, dbxrefs)
//            for (DBXref gsolDbxref : gsolFeatureDbxrefs) {
//                JSONObject dbxref = new JSONObject()
//                dbxref.put(FeatureStringEnum.ACCESSION.value, gsolDbxref.getAccession())
//                dbxref.put(FeatureStringEnum.DB.value, new JSONObject().put(FeatureStringEnum.NAME.value, gsolDbxref.getDb().getName()))
//                dbxrefs.put(dbxref)
//            }
//        }
//        log.debug "date . . . ${neo4jFeature.lastUpdated} ${neo4jFeature.lastUpdated.time}"
        feature.lastUpdated = new Date(neo4jFeature.get("lastUpdated").asLong())
        feature.dateCreated = new Date(neo4jFeature.get("dateCreated").asLong())
//        jsonFeature.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, neo4jFeature.get("lastUpdated").asLong())
//        jsonFeature.put(FeatureStringEnum.DATE_CREATION.value, neo4jFeature.get("dateCreated").asLong())
        return feature
    }

    /**
     * @param neo4jObject
     * @param includeSequence
     * @return
     */
    JSONObject convertNeo4jFeatureToJSON(def neo4jObject, boolean includeSequence = false) {
//        log.debug "converting features to json ${neo4jObject}"
        def neo4jFeature = neo4jObject.feature
//        log.debug "feature ${neo4jFeature} "
//        log.debug "feature as JSON ${neo4jFeature} "
        def neo4jLocation = neo4jObject.location
//        log.debug "locaitons ${neo4jLocation} "
        def neo4jOwners = neo4jObject.owners
//        log.debug "owners ${neo4jOwners} "
        def neo4jSequence = neo4jObject.sequence

        def neo4jChildren = neo4jObject.children
//        log.debug "children ${neo4jChildren} "

        def neo4jParent = neo4jObject.parent
//        log.debug "parent ${neo4jParent} "


        JSONObject jsonFeature = new JSONObject()
//        log.debug "ID ${neo4jFeature.id()}"
//        log.debug "labels ${neo4jFeature.labels().join(",")}"
//        log.debug "keys ${neo4jFeature.keys().join(",")}"
        if (neo4jFeature.id() != null) {
            jsonFeature.put(FeatureStringEnum.ID.value, neo4jFeature.id())
        }
//        def types = neo4jFeature.labels()
        String type = getCvTermFromNeo4jFeature(neo4jFeature) // TODO: find a better way for this to get the most specific type
        jsonFeature.put(FeatureStringEnum.TYPE.value, type)

        jsonFeature.put(FeatureStringEnum.UNIQUENAME.value, neo4jFeature.get(FeatureStringEnum.UNIQUENAME.value).asString())
        if (neo4jFeature.get(FeatureStringEnum.NAME.value) != null) {
            jsonFeature.put(FeatureStringEnum.NAME.value, neo4jFeature.get(FeatureStringEnum.NAME.value).asString())
        }
//        if (neo4jFeature.symbol) {
//            jsonFeature.put(FeatureStringEnum.SYMBOL.value, neo4jFeature.symbol)
//        }
//        if (neo4jFeature.status) {
//            jsonFeature.put(FeatureStringEnum.STATUS.value, neo4jFeature.status.value)
//        }
//        if (neo4jFeature.description) {
//            jsonFeature.put(FeatureStringEnum.DESCRIPTION.value, neo4jFeature.description)
//        }
//        if (neo4jFeature.featureSynonyms) {
//            jsonFeature.put(FeatureStringEnum.SYNONYMS.value, neo4jFeature.featureSynonyms.synonym.name)
//        }

        long start = System.currentTimeMillis()
//        String finalOwnerString = generateOwnerString(neo4jObject)
        if (neo4jOwners) {
            String finalOwnerString = neo4jOwners.collect { it.get(FeatureStringEnum.USERNAME.value).asString() }.join(" ")
//        String finalOwnerString = "asdfasdf"
//            for (def owner in neo4jOwners) {
//                log.debug "owner ${owner} "
//                log.debug "value ${owner.get(FeatureStringEnum.USERNAME.value)} "
//            }
//        neo4jOwners.each {
//            log.debug it
//        }

//            log.debug "final owner string ${finalOwnerString}"
            jsonFeature.put(FeatureStringEnum.OWNER.value.toLowerCase(), finalOwnerString)
        }

        long durationInMilliseconds = System.currentTimeMillis() - start

        start = System.currentTimeMillis()


//        if (neo4jObject.featureLocation) {
//            Sequence sequence = neo4jObject.featureLocation.to
        if (neo4jSequence) {
            jsonFeature.put(FeatureStringEnum.SEQUENCE.value, neo4jSequence.get(FeatureStringEnum.NAME.value).asString())
        }
//        }
//        log.debug "added sequence name : . . . ${jsonFeature.sequence}"

//        if (neo4jObject.goAnnotations) {
//            JSONArray goAnnotationsArray = goAnnotationService.convertAnnotationsToJson(neo4jObject.goAnnotations)
//            jsonFeature.put(FeatureStringEnum.GO_ANNOTATIONS.value, goAnnotationsArray)
//        }

        durationInMilliseconds = System.currentTimeMillis() - start


        start = System.currentTimeMillis();

        // get children
//        List<Feature> childFeatures = featureRelationshipService.getChildrenForFeatureAndTypes(neo4jObject)


        durationInMilliseconds = System.currentTimeMillis() - start;
//        log.debug"has a child ${neo4jChildren} . . ${neo4jChildren == null} ${neo4jChildren[0].feature}"
        if (neo4jChildren != null && neo4jChildren[0].feature != null) {
//            log.debug "finding children"
            JSONArray children = new JSONArray();
            jsonFeature.put(FeatureStringEnum.CHILDREN.value, children);
            for (def child : neo4jChildren) {
//                log.debug "each child ${child}"
//                log.debug "each child labels ${child.labels().join(", ")}"
//                log.debug "each child keys ${child.keys().join(", ")}"
////                Feature childFeature = f
////                children.put(convertFeatureToJSON(childFeature, includeSequence));
                children.put(convertNeo4jFeatureToJSON(child, includeSequence));
            }
        }


        start = System.currentTimeMillis()
        // get parents
        log.debug "neo4j parents ${neo4jParent}"
//        List<Feature> parentFeatures = featureRelationshipService.getParentsForFeature(neo4jObject)

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "parents ${durationInMilliseconds}"
        if (neo4jParent != null && neo4jParent.feature != null) {
//            Feature parent = parentFeatures.iterator().next();
            def parentTypes = neo4jParent.feature.labels()
            def parentType = parentTypes.last()
            jsonFeature.put(FeatureStringEnum.PARENT_ID.value, neo4jParent.feature.get(FeatureStringEnum.ID.value).asString());
            jsonFeature.put(FeatureStringEnum.PARENT_NAME.value, neo4jParent.feature.get(FeatureStringEnum.NAME.value).asString());
//            jsonFeature.put(FeatureStringEnum.PARENT_TYPE.value, generateJSONFeatureStringForType(parent.ontologyId));
            jsonFeature.put(FeatureStringEnum.PARENT_TYPE.value, parentType);
        }


        start = System.currentTimeMillis()

//        Collection<FeatureLocation> featureLocations = neo4jObject.getFeatureLocations();
        if (neo4jLocation) {

//            FeatureLocation gsolFeatureLocation = neo4jLocation.iterator().next();
//            if (gsolFeatureLocation != null) {
            jsonFeature.put(FeatureStringEnum.LOCATION.value, convertNeo4jFeatureLocationToJSON(neo4jLocation))
//            }
        }

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "featloc ${durationInMilliseconds}"

//        if (neo4jObject instanceof SequenceAlteration) {
//            JSONArray alternateAllelesArray = new JSONArray()
//            neo4jObject.alleles.each { allele ->
//                JSONObject alleleObject = new JSONObject()
//                alleleObject.put(FeatureStringEnum.BASES.value, allele.bases)
////                if (allele.alleleFrequency) {
////                    alternateAlleleObject.put(FeatureStringEnum.ALLELE_FREQUENCY.value, String.valueOf(allele.alleleFrequency))
////                }
////                if (allele.provenance) {
////                    alternateAlleleObject.put(FeatureStringEnum.PROVENANCE.value, allele.provenance);
////                }
//                if (allele.alleleInfo) {
//                    JSONArray alleleInfoArray = new JSONArray()
//                    allele.alleleInfo.each { alleleInfo ->
//                        JSONObject alleleInfoObject = new JSONObject()
//                        alleleInfoObject.put(FeatureStringEnum.TAG.value, alleleInfo.tag)
//                        alleleInfoObject.put(FeatureStringEnum.VALUE.value, alleleInfo.value)
//                        alleleInfoArray.add(alleleInfoObject)
//                    }
//                    alleleObject.put(FeatureStringEnum.ALLELE_INFO.value, alleleInfoArray)
//                }
//                if (allele.reference) {
//                    jsonFeature.put(FeatureStringEnum.REFERENCE_ALLELE.value, alleleObject)
//                } else {
//                    alternateAllelesArray.add(alleleObject)
//                }
//            }
//
//            jsonFeature.put(FeatureStringEnum.ALTERNATE_ALLELES.value, alternateAllelesArray)
//
//            if (neo4jObject.variantInfo) {
//                JSONArray variantInfoArray = new JSONArray()
//                neo4jObject.variantInfo.each { variantInfo ->
//                    JSONObject variantInfoObject = new JSONObject()
//                    variantInfoObject.put(FeatureStringEnum.TAG.value, variantInfo.tag)
//                    variantInfoObject.put(FeatureStringEnum.VALUE.value, variantInfo.value)
//                    variantInfoArray.add(variantInfoObject)
//                }
//                jsonFeature.put(FeatureStringEnum.VARIANT_INFO.value, variantInfoArray)
//            }
//        }

//        if (neo4jObject instanceof SequenceAlterationArtifact) {
//            SequenceAlterationArtifact sequenceAlteration = (SequenceAlterationArtifact) neo4jObject
//            if (sequenceAlteration.alterationResidue) {
//                jsonFeature.put(FeatureStringEnum.RESIDUES.value, sequenceAlteration.alterationResidue);
//            }
//        } else
        if (includeSequence) {
            String residues = sequenceService.getResiduesFromFeature(neo4jObject)
            if (residues) {
                jsonFeature.put(FeatureStringEnum.RESIDUES.value, residues);
            }
        }

        //e.g. properties: [{value: "demo", type: {name: "owner", cv: {name: "feature_property"}}}]
//        Collection<FeatureProperty> gsolFeatureProperties = neo4jObject.getFeatureProperties();
//
//        JSONArray properties = new JSONArray();
//        jsonFeature.put(FeatureStringEnum.PROPERTIES.value, properties);
//        if (gsolFeatureProperties) {
//            for (FeatureProperty property : gsolFeatureProperties) {
//                JSONObject jsonProperty = new JSONObject();
//                JSONObject jsonPropertyType = new JSONObject()
//                if (property instanceof Comment) {
//                    JSONObject jsonPropertyTypeCv = new JSONObject()
//                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
//                    jsonProperty.put(FeatureStringEnum.NAME.value, FeatureStringEnum.COMMENT.value);
//                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
//                    properties.put(jsonProperty);
//                    continue
//                }
//                if (property.tag == "justification") {
//                    JSONObject jsonPropertyTypeCv = new JSONObject()
//                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
//                    jsonProperty.put(FeatureStringEnum.NAME.value, "justification");
//                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
//                    properties.put(jsonProperty);
//                    continue
//                }
//                jsonPropertyType.put(FeatureStringEnum.NAME.value, property.type)
//                JSONObject jsonPropertyTypeCv = new JSONObject()
//                jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
//                jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)
//
//                jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType)
//                jsonProperty.put(FeatureStringEnum.NAME.value, property.getTag())
//                jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue())
//                properties.put(jsonProperty)
//            }
//        }
//        JSONObject ownerProperty = JSON.parse("{value: ${finalOwnerString}, type: {name: 'owner', cv: {name: 'feature_property'}}}") as JSONObject
//        properties.put(ownerProperty)


//        Collection<DBXref> gsolFeatureDbxrefs = neo4jObject.getFeatureDBXrefs()
//        if (gsolFeatureDbxrefs) {
//            JSONArray dbxrefs = new JSONArray();
//            jsonFeature.put(FeatureStringEnum.DBXREFS.value, dbxrefs)
//            for (DBXref gsolDbxref : gsolFeatureDbxrefs) {
//                JSONObject dbxref = new JSONObject()
//                dbxref.put(FeatureStringEnum.ACCESSION.value, gsolDbxref.getAccession())
//                dbxref.put(FeatureStringEnum.DB.value, new JSONObject().put(FeatureStringEnum.NAME.value, gsolDbxref.getDb().getName()))
//                dbxrefs.put(dbxref)
//            }
//        }
//        log.debug "date . . . ${neo4jFeature.lastUpdated} ${neo4jFeature.lastUpdated.time}"
        jsonFeature.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, neo4jFeature.get("lastUpdated").asLong())
        jsonFeature.put(FeatureStringEnum.DATE_CREATION.value, neo4jFeature.get("dateCreated").asLong())
        return jsonFeature
    }

    /**
     * @param inputFeature
     * @param includeSequence
     * @return
     */

    JSONObject convertFeatureToJSONLite(Feature inputFeature, boolean includeSequence = false, int depth) {
        JSONObject jsonFeature = new JSONObject();
        if (inputFeature.id) {
            jsonFeature.put(FeatureStringEnum.ID.value, inputFeature.id);
        }
        jsonFeature.put(FeatureStringEnum.TYPE.value, generateJSONFeatureStringForType(inputFeature.ontologyId));
        jsonFeature.put(FeatureStringEnum.UNIQUENAME.value, inputFeature.getUniqueName());
        if (inputFeature.getName() != null) {
            jsonFeature.put(FeatureStringEnum.NAME.value, inputFeature.getName());
        }
        if (inputFeature.symbol) {
            jsonFeature.put(FeatureStringEnum.SYMBOL.value, inputFeature.symbol);
        }
        if (inputFeature.description) {
            jsonFeature.put(FeatureStringEnum.DESCRIPTION.value, inputFeature.description);
        }
        if (inputFeature.featureSynonyms) {
            String synonymString = ""
            for (def fs in inputFeature.featureSynonyms) {
                synonymString += "|" + fs.synonym.name
            }
            jsonFeature.put(FeatureStringEnum.SYNONYMS.value, synonymString.substring(1));
        }
        if (inputFeature.status) {
            jsonFeature.put(FeatureStringEnum.STATUS.value, inputFeature.status.value)
        }
        if (inputFeature.featureDBXrefs) {
            jsonFeature.put(FeatureStringEnum.DBXREFS.value, generateFeatureForDBXrefs(inputFeature.featureDBXrefs))
        }
        if (inputFeature.featureProperties) {
            jsonFeature.put(FeatureStringEnum.COMMENTS.value, generateFeatureForComments(inputFeature.featureProperties))
            jsonFeature.put(FeatureStringEnum.ATTRIBUTES.value, generateFeatureForFeatureProperties(inputFeature.featureProperties))
        }

        if (inputFeature.instanceOf(SequenceAlteration.class)) {

            // TODO: optimize
            // variant info (properties)
            if (inputFeature.getVariantInfo()) {
                JSONArray variantInfoArray = new JSONArray()
                inputFeature.variantInfo.each { variantInfo ->
                    JSONObject variantInfoObject = new JSONObject()
                    variantInfoObject.put(FeatureStringEnum.TAG.value, variantInfo.tag)
                    variantInfoObject.put(FeatureStringEnum.VALUE.value, variantInfo.value)
                    variantInfoArray.add(variantInfoObject)
                }
                jsonFeature.put(FeatureStringEnum.VARIANT_INFO.value, variantInfoArray)
            }

            // TODO: optimize
            JSONArray alternateAllelesArray = new JSONArray()
            inputFeature.alleles.each { allele ->
                JSONObject alleleObject = new JSONObject()
                alleleObject.put(FeatureStringEnum.BASES.value, allele.bases)
                if (allele.alleleInfo) {
                    JSONArray alleleInfoArray = new JSONArray()
                    allele.alleleInfo.each { alleleInfo ->
                        JSONObject alleleInfoObject = new JSONObject()
                        alleleInfoObject.put(FeatureStringEnum.TAG.value, alleleInfo.tag)
                        alleleInfoObject.put(FeatureStringEnum.VALUE.value, alleleInfo.value)
                        alleleInfoArray.add(alleleInfoObject)
                    }
                    alleleObject.put(FeatureStringEnum.ALLELE_INFO.value, alleleInfoArray)
                }
                if (allele.reference) {
                    jsonFeature.put(FeatureStringEnum.REFERENCE_ALLELE.value, alleleObject)
                } else {
                    alternateAllelesArray.add(alleleObject)
                }
            }
            jsonFeature.put(FeatureStringEnum.ALTERNATE_ALLELES.value, alternateAllelesArray)
        }

        long start = System.currentTimeMillis();
        if (depth <= 1) {
            String finalOwnerString
            if (inputFeature.owners) {
                String ownerString = ""
                for (owner in inputFeature.owners) {
                    ownerString += inputFeature.owner.username + " "
                }
                finalOwnerString = ownerString?.trim()
            } else if (inputFeature.owner) {
                finalOwnerString = inputFeature?.owner?.username
            } else {
                finalOwnerString = "None"
            }
            jsonFeature.put(FeatureStringEnum.OWNER.value.toLowerCase(), finalOwnerString);
        }

        if (inputFeature.featureLocation) {
            jsonFeature.put(FeatureStringEnum.SEQUENCE.value, inputFeature.featureLocation.to.name);
            jsonFeature.put(FeatureStringEnum.LOCATION.value, convertFeatureLocationToJSON(inputFeature.featureLocation));
        }


        if (depth <= 1) {
            List<Feature> childFeatures = featureRelationshipService.getChildrenForFeatureAndTypes(inputFeature)
            if (childFeatures) {
                JSONArray children = new JSONArray();
                jsonFeature.put(FeatureStringEnum.CHILDREN.value, children);
                for (Feature f : childFeatures) {
                    Feature childFeature = f
                    children.put(convertFeatureToJSONLite(childFeature, includeSequence, depth + 1));
                }
            }
        }


        jsonFeature.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, inputFeature.lastUpdated.time);
        jsonFeature.put(FeatureStringEnum.DATE_CREATION.value, inputFeature.dateCreated.time);
        return jsonFeature;
    }

    String generateOwnerString(Feature feature) {
        if (feature.owner) {
            return feature.owner.username
        }
        if (feature.owners) {
            String ownerString = ""
            for (owner in feature.owners) {
                ownerString += owner.username + " "
            }
            return ownerString
        }
        return "None"
    }

    /**
     * @param inputFeature
     * @param includeSequence
     * @return
     */

    JSONObject convertFeatureToJSON(Feature inputFeature, boolean includeSequence = false) {
//        log.debug "converting features to json ${inputFeature}"
        JSONObject jsonFeature = new JSONObject()
        if (inputFeature.id) {
            jsonFeature.put(FeatureStringEnum.ID.value, inputFeature.id)
        }
        jsonFeature.put(FeatureStringEnum.TYPE.value, generateJSONFeatureStringForType(inputFeature.ontologyId))
        jsonFeature.put(FeatureStringEnum.UNIQUENAME.value, inputFeature.getUniqueName())
        if (inputFeature.getName() != null) {
            jsonFeature.put(FeatureStringEnum.NAME.value, inputFeature.getName())
        }
        if (inputFeature.symbol) {
            jsonFeature.put(FeatureStringEnum.SYMBOL.value, inputFeature.symbol)
        }
        def statusValue = Status.executeQuery("MATCH (f:Feature)--(s:Status) where f.uniqueName = ${inputFeature.uniqueName} return s.value")
        if (statusValue.size()>0) {
            jsonFeature.put(FeatureStringEnum.STATUS.value, statusValue)
        }
        if (inputFeature.description) {
            jsonFeature.put(FeatureStringEnum.DESCRIPTION.value, inputFeature.description)
        }
        if (inputFeature.featureSynonyms) {
            jsonFeature.put(FeatureStringEnum.SYNONYMS.value, inputFeature.featureSynonyms.synonym.name)
        }

        long start = System.currentTimeMillis()
        String finalOwnerString = generateOwnerString(inputFeature)
        jsonFeature.put(FeatureStringEnum.OWNER.value.toLowerCase(), finalOwnerString)

        long durationInMilliseconds = System.currentTimeMillis() - start

        start = System.currentTimeMillis()
        if (inputFeature.featureLocation) {
            def sequenceNodes = Feature.executeQuery("MATCH (f:Feature)-[fl:FEATURELOCATION]-(s:Sequence) where f.uniqueName=${inputFeature.uniqueName} return s limit 1")
            log.debug "sequence node ${sequenceNodes} and ${sequenceNodes.size()}"
            Sequence sequence = sequenceNodes[0]  as Sequence
            if(sequence!=null){
                jsonFeature.put(FeatureStringEnum.SEQUENCE.value, sequence.name)
            }
        }

        if (inputFeature.goAnnotations) {
            JSONArray goAnnotationsArray = goAnnotationService.convertAnnotationsToJson(inputFeature.goAnnotations)
            jsonFeature.put(FeatureStringEnum.GO_ANNOTATIONS.value, goAnnotationsArray)
        }

        durationInMilliseconds = System.currentTimeMillis() - start


        start = System.currentTimeMillis();

        // get children
        List<Feature> childFeatures = featureRelationshipService.getChildrenForFeatureAndTypes(inputFeature)


        durationInMilliseconds = System.currentTimeMillis() - start;
        if (childFeatures) {
            JSONArray children = new JSONArray();
            jsonFeature.put(FeatureStringEnum.CHILDREN.value, children);
            for (Feature f : childFeatures) {
                Feature childFeature = f
                children.put(convertFeatureToJSON(childFeature, includeSequence));
            }
        }


        start = System.currentTimeMillis()
        // get parents
        List<Feature> parentFeatures = featureRelationshipService.getParentsForFeature(inputFeature)

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "parents ${durationInMilliseconds}"
        if (parentFeatures?.size() == 1) {
            Feature parent = parentFeatures.iterator().next();
            jsonFeature.put(FeatureStringEnum.PARENT_ID.value, parent.getUniqueName());
            jsonFeature.put(FeatureStringEnum.PARENT_NAME.value, parent.getName());
            jsonFeature.put(FeatureStringEnum.PARENT_TYPE.value, generateJSONFeatureStringForType(parent.ontologyId));
        }


        start = System.currentTimeMillis()

        def featureLocationNodes = FeatureLocation.executeQuery("MATCH (f:Feature)-[fl:FEATURELOCATION]-(s:Sequence) where f.uniqueName = ${inputFeature.uniqueName} return fl")
        if (featureLocationNodes.size()>0) {
//            FeatureLocation featureLocation = inputFeature.featureLocation
            FeatureLocation featureLocation = featureLocationNodes[0] as FeatureLocation
            if (featureLocation != null) {
                jsonFeature.put(FeatureStringEnum.LOCATION.value, convertFeatureLocationToJSON(featureLocation));
            }
        }

        durationInMilliseconds = System.currentTimeMillis() - start;
        //log.debug "featloc ${durationInMilliseconds}"

        if (inputFeature.instanceOf(SequenceAlteration.class)) {
            JSONArray alternateAllelesArray = new JSONArray()
            inputFeature.alleles.each { allele ->
                JSONObject alleleObject = new JSONObject()
                alleleObject.put(FeatureStringEnum.BASES.value, allele.bases)
//                if (allele.alleleFrequency) {
//                    alternateAlleleObject.put(FeatureStringEnum.ALLELE_FREQUENCY.value, String.valueOf(allele.alleleFrequency))
//                }
//                if (allele.provenance) {
//                    alternateAlleleObject.put(FeatureStringEnum.PROVENANCE.value, allele.provenance);
//                }
                if (allele.alleleInfo) {
                    JSONArray alleleInfoArray = new JSONArray()
                    allele.alleleInfo.each { alleleInfo ->
                        JSONObject alleleInfoObject = new JSONObject()
                        alleleInfoObject.put(FeatureStringEnum.TAG.value, alleleInfo.tag)
                        alleleInfoObject.put(FeatureStringEnum.VALUE.value, alleleInfo.value)
                        alleleInfoArray.add(alleleInfoObject)
                    }
                    alleleObject.put(FeatureStringEnum.ALLELE_INFO.value, alleleInfoArray)
                }
                if (allele.reference) {
                    jsonFeature.put(FeatureStringEnum.REFERENCE_ALLELE.value, alleleObject)
                } else {
                    alternateAllelesArray.add(alleleObject)
                }
            }

            jsonFeature.put(FeatureStringEnum.ALTERNATE_ALLELES.value, alternateAllelesArray)

            if (inputFeature.variantInfo) {
                JSONArray variantInfoArray = new JSONArray()
                inputFeature.variantInfo.each { variantInfo ->
                    JSONObject variantInfoObject = new JSONObject()
                    variantInfoObject.put(FeatureStringEnum.TAG.value, variantInfo.tag)
                    variantInfoObject.put(FeatureStringEnum.VALUE.value, variantInfo.value)
                    variantInfoArray.add(variantInfoObject)
                }
                jsonFeature.put(FeatureStringEnum.VARIANT_INFO.value, variantInfoArray)
            }
        }

        if (inputFeature.instanceOf(SequenceAlterationArtifact.class)) {
            SequenceAlterationArtifact sequenceAlteration = (SequenceAlterationArtifact) inputFeature
            if (sequenceAlteration.alterationResidue) {
                jsonFeature.put(FeatureStringEnum.RESIDUES.value, sequenceAlteration.alterationResidue);
            }
        } else if (includeSequence) {
            String residues = sequenceService.getResiduesFromFeature(inputFeature)
            if (residues) {
                jsonFeature.put(FeatureStringEnum.RESIDUES.value, residues);
            }
        }

        //e.g. properties: [{value: "demo", type: {name: "owner", cv: {name: "feature_property"}}}]
        Collection<FeatureProperty> featureProperties = inputFeature.getFeatureProperties();

        JSONArray properties = new JSONArray();
        jsonFeature.put(FeatureStringEnum.PROPERTIES.value, properties);
        if (featureProperties) {
            for (FeatureProperty property : featureProperties) {
                JSONObject jsonProperty = new JSONObject();
                JSONObject jsonPropertyType = new JSONObject()
                if (property.instanceOf(Comment.class)) {
                    JSONObject jsonPropertyTypeCv = new JSONObject()
                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)

                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
                    jsonProperty.put(FeatureStringEnum.NAME.value, FeatureStringEnum.COMMENT.value);
                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
                    properties.put(jsonProperty);
                    continue
                }
                if (property.tag == "justification") {
                    JSONObject jsonPropertyTypeCv = new JSONObject()
                    jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
                    jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)

                    jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType);
                    jsonProperty.put(FeatureStringEnum.NAME.value, "justification");
                    jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue());
                    properties.put(jsonProperty);
                    continue
                }
                jsonPropertyType.put(FeatureStringEnum.NAME.value, property.type)
                JSONObject jsonPropertyTypeCv = new JSONObject()
                jsonPropertyTypeCv.put(FeatureStringEnum.NAME.value, FeatureStringEnum.FEATURE_PROPERTY.value)
                jsonPropertyType.put(FeatureStringEnum.CV.value, jsonPropertyTypeCv)

                jsonProperty.put(FeatureStringEnum.TYPE.value, jsonPropertyType)
                jsonProperty.put(FeatureStringEnum.NAME.value, property.getTag())
                jsonProperty.put(FeatureStringEnum.VALUE.value, property.getValue())
                properties.put(jsonProperty)
            }
        }
//        JSONObject ownerProperty = JSON.parse("{value: ${finalOwnerString}, type: {name: 'owner', cv: {name: 'feature_property'}}}") as JSONObject
//        properties.put(ownerProperty)


        Collection<DBXref> featureDbxrefs = inputFeature.getFeatureDBXrefs()
        if (featureDbxrefs) {
            JSONArray dbxrefs = new JSONArray();
            jsonFeature.put(FeatureStringEnum.DBXREFS.value, dbxrefs)
            for (DBXref dBXref : featureDbxrefs) {
                JSONObject dbxrefJson = new JSONObject()
                dbxrefJson.put(FeatureStringEnum.ACCESSION.value, dBXref.getAccession())
                dbxrefJson.put(FeatureStringEnum.DB.value, new JSONObject().put(FeatureStringEnum.NAME.value, dBXref.getDb().getName()))
                dbxrefs.put(dbxrefJson)
            }
        }
        jsonFeature.put(FeatureStringEnum.DATE_LAST_MODIFIED.value, inputFeature.lastUpdated.time)
        jsonFeature.put(FeatureStringEnum.DATE_CREATION.value, inputFeature.dateCreated.time)
        return jsonFeature
    }

    JSONArray generateFeatureForDBXrefs(Collection<DBXref> dBXrefs) {
        JSONArray jsonArray = new JSONArray()
        for (DBXref dbXref in dBXrefs) {
            jsonArray.add(generateFeatureForDBXref(dbXref))
        }
        return jsonArray
    }

    JSONArray generateFeatureForFeatureProperties(Collection<FeatureProperty> featureProperties) {
        JSONArray jsonArray = new JSONArray()
        for (featureProperty in featureProperties) {
            if (featureProperty.cvTerm != Comment.cvTerm && featureProperty.cvTerm != Status.cvTerm) {
                jsonArray.add(generateFeatureForFeatureProperty(featureProperty))
            }
        }
        return jsonArray
    }

    JSONArray generateFeatureForComments(Collection<FeatureProperty> featureProperties) {
        JSONArray jsonArray = new JSONArray()
        for (featureProperty in featureProperties) {
            if (featureProperty.instanceOf(Comment.class)) {
                jsonArray.add(generateFeatureForComment((Comment) featureProperty))
            }
        }
        return jsonArray
    }

    JSONObject generateFeatureForComment(Comment comment) {
        JSONObject jsonObject = new JSONObject()
        jsonObject.put(FeatureStringEnum.COMMENT.value, comment.value)
        return jsonObject
    }

    JSONObject generateFeatureForDBXref(DBXref dBXref) {
        JSONObject jsonObject = new JSONObject()
        jsonObject.put(FeatureStringEnum.TAG.value, dBXref.db.name)
        jsonObject.put(FeatureStringEnum.VALUE.value, dBXref.accession)
        return jsonObject
    }

    JSONObject generateFeatureForFeatureProperty(FeatureProperty featureProperty) {
        JSONObject jsonObject = new JSONObject()
        jsonObject.put(FeatureStringEnum.TAG.value, featureProperty.tag)
        jsonObject.put(FeatureStringEnum.VALUE.value, featureProperty.value)
        return jsonObject
    }

    JSONObject generateJSONFeatureStringForType(String ontologyId) {
        if (ontologyId == null) return null;
        JSONObject jsonObject = new JSONObject();
        def feature = FeatureTypeMapper.generateFeatureForType(ontologyId)
        String cvTerm = feature.cvTerm

        jsonObject.put(FeatureStringEnum.NAME.value, cvTerm)

        JSONObject cvObject = new JSONObject()
        cvObject.put(FeatureStringEnum.NAME.value, FeatureStringEnum.SEQUENCE.value)
        jsonObject.put(FeatureStringEnum.CV.value, cvObject)

        return jsonObject
    }

    FeatureLocation convertNeo4jFeatureLocationToFeatureLocation(def featureLocationNode) {
        FeatureLocation featureLocation = new FeatureLocation()
//        log.debug " "
//        if (featureLocationNode.id) {
//            jsonFeatureLocation.put(FeatureStringEnum.ID.value, featureLocationNode.id);
//        }
//        log.debug "got an fmin ${featureLocationNode.get(FeatureStringEnum.FMIN.value).asLong()}"
        featureLocation.fmin = featureLocationNode.get(FeatureStringEnum.FMIN.value).asLong()
        featureLocation.fmax = featureLocationNode.get(FeatureStringEnum.FMAX.value).asLong()
        featureLocation.strand = featureLocationNode.get(FeatureStringEnum.STRAND.value).asInt()
//        jsonFeatureLocation.put(FeatureStringEnum.FMIN.value, featureLocationNode.get(FeatureStringEnum.FMIN.value).asLong())
//        jsonFeatureLocation.put(FeatureStringEnum.FMAX.value, featureLocationNode.get(FeatureStringEnum.FMAX.value).asLong())
//        if (featureLocationNode.isIsFminPartial()) {
//            jsonFeatureLocation.put(FeatureStringEnum.IS_FMIN_PARTIAL.value, true);
//        }
//        if (featureLocationNode.isIsFmaxPartial()) {
//            jsonFeatureLocation.put(FeatureStringEnum.IS_FMAX_PARTIAL.value, true);
//        }
//        jsonFeatureLocation.put(FeatureStringEnum.STRAND.value, featureLocationNode.get(FeatureStringEnum.STRAND.value).asInt())
        featureLocation.save(flush: true)
        return featureLocation
    }

    JSONObject convertNeo4jFeatureLocationToJSON(def featureLocationNode) throws JSONException {
        JSONObject jsonFeatureLocation = new JSONObject();
//        log.debug " "
//        if (featureLocationNode.id) {
//            jsonFeatureLocation.put(FeatureStringEnum.ID.value, featureLocationNode.id);
//        }
        log.debug "got an fmin ${featureLocationNode.get(FeatureStringEnum.FMIN.value).asLong()}"
        jsonFeatureLocation.put(FeatureStringEnum.FMIN.value, featureLocationNode.get(FeatureStringEnum.FMIN.value).asLong())
        jsonFeatureLocation.put(FeatureStringEnum.FMAX.value, featureLocationNode.get(FeatureStringEnum.FMAX.value).asLong())
//        if (featureLocationNode.isIsFminPartial()) {
//            jsonFeatureLocation.put(FeatureStringEnum.IS_FMIN_PARTIAL.value, true);
//        }
//        if (featureLocationNode.isIsFmaxPartial()) {
//            jsonFeatureLocation.put(FeatureStringEnum.IS_FMAX_PARTIAL.value, true);
//        }
        jsonFeatureLocation.put(FeatureStringEnum.STRAND.value, featureLocationNode.get(FeatureStringEnum.STRAND.value).asInt())
        return jsonFeatureLocation;
    }


    JSONObject convertFeatureLocationToJSON(FeatureLocation inputFeatureLocation) throws JSONException {
        JSONObject jsonFeatureLocation = new JSONObject();
        if (inputFeatureLocation.id) {
            jsonFeatureLocation.put(FeatureStringEnum.ID.value, inputFeatureLocation.id);
        }
        jsonFeatureLocation.put(FeatureStringEnum.FMIN.value, inputFeatureLocation.getFmin());
        jsonFeatureLocation.put(FeatureStringEnum.FMAX.value, inputFeatureLocation.getFmax());
        if (inputFeatureLocation.isIsFminPartial()) {
            jsonFeatureLocation.put(FeatureStringEnum.IS_FMIN_PARTIAL.value, true);
        }
        if (inputFeatureLocation.isIsFmaxPartial()) {
            jsonFeatureLocation.put(FeatureStringEnum.IS_FMAX_PARTIAL.value, true);
        }
        jsonFeatureLocation.put(FeatureStringEnum.STRAND.value, inputFeatureLocation.getStrand());
        return jsonFeatureLocation;
    }

    @Transactional
    Boolean deleteFeature(Feature feature, Map<String, List<Feature>> modifiedFeaturesUniqueNames = new HashMap<>()) {

        if (feature.instanceOf(Exon.class)) {
            Exon exon = (Exon) feature;
            Transcript transcript = (Transcript) Transcript.findByUniqueName(exonService.getTranscript(exon).getUniqueName());

            if (!(transcriptService.getGene(transcript).instanceOf(Pseudogene.class) && transcriptService.isProteinCoding(transcript))) {
                CDS cds = transcriptService.getCDS(transcript);
                if (cdsService.isManuallySetTranslationStart(cds)) {
                    int cdsStart = cds.getStrand() == -1 ? cds.getFmax() : cds.getFmin();
                    if (cdsStart >= exon.getFmin() && cdsStart <= exon.getFmax()) {
                        cdsService.setManuallySetTranslationStart(cds, false);
                    }
                }
            }

            exonService.deleteExon(transcript, exon)
            List<Feature> deletedFeatures = modifiedFeaturesUniqueNames.get(transcript.getUniqueName());
            if (deletedFeatures == null) {
                deletedFeatures = new ArrayList<Feature>();
                modifiedFeaturesUniqueNames.put(transcript.getUniqueName(), deletedFeatures);
            }
            deletedFeatures.add(exon);
            return transcriptService.getExons(transcript)?.size() > 0;
        } else {
            List<Feature> deletedFeatures = modifiedFeaturesUniqueNames.get(feature.getUniqueName());
            if (deletedFeatures == null) {
                deletedFeatures = new ArrayList<Feature>();
                modifiedFeaturesUniqueNames.put(feature.getUniqueName(), deletedFeatures);
            }
            deletedFeatures.add(feature);
            return false;
        }
    }


    @Transactional
    def flipStrand(Feature feature) {

        for (FeatureLocation featureLocation in feature.featureLocations) {
        feature.featureLocation.strand = featureLocation.strand == Strand.POSITIVE.value ? Strand.NEGATIVE.value : Strand.POSITIVE.value
        feature.featureLocation.save(flush: true)
        }

        for (Feature childFeature : feature?.parentFeatureRelationships?.childFeature) {
            flipStrand(childFeature)
        }

        feature.save(flush: true)
        return feature

    }

    boolean typeHasChildren(Feature feature) {
        def f = Feature.get(feature.id)
        boolean hasChildren = !(f.instanceOf(Exon.class) && !(f.instanceOf(CDS.class) && !(f.instanceOf(SpliceSite))))
        return hasChildren
    }

    /**
     * If genes is empty, create a new gene.
     * Else, merge
     * @param genes
     */
    @Transactional
    private Gene mergeGenes(Set<Gene> genes) {
        // TODO: implement
        Gene newGene = null

        if (!genes) {
            log.error "No genes to merge, returning null"
        }

        for (Gene gene in genes) {
            if (!newGene) {
                newGene = gene
            } else {
                // merging code goes here

            }
        }


        return newGene
    }

    /**
     * Remove old gene / transcript from the transcript
     * Delete gene if no overlapping.
     * @param transcript
     * @param gene
     */
    @Transactional
    private void setGeneTranscript(Transcript transcript, Gene gene) {
        Gene oldGene = transcriptService.getGene(transcript)
        if (gene.uniqueName == oldGene.uniqueName) {
            log.info "Same gene do not need to set"
            return
        }

        transcriptService.deleteTranscript(oldGene, transcript)
        addTranscriptToGene(gene, transcript)

        // if this is empty then delete the gene
        if (!featureRelationshipService.getChildren(oldGene)) {
            deleteFeature(oldGene)
        }
    }

    /**
     * From https://github.com/GMOD/Apollo/issues/73
     * Need to add another call after other calculations are done to verify that we verify that we have not left our current isoform siblings or that we have just joined some and we should merge genes (always taking the one on the left).
     1 - using OrfOverlapper, find other isoforms
     2 - for each isoform, confirm that they belong to the same gene (if not, we merge genes)
     3 - confirm that no other non-overlapping isoforms have the same gene (if not, we create a new gene)
     * @param transcript
     */
    @Transactional
    def handleIsoformOverlap(Transcript transcript) {
        Gene originalGene = transcriptService.getGene(transcript)

        // TODO: should go left to right, may need to sort
        List<Transcript> originalTranscripts = transcriptService.getTranscripts(originalGene)?.sort() { a, b ->
            a.featureLocation.fmin <=> b.featureLocation.fmin
        }
        List<Transcript> newTranscripts = getOverlappingTranscripts(transcript.featureLocation)?.sort() { a, b ->
            a.featureLocation.fmin <=> b.featureLocation.fmin
        };

        List<Transcript> leftBehindTranscripts = originalTranscripts - newTranscripts

        Set<Gene> newGenesToMerge = new HashSet<>()
        for (Transcript newTranscript in newTranscripts) {
            newGenesToMerge.add(transcriptService.getGene(newTranscript))
        }
        Gene newGene = newGenesToMerge ? mergeGenes(newGenesToMerge) : new Gene(
            name: transcript.name
            , uniqueName: nameService.generateUniqueName()
        ).save(flush: true, insert: true)

        for (Transcript newTranscript in newTranscripts) {
            setGeneTranscript(newTranscript, newGene)
        }


        Set<Gene> usedGenes = new HashSet<>()
        while (leftBehindTranscripts.size() > 0) {
            Transcript originalOverlappingTranscript = leftBehindTranscripts.pop()
            Gene originalOverlappingGene = transcriptService.getGene(originalOverlappingTranscript)
            List<Transcript> overlappingTranscripts = getOverlappingTranscripts(originalOverlappingTranscript.featureLocation)
            overlappingTranscripts = overlappingTranscripts - usedGenes
            overlappingTranscripts.each { it ->
                setGeneTranscript(it, originalOverlappingGene)
            }
            leftBehindTranscripts = leftBehindTranscripts - overlappingTranscripts
        }
    }

    @Transactional
    def handleDynamicIsoformOverlap(Transcript transcript) {
        // Get all transcripts that overlap transcript and verify if they have the proper parent gene assigned
        ArrayList<Transcript> transcriptsToUpdate = new ArrayList<Transcript>()
        List<Transcript> allOverlappingTranscripts = getOverlappingTranscripts(transcript)
        List<Transcript> allTranscriptsForCurrentGene = transcriptService.getTranscripts(transcriptService.getGene(transcript))
        List<Transcript> allTranscripts = (allOverlappingTranscripts + allTranscriptsForCurrentGene).unique()
        List<Transcript> allSortedTranscripts

        FeatureLocation featureLocation = FeatureLocation.findByFrom(transcript)

        // force null / 0 strand to be positive
        // when getting the up-most strand, make sure to put matching transcript strands BEFORE unmatching strands
        if (featureLocation.strand != Strand.NEGATIVE.value) {
            allSortedTranscripts = allTranscripts?.sort() { a, b ->
                a.featureLocation.strand <=> b.featureLocation.strand ?: a.featureLocation.fmin <=> b.featureLocation.fmin ?: a.name <=> b.name
            }
        } else {
            allSortedTranscripts = allTranscripts?.sort() { a, b ->
                b.featureLocation.strand <=> a.featureLocation.strand ?: b.featureLocation.fmax <=> a.featureLocation.fmax ?: a.name <=> b.name
            }
        }

        // remove exceptions
        List<Transcript> allSortedTranscriptsToCheck = new ArrayList<Transcript>()

        allSortedTranscripts.each {
            boolean safe = true
            if (!checkForComment(it, MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE) && !checkForComment(it, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)) {
                allSortedTranscriptsToCheck.add(it)
            }
        }

        // In a normal scenario, all sorted transcripts should have the same parent indicating no changes to be made.
        // If there are transcripts that do overlap but do not have the same parent gene then these transcripts should
        // be merged to the 5' most transcript's gene.
        // If there are transcripts that do not overlap but have the same parent gene then these transcripts should be
        // given a new, de-novo gene.
//        log.debug "all sorted transcripts: ${allSortedTranscriptsToCheck.name}"

        if (allSortedTranscriptsToCheck.size() > 0) {
            Transcript fivePrimeTranscript = allSortedTranscriptsToCheck.get(0)
            Gene fivePrimeGene = transcriptService.getGene(fivePrimeTranscript)
//            log.debug "5' Transcript: ${fivePrimeTranscript.name}"
//            log.debug "5' Gene: ${fivePrimeGene.name}"
            allSortedTranscriptsToCheck.remove(0)
            ArrayList<Transcript> transcriptsToAssociate = new ArrayList<Transcript>()
            ArrayList<Gene> genesToMerge = new ArrayList<Gene>()
            ArrayList<Transcript> transcriptsToDissociate = new ArrayList<Transcript>()

            for (Transcript eachTranscript : allSortedTranscriptsToCheck) {
                if (eachTranscript && fivePrimeGene && overlapperService.overlaps(eachTranscript, fivePrimeGene)) {
                    if (transcriptService.getGene(eachTranscript).uniqueName != fivePrimeGene.uniqueName) {
                        transcriptsToAssociate.add(eachTranscript)
                        genesToMerge.add(transcriptService.getGene(eachTranscript))
                    }
                } else {
                    if (transcriptService.getGene(eachTranscript).uniqueName == fivePrimeGene.uniqueName) {
                        transcriptsToDissociate.add(eachTranscript)
                    }
                }
            }

//            log.debug "Transcripts to Associate: ${transcriptsToAssociate.name}"
//            log.debug "Transcripts to Dissociate: ${transcriptsToDissociate.name}"
            transcriptsToUpdate.addAll(transcriptsToAssociate)
            transcriptsToUpdate.addAll(transcriptsToDissociate)

            if (transcriptsToAssociate) {
                Gene mergedGene = mergeGeneEntities(fivePrimeGene, genesToMerge.unique())
//                log.debug "mergedGene: ${mergedGene.name}"
                for (Transcript eachTranscript in transcriptsToAssociate) {
                    Gene eachTranscriptParent = transcriptService.getGene(eachTranscript)
                    featureRelationshipService.removeFeatureRelationship(eachTranscriptParent, eachTranscript)
                    addTranscriptToGene(mergedGene, eachTranscript)
                    eachTranscript.name = nameService.generateUniqueName(eachTranscript, mergedGene.name)
                    eachTranscript.save()
                    if (eachTranscriptParent.parentFeatureRelationships.size() == 0) {
                        ArrayList<FeatureProperty> featureProperties = eachTranscriptParent.featureProperties
                        for (FeatureProperty fp : featureProperties) {
                            featurePropertyService.deleteProperty(eachTranscriptParent, fp)
                        }
                        //eachTranscriptParent.delete()
                        // replace a direct delete with the standard method
                        Feature topLevelFeature = featureService.getTopLevelFeature(eachTranscriptParent)
                        featureRelationshipService.deleteFeatureAndChildren(topLevelFeature)
                    }
                }
            }

            if (transcriptsToDissociate) {
                Transcript firstTranscript = null
                for (Transcript eachTranscript in transcriptsToDissociate) {
                    if (firstTranscript == null) {
                        firstTranscript = eachTranscript
                        Gene newGene = new Gene(
                            uniqueName: nameService.generateUniqueName(),
                            name: nameService.generateUniqueName(fivePrimeGene)
                        )

                        firstTranscript.owners.each {
                            newGene.addToOwners(it)
                        }
                        newGene.save(flush: true)

                        FeatureLocation newGeneFeatureLocation = new FeatureLocation(
                            from: newGene,
                            fmin: firstTranscript.fmin,
                            fmax: firstTranscript.fmax,
                            strand: firstTranscript.strand,
                            to: firstTranscript.featureLocation.to,
                        ).save(flush: true)
                        newGene.setFeatureLocation(newGeneFeatureLocation)
                        featureRelationshipService.removeFeatureRelationship(transcriptService.getGene(firstTranscript), firstTranscript)
                        addTranscriptToGene(newGene, firstTranscript)
                        firstTranscript.name = nameService.generateUniqueName(firstTranscript, newGene.name)
                        firstTranscript.save(flush: true)
                        continue
                    }
                    if (eachTranscript && firstTranscript && overlapperService.overlaps(eachTranscript, firstTranscript)) {
                        featureRelationshipService.removeFeatureRelationship(transcriptService.getGene(eachTranscript), eachTranscript)
                        addTranscriptToGene(transcriptService.getGene(firstTranscript), eachTranscript)
                        firstTranscript.name = nameService.generateUniqueName(firstTranscript, transcriptService.getGene(firstTranscript).name)
                        firstTranscript.save(flush: true)
                    } else {
                        throw new AnnotationException("Left behind transcript that doesn't overlap with any other transcripts")
                    }
                }
            }
        }

        return transcriptsToUpdate
    }


    /**
     *
     * @param feature
     * @param gene
     * @return
     */
    def associateFeatureToGene(Feature feature, Gene originalGene) {
        log.debug "associateFeatureToGene: ${feature.name} -> ${originalGene.name}"
        if (!FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(feature.cvTerm)) {
            log.error("Feature type can not be associated with a gene with this method: ${feature.cvTerm}")
            return
        }
        featureRelationshipService.addChildFeature(originalGene, feature)
        // set max and min
        if (feature.fmax > originalGene.fmax) {
            featureService.setFmax(originalGene, feature.fmax)
        }
        if (feature.fmin < originalGene.fmin) {
            featureService.setFmin(originalGene, feature.fmin)
        }

        if (checkForComment(feature, MANUALLY_DISSOCIATE_FEATURE_FROM_GENE)) {
            featurePropertyService.deleteComment(feature, MANUALLY_DISSOCIATE_FEATURE_FROM_GENE)
        }

        featurePropertyService.addComment(feature, MANUALLY_ASSOCIATE_FEATURE_TO_GENE)
        return feature
    }

    def dissociateFeatureFromGene(Feature feature, Gene gene) {
//        log.debug "dissociateFeatureFromGene: ${feature.name} -> ${gene.name}"
        featureRelationshipService.removeFeatureRelationship(gene, feature)

        if (checkForComment(feature, MANUALLY_ASSOCIATE_FEATURE_TO_GENE)) {
            featurePropertyService.deleteComment(feature, MANUALLY_ASSOCIATE_FEATURE_TO_GENE)
        }

        featurePropertyService.addComment(gene, MANUALLY_DISSOCIATE_FEATURE_FROM_GENE)

        if (featureRelationshipService.getChildren(gene).size() == 0) {
            // check if original gene has any additional isoforms; if not then delete original gene
            gene.delete()
        } else {
            featureService.updateGeneBoundaries(gene)
        }

        // redo ?

        featurePropertyService.addComment(feature, MANUALLY_DISSOCIATE_FEATURE_FROM_GENE)
        return feature
    }


    def associateTranscriptToGene(Transcript transcript, Gene gene) {
//        log.debug "associateTranscriptToGene: ${transcript.name} -> ${gene.name}"
        Gene originalGene = transcriptService.getGene(transcript)
//        log.debug "removing transcript ${transcript.name} from its own gene: ${originalGene.name}"
        featureRelationshipService.removeFeatureRelationship(originalGene, transcript)
        addTranscriptToGene(gene, transcript)
        transcript.name = nameService.generateUniqueName(transcript, gene.name)
        // check if original gene has any additional isoforms; if not then delete original gene
        if (transcriptService.getTranscripts(originalGene).size() == 0) {
            originalGene.delete()
        }

        if (checkForComment(transcript, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)) {
            featurePropertyService.deleteComment(transcript, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)
        }

        featurePropertyService.addComment(transcript, MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE)
        return transcript
    }

    def dissociateTranscriptFromGene(Transcript transcript) {
        Gene gene = transcriptService.getGene(transcript)
//        log.debug "dissociateTranscriptFromGene: ${transcript.name} -> ${gene.name}"
        featureRelationshipService.removeFeatureRelationship(gene, transcript)
        Gene newGene
        if (FeatureTypeMapper.PSEUDOGENIC_FEATURE_TYPES.contains(gene.cvTerm)) {
            newGene = new Pseudogene(
                uniqueName: nameService.generateUniqueName(),
                name: nameService.generateUniqueName(gene)
            ).save()
        } else {
            newGene = new Gene(
                uniqueName: nameService.generateUniqueName(),
                name: nameService.generateUniqueName(gene)
            ).save()
        }

//        log.debug "New gene name: ${newGene.name}"
        transcript.owners.each {
            newGene.addToOwners(it)
        }

        FeatureLocation newGeneFeatureLocation = new FeatureLocation(
            from: newGene,
            fmin: transcript.fmin,
            fmax: transcript.fmax,
            strand: transcript.strand,
            to: transcript.featureLocation.to,
        ).save(flush: true)
        newGene.setFeatureLocation(newGeneFeatureLocation)

        if (checkForComment(transcript, MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE)) {
            featurePropertyService.deleteComment(transcript, MANUALLY_ASSOCIATE_TRANSCRIPT_TO_GENE)
        }

        featurePropertyService.addComment(newGene, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)

        addTranscriptToGene(newGene, transcript)
        transcript.name = nameService.generateUniqueName(transcript, newGene.name)

        if (transcriptService.getTranscripts(gene).size() == 0) {
            // check if original gene has any additional isoforms; if not then delete original gene
            gene.delete()
        }

        featurePropertyService.addComment(transcript, MANUALLY_DISSOCIATE_TRANSCRIPT_FROM_GENE)
        return transcript
    }

    def getOverlappingTranscripts(Transcript transcript) {
        FeatureLocation featureLocation = FeatureLocation.executeQuery("MATCH (t:Transcript)-[fl:FEATURELOCATION]-(s:Sequence) where t.uniqueName = ${transcript.uniqueName} return fl ")[0] as FeatureLocation
        ArrayList<Transcript> overlappingTranscripts = getOverlappingTranscripts(featureLocation)
        overlappingTranscripts.remove(transcript) // removing itself
        ArrayList<Transcript> transcriptsWithOverlapCriteria = new ArrayList<Transcript>()
        for (Transcript eachTranscript in overlappingTranscripts) {
            if (eachTranscript && transcript && overlapperService.overlaps(eachTranscript, transcript)) {
                transcriptsWithOverlapCriteria.add(eachTranscript)
            }
        }
        return transcriptsWithOverlapCriteria
    }

    @Transactional
    Gene mergeGeneEntities(Gene mainGene, ArrayList<Gene> genes) {
        def fminList = genes.featureLocation.fmin
        def fmaxList = genes.featureLocation.fmax
        fminList.add(mainGene.fmin)
        fmaxList.add(mainGene.fmax)

        FeatureLocation newFeatureLocation = mainGene.featureLocation
        newFeatureLocation.fmin = fminList.min()
        newFeatureLocation.fmax = fmaxList.max()
        newFeatureLocation.save(flush: true)
        for (Gene gene in genes) {
            gene.featureDBXrefs.each { mainGene.addToFeatureDBXrefs(it) }
            gene.featureGenotypes.each { mainGene.addToFeatureGenotypes(it) }
            gene.featurePhenotypes.each { mainGene.addToFeaturePhenotypes(it) }
            gene.featurePublications.each { mainGene.addToFeaturePublications(it) }
            gene.featureProperties.each { mainGene.addToFeatureProperties(it) }
            gene.featureSynonyms.each { mainGene.addToFeatureSynonyms(it) }
            gene.owners.each { mainGene.addToOwners(it) }
        }

        mainGene.save(flush: true)
        return mainGene
    }

    private class SequenceAlterationInContextPositionComparator<SequenceAlterationInContext> implements Comparator<SequenceAlterationInContext> {
        @Override
        int compare(SequenceAlterationInContext obj1, SequenceAlterationInContext obj2) {
            return obj1.fmin - obj2.fmin
        }
    }

    def sortSequenceAlterationInContext(List<SequenceAlterationInContext> sequenceAlterationInContextList) {
        Collections.sort(sequenceAlterationInContextList, new SequenceAlterationInContextPositionComparator<SequenceAlterationInContext>())
        return sequenceAlterationInContextList
    }

    def sequenceAlterationInContextOverlapper(Feature feature, SequenceAlterationInContext sequenceAlteration) {
        List<Exon> exonList = []
        if (feature.instanceOf(Transcript.class)) {
            exonList = transcriptService.getSortedExons((Transcript) feature, true)
        } else if (feature.instanceOf(CDS.class)) {
            Transcript transcript = transcriptService.getTranscript((CDS) feature)
            exonList = transcriptService.getSortedExons(transcript, true)
        }

        for (Exon exon : exonList) {
            int fmin = exon.fmin
            int fmax = exon.fmax
            if ((sequenceAlteration.fmin >= fmin && sequenceAlteration.fmin <= fmax) || (sequenceAlteration.fmin + sequenceAlteration.alterationResidue.length() >= fmin && sequenceAlteration.fmax + sequenceAlteration.alterationResidue.length() <= fmax)) {
                // alteration overlaps with exon
                return true
            }
        }
        return false
    }

    String getResiduesWithAlterations(Feature feature, Collection<SequenceAlterationArtifact> sequenceAlterations = new ArrayList<>()) {
        String residueString = null
//        log.debug "in seq with alterations "
        List<SequenceAlterationInContext> sequenceAlterationInContextList = new ArrayList<>()
        if (feature.instanceOf(Transcript.class)) {
//            log.debug "log.debug getting residues for transcripr ${feature}"
            residueString = transcriptService.getResiduesFromTranscript((Transcript) feature)
//            log.debug "reside string  ${residueString}"
            // sequence from exons, with UTRs too
            sequenceAlterationInContextList = getSequenceAlterationsInContext(feature, sequenceAlterations)
//            log.debug "GOT getting residues for transcripr ${feature}"
        } else if (feature.instanceOf(CDS.class)) {
//            log.debug "getting residues for CDS ${feature}"
            residueString = cdsService.getResiduesFromCDS((CDS) feature)
            // sequence from exons without UTRs
            sequenceAlterationInContextList = getSequenceAlterationsInContext(transcriptService.getTranscript(feature), sequenceAlterations)
//            log.debug "GOT residues for CDS ${feature}"
        } else {
            // sequence from feature, as is
//            log.debug "getting residues for feature ${feature}"
            residueString = sequenceService.getResiduesFromFeature(feature)
            sequenceAlterationInContextList = getSequenceAlterationsInContext(feature, sequenceAlterations)
//            log.debug "GOT residues for feature ${feature}"
        }
        if (sequenceAlterations.size() == 0 || sequenceAlterationInContextList.size() == 0) {
//            log.debug "retrurening residue ${residueString}"
            return residueString
        }

        StringBuilder residues = new StringBuilder(residueString);
//        log.debug "residues is ${residues}"
        List<SequenceAlterationInContext> orderedSequenceAlterationInContextList = new ArrayList<>(sequenceAlterationInContextList)
        Collections.sort(orderedSequenceAlterationInContextList, new SequenceAlterationInContextPositionComparator<SequenceAlterationInContext>());
        if (!feature.strand.equals(orderedSequenceAlterationInContextList.get(0).strand)) {
            Collections.reverse(orderedSequenceAlterationInContextList);
        }

        int currentOffset = 0
        for (SequenceAlterationInContext sequenceAlteration : orderedSequenceAlterationInContextList) {
            int localCoordinate
            if (feature.instanceOf(Transcript.class)) {
                localCoordinate = convertSourceCoordinateToLocalCoordinateForTranscript((Transcript) feature, sequenceAlteration.fmin);

            } else if (feature.instanceOf(CDS.class)) {
                if (!((sequenceAlteration.fmin >= feature.fmin && sequenceAlteration.fmin <= feature.fmax) || (sequenceAlteration.fmax >= feature.fmin && sequenceAlteration.fmax <= feature.fmin))) {
                    // check to verify if alteration is part of the CDS
                    continue
                }
                localCoordinate = convertSourceCoordinateToLocalCoordinateForCDS(transcriptService.getTranscript(feature), sequenceAlteration.fmin)
            } else {
                localCoordinate = convertSourceCoordinateToLocalCoordinate(feature, sequenceAlteration.fmin);
            }

            String sequenceAlterationResidues = sequenceAlteration.alterationResidue
            if (feature.getFeatureLocation().getStrand() == -1) {
                sequenceAlterationResidues = SequenceTranslationHandler.reverseComplementSequence(sequenceAlterationResidues);
            }
            // Insertions
            if (sequenceAlteration.instanceOf == InsertionArtifact.canonicalName) {
                if (feature.getFeatureLocation().getStrand() == -1) {
                    ++localCoordinate;
                }
                residues.insert(localCoordinate + currentOffset, sequenceAlterationResidues);
                currentOffset += sequenceAlterationResidues.length();
            }
            // Deletions
            else if (sequenceAlteration.instanceOf == DeletionArtifact.canonicalName) {
                if (feature.getFeatureLocation().getStrand() == -1) {
                    residues.delete(localCoordinate + currentOffset - sequenceAlteration.alterationResidue.length() + 1,
                        localCoordinate + currentOffset + 1);
                } else {
                    residues.delete(localCoordinate + currentOffset,
                        localCoordinate + currentOffset + sequenceAlteration.alterationResidue.length());
                }
                currentOffset -= sequenceAlterationResidues.length();
            }
            // Substitions
            else if (sequenceAlteration.instanceOf == SubstitutionArtifact.canonicalName) {
                int start = feature.getStrand() == -1 ? localCoordinate - (sequenceAlteration.alterationResidue.length() - 1) : localCoordinate;
                residues.replace(start + currentOffset,
                    start + currentOffset + sequenceAlteration.alterationResidue.length(),
                    sequenceAlterationResidues);
            }
        }

        return residues.toString();
    }

    List<SequenceAlterationArtifact> getSequenceAlterationsForFeature(Feature feature) {

//        int fmin = feature.fmin
//        int fmax = feature.fmax
//        Sequence sequence = feature.featureLocation.to
//        log.debug "fmin ${fmin}"
//        log.debug "fmax ${fmax}"
//        log.debug "sequence ${sequence}"
//    sessionFactory.currentSession.flushMode = Flush
        return []

        // TODO: fix flush mode,
//        List<SequenceAlterationArtifact> sequenceAlterations = SequenceAlterationArtifact.executeQuery("select distinct sa from SequenceAlterationArtifact sa join sa.featureLocations fl where ((fl.fmin >= :fmin and fl.fmin <= :fmax) or (fl.fmax >= :fmin and fl.fmax <= :fmax)) and fl.sequence = :seqId", [fmin: fmin, fmax: fmax, seqId: sequence])
//        Collection<SequenceAlterationArtifact> sequenceAlterations = (Collection<SequenceAlterationArtifact>) SequenceAlterationArtifact.createCriteria().listDistinct {
//            featureLocations {
//                eq "sequence", sequence
//                or {
//                    and {
//                        gte "fmin", fmin
//                        lte "fmin", fmax
//                    }
//                    and {
//                        lte "fmax", fmax
//                        gte "fmax", fmin
//                    }
//                }
//            }
//
//        }
//        log.debug "got sequence alterations ${sequenceAlterations}"
////    sessionFactory.currentSession.flushMode = FlushMode.AUTO
//
//        return sequenceAlterations
    }


    List<SequenceAlterationInContext> getSequenceAlterationsInContext(Feature feature, Collection<SequenceAlterationArtifact> sequenceAlterations) {
        List<SequenceAlterationInContext> sequenceAlterationInContextList = new ArrayList<>()
        if (!(feature.instanceOf(CDS.class) && !(feature.instanceOf(Transcript.class)))) {
            // for features that are not instance of CDS or Transcript (ex. Single exons)
            def featureLocation = FeatureLocation.findByFrom(feature)
            int featureFmin = featureLocation.fmin
            int featureFmax = featureLocation.fmax
            for (SequenceAlterationArtifact eachSequenceAlteration : sequenceAlterations) {
                int alterationFmin = eachSequenceAlteration.fmin
                int alterationFmax = eachSequenceAlteration.fmax
                SequenceAlterationInContext sa = new SequenceAlterationInContext()
                if ((alterationFmin >= featureFmin && alterationFmax <= featureFmax) && (alterationFmax >= featureFmin && alterationFmax <= featureFmax)) {
                    // alteration is within the generic feature
                    sa.fmin = alterationFmin
                    sa.fmax = alterationFmax
                    if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                        sa.instanceOf = InsertionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                        sa.instanceOf = DeletionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                        sa.instanceOf = SubstitutionArtifact.canonicalName
                    }
                    sa.type = 'within'
                    sa.strand = eachSequenceAlteration.strand
                    sa.name = eachSequenceAlteration.name + '-inContext'
                    sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                    sa.offset = eachSequenceAlteration.offset
                    sa.alterationResidue = eachSequenceAlteration.alterationResidue
                    sequenceAlterationInContextList.add(sa)
                } else if ((alterationFmin >= featureFmin && alterationFmin <= featureFmax) && (alterationFmax >= featureFmin && alterationFmax >= featureFmax)) {
                    // alteration starts in exon but ends in an intron
                    int difference = alterationFmax - featureFmax
                    sa.fmin = alterationFmin
                    sa.fmax = Math.min(featureFmax, alterationFmax)
                    if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                        sa.instanceOf = InsertionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                        sa.instanceOf = DeletionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                        sa.instanceOf = SubstitutionArtifact.canonicalName
                    }
                    sa.type = 'exon-to-intron'
                    sa.strand = eachSequenceAlteration.strand
                    sa.name = eachSequenceAlteration.name + '-inContext'
                    sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                    sa.offset = eachSequenceAlteration.offset - difference
                    sa.alterationResidue = eachSequenceAlteration.alterationResidue.substring(0, eachSequenceAlteration.alterationResidue.length() - difference)
                    sequenceAlterationInContextList.add(sa)
                } else if ((alterationFmin <= featureFmin && alterationFmin <= featureFmax) && (alterationFmax >= featureFmin && alterationFmax <= featureFmax)) {
                    // alteration starts within intron but ends in an exon
                    int difference = featureFmin - alterationFmin
                    sa.fmin = Math.max(featureFmin, alterationFmin)
                    sa.fmax = alterationFmax
                    if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                        sa.instanceOf = InsertionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                        sa.instanceOf = DeletionArtifact.canonicalName
                    } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                        sa.instanceOf = SubstitutionArtifact.canonicalName
                    }
                    sa.type = 'intron-to-exon'
                    sa.strand = eachSequenceAlteration.strand
                    sa.name = eachSequenceAlteration.name + '-inContext'
                    sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                    sa.offset = eachSequenceAlteration.offset - difference
                    sa.alterationResidue = eachSequenceAlteration.alterationResidue.substring(difference, eachSequenceAlteration.alterationResidue.length())
                    sequenceAlterationInContextList.add(sa)
                }
            }
        } else {
            boolean isCDS = feature.instanceOf(CDS.class)
            List<Exon> exonList = isCDS ? transcriptService.getSortedExons(transcriptService.getTranscript(feature), true) : transcriptService.getSortedExons((Transcript) feature, true)
            for (Exon exon : exonList) {
                FeatureLocation exonFeatureLocation = FeatureLocation.findByFrom(exon)
                int exonFmin = exonFeatureLocation.fmin
                int exonFmax = exonFeatureLocation.fmax

                for (SequenceAlterationArtifact eachSequenceAlteration : sequenceAlterations) {
                    FeatureLocation eachSequenceAlterationFeatureLocation = FeatureLocation.findByFrom(eachSequenceAlteration)
                    int alterationFmin = eachSequenceAlterationFeatureLocation.fmin
                    int alterationFmax = eachSequenceAlterationFeatureLocation.fmax
                    SequenceAlterationInContext sa = new SequenceAlterationInContext()
                    if ((alterationFmin >= exonFmin && alterationFmin <= exonFmax) && (alterationFmax >= exonFmin && alterationFmax <= exonFmax)) {
                        // alteration is within exon
                        sa.fmin = alterationFmin
                        sa.fmax = alterationFmax
                        if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                            sa.instanceOf = InsertionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                            sa.instanceOf = DeletionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                            sa.instanceOf = SubstitutionArtifact.canonicalName
                        }
                        sa.type = 'within'
                        sa.strand = eachSequenceAlteration.strand
                        sa.name = eachSequenceAlteration.name + '-inContext'
                        sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                        sa.offset = eachSequenceAlteration.offset
                        sa.alterationResidue = eachSequenceAlteration.alterationResidue
                        sequenceAlterationInContextList.add(sa)
                    } else if ((alterationFmin >= exonFmin && alterationFmin <= exonFmax) && (alterationFmax >= exonFmin && alterationFmax >= exonFmax)) {
                        // alteration starts in exon but ends in an intron
                        int difference = alterationFmax - exonFmax
                        sa.fmin = alterationFmin
                        sa.fmax = Math.min(exonFmax, alterationFmax)
                        if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                            sa.instanceOf = InsertionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                            sa.instanceOf = DeletionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                            sa.instanceOf = SubstitutionArtifact.canonicalName
                        }
                        sa.type = 'exon-to-intron'
                        sa.strand = eachSequenceAlteration.strand
                        sa.name = eachSequenceAlteration.name + '-inContext'
                        sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                        sa.offset = eachSequenceAlteration.offset - difference
                        sa.alterationResidue = eachSequenceAlteration.alterationResidue.substring(0, eachSequenceAlteration.alterationResidue.length() - difference)
                        sequenceAlterationInContextList.add(sa)
                    } else if ((alterationFmin <= exonFmin && alterationFmin <= exonFmax) && (alterationFmax >= exonFmin && alterationFmax <= exonFmax)) {
                        // alteration starts within intron but ends in an exon
                        int difference = exonFmin - alterationFmin
                        sa.fmin = Math.max(exonFmin, alterationFmin)
                        sa.fmax = alterationFmax
                        if (eachSequenceAlteration.instanceOf(InsertionArtifact.class)) {
                            sa.instanceOf = InsertionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(DeletionArtifact.class)) {
                            sa.instanceOf = DeletionArtifact.canonicalName
                        } else if (eachSequenceAlteration.instanceOf(SubstitutionArtifact.class)) {
                            sa.instanceOf = SubstitutionArtifact.canonicalName
                        }
                        sa.type = 'intron-to-exon'
                        sa.strand = eachSequenceAlteration.strand
                        sa.name = eachSequenceAlteration.name + '-inContext'
                        sa.originalAlterationUniqueName = eachSequenceAlteration.uniqueName
                        sa.offset = eachSequenceAlteration.offset - difference
                        sa.alterationResidue = eachSequenceAlteration.alterationResidue.substring(difference, eachSequenceAlteration.alterationResidue.length())
                        sequenceAlterationInContextList.add(sa)
                    }
                }
            }
        }
        return sequenceAlterationInContextList
    }

    int convertModifiedLocalCoordinateToSourceCoordinate(Feature feature, int localCoordinate) {
        Transcript transcript = (Transcript) featureRelationshipService.getParentForFeature(feature, Transcript.ontologyId)
        List<SequenceAlterationInContext> alterations = new ArrayList<>()
        if (feature.instanceOf(CDS.class)) {
            List<SequenceAlterationArtifact> frameshiftsAsAlterations = getFrameshiftsAsAlterations(transcript)
            if (frameshiftsAsAlterations.size() > 0) {
                for (SequenceAlterationArtifact frameshifts : frameshiftsAsAlterations) {
                    SequenceAlterationInContext sa = new SequenceAlterationInContext()
                    sa.fmin = frameshifts.fmin
                    sa.fmax = frameshifts.fmax
                    sa.alterationResidue = frameshifts.alterationResidue
                    sa.type = 'frameshifts-as-alteration'
                    sa.instanceOf = Frameshift.canonicalName
                    sa.originalAlterationUniqueName = frameshifts.uniqueName
                    sa.name = frameshifts.uniqueName + '-frameshifts-inContext'
                    alterations.add(sa)
                }
            }
        }

        alterations.addAll(getSequenceAlterationsInContext(feature, getAllSequenceAlterationsForFeature(feature)))
        if (alterations.size() == 0) {
            if (feature.instanceOf(CDS.class)) {
                // if feature is CDS then calling convertLocalCoordinateToSourceCoordinateForCDS
                return convertLocalCoordinateToSourceCoordinateForCDS((CDS) feature, localCoordinate);
            } else if (feature.instanceOf(Transcript.class)) {
                // if feature is Transcript then calling convertLocalCoordinateToSourceCoordinateForTranscript
                return convertLocalCoordinateToSourceCoordinateForTranscript((Transcript) feature, localCoordinate);
            } else {
                // calling convertLocalCoordinateToSourceCoordinate
                return convertLocalCoordinateToSourceCoordinate(feature, localCoordinate);
            }
        }

        Collections.sort(alterations, new SequenceAlterationInContextPositionComparator<SequenceAlterationInContext>());
        if (feature.getFeatureLocation().getStrand() == -1) {
            Collections.reverse(alterations);
        }

        int insertionOffset = 0
        int deletionOffset = 0
        for (SequenceAlterationInContext alteration : alterations) {
            int alterationResidueLength = alteration.alterationResidue.length()
            if (!sequenceAlterationInContextOverlapper(feature, alteration)) {
                // sequenceAlterationInContextOverlapper method verifies if the alteration is within any of the given exons of the transcript
                continue;
            }
            int coordinateInContext = -1
            if (feature.instanceOf(CDS.class)) {
                // if feature is CDS then calling convertSourceCoordinateToLocalCoordinateForCDS
                coordinateInContext = convertSourceCoordinateToLocalCoordinateForCDS(feature, alteration.fmin)
            } else if (feature.instanceOf(Transcript.class)) {
                // if feature is Transcript then calling convertSourceCoordinateToLocalCoordinateForTranscript
                coordinateInContext = convertSourceCoordinateToLocalCoordinateForTranscript((Transcript) feature, alteration.fmin)
            } else {
                // calling convertSourceCoordinateToLocalCoordinate
                coordinateInContext = convertSourceCoordinateToLocalCoordinate(feature, alteration.fmin)
            }

            if (feature.strand == Strand.NEGATIVE.value) {
                if (coordinateInContext <= localCoordinate && alteration.instanceOf == DeletionArtifact.canonicalName) {
                    deletionOffset += alterationResidueLength
                }
                if ((coordinateInContext - alterationResidueLength) - 1 <= localCoordinate && alteration.instanceOf == InsertionArtifact.canonicalName) {
                    insertionOffset += alterationResidueLength
                }
                if ((localCoordinate - coordinateInContext) - 1 < alterationResidueLength && (localCoordinate - coordinateInContext) >= 0 && alteration.instanceOf == InsertionArtifact.canonicalName) {
                    insertionOffset -= (alterationResidueLength - (localCoordinate - coordinateInContext - 1))

                }

            } else {
                if (coordinateInContext < localCoordinate && alteration.instanceOf == DeletionArtifact.canonicalName) {
                    deletionOffset += alterationResidueLength
                }
                if ((coordinateInContext + alterationResidueLength) <= localCoordinate && alteration.instanceOf == InsertionArtifact.canonicalName) {
                    insertionOffset += alterationResidueLength
                }
                if ((localCoordinate - coordinateInContext) < alterationResidueLength && (localCoordinate - coordinateInContext) >= 0 && alteration.instanceOf == InsertionArtifact.canonicalName) {
                    insertionOffset += localCoordinate - coordinateInContext
                }
            }
        }
        localCoordinate = localCoordinate - insertionOffset
        localCoordinate = localCoordinate + deletionOffset

        if (feature.instanceOf(CDS.class)) {
            // if feature is CDS then calling convertLocalCoordinateToSourceCoordinateForCDS
            return convertLocalCoordinateToSourceCoordinateForCDS((CDS) feature, localCoordinate)
        } else if (feature.instanceOf(Transcript.class)) {
            // if feature is Transcript then calling convertLocalCoordinateToSourceCoordinateForTranscript
            return convertLocalCoordinateToSourceCoordinateForTranscript((Transcript) feature, localCoordinate)
        } else {
            // calling convertLocalCoordinateToSourceCoordinate for all other feature types
            return convertLocalCoordinateToSourceCoordinate(feature, localCoordinate)
        }
    }

    /* convert an input local coordinate to a local coordinate that incorporates sequence alterations */

    int convertSourceToModifiedLocalCoordinate(Feature feature, Integer localCoordinate, List<SequenceAlterationArtifact> alterations = new ArrayList<>()) {
//        log.debug "convertSourceToModifiedLocalCoordinate"

        if (alterations.size() == 0) {
            log.debug "No alterations returning ${localCoordinate}"
            return localCoordinate
        }


        Collections.sort(alterations, new FeaturePositionComparator<SequenceAlterationArtifact>());
        if (feature.getFeatureLocation().getStrand() == -1) {
            Collections.reverse(alterations);
        }

        int deletionOffset = 0
        int insertionOffset = 0

        for (SequenceAlterationArtifact alteration : alterations) {
            int alterationResidueLength = alteration.alterationResidue.length()
            int coordinateInContext = convertSourceCoordinateToLocalCoordinate(feature, alteration.fmin);

            //getAllSequenceAlterationsForFeature returns alterations over entire scaffold?!
            if (alteration.fmin <= feature.fmin || alteration.fmax > feature.fmax) {
                continue
            }

            if (feature.strand == Strand.NEGATIVE.value) {
                coordinateInContext = feature.featureLocation.calculateLength() - coordinateInContext
//                log.debug "Checking negative insertion ${coordinateInContext} ${localCoordinate} ${(coordinateInContext - alterationResidueLength) - 1}"
                if (coordinateInContext <= localCoordinate && alteration.instanceOf(DeletionArtifact.class)) {
//                    log.debug "Processing negative deletion"
                    deletionOffset += alterationResidueLength
                }
                if ((coordinateInContext - alterationResidueLength) - 1 <= localCoordinate && alteration.instanceOf(InsertionArtifact.class)) {
//                    log.debug "Processing negative insertion ${coordinateInContext} ${localCoordinate} ${(coordinateInContext - alterationResidueLength) - 1}"
                    insertionOffset += alterationResidueLength
                }
                if ((localCoordinate - coordinateInContext) - 1 < alterationResidueLength && (localCoordinate - coordinateInContext) >= 0 && alteration.instanceOf(InsertionArtifact.class)) {
                    log.debug "Processing negative insertion pt 2"
                    insertionOffset -= (alterationResidueLength - (localCoordinate - coordinateInContext - 1))

                }

            } else {
                if (coordinateInContext < localCoordinate && alteration.instanceOf(DeletionArtifact.class)) {
//                    log.debug "Processing positive deletion"
                    deletionOffset += alterationResidueLength
                }
                if ((coordinateInContext + alterationResidueLength) <= localCoordinate && alteration.instanceOf(InsertionArtifact.class)) {
//                    log.debug "Processing positive insertion"
                    insertionOffset += alterationResidueLength
                }
                if ((localCoordinate - coordinateInContext) < alterationResidueLength && (localCoordinate - coordinateInContext) >= 0 && alteration.instanceOf(InsertionArtifact.class)) {
//                    log.debug "Processing positive insertion pt 2"
                    insertionOffset += localCoordinate - coordinateInContext
                }
            }

        }

//        log.debug "Returning ${localCoordinate - deletionOffset + insertionOffset}"
        return localCoordinate - deletionOffset + insertionOffset

    }


    def changeAnnotationType(JSONObject inputObject, Feature feature, Sequence sequence, User user, String type) {
        String uniqueName = feature.uniqueName
        String originalType = feature.cvTerm
        JSONObject currentFeatureJsonObject = convertFeatureToJSON(feature)
        Feature newFeature = null

        String topLevelFeatureType = null
        if (type == Transcript.cvTerm) {
            topLevelFeatureType = Pseudogene.cvTerm
        } else if (FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(type)) {
            topLevelFeatureType = type
        } else {
            topLevelFeatureType = Gene.cvTerm
        }

        Gene parentGene = null
        String parentGeneSymbol = null
        String parentGeneDescription = null
        Status parentStatus = null
        Set<DBXref> parentGeneDbxrefs = null
        Set<FeatureProperty> parentGeneFeatureProperties = null
        List<Transcript> transcriptList = []

        if (feature.instanceOf(Transcript.class)) {
            parentGene = transcriptService.getGene((Transcript) feature)
            parentGeneSymbol = parentGene.symbol
            parentStatus = parentGene.status
            parentGeneDescription = parentGene.description
            parentGeneDbxrefs = parentGene.featureDBXrefs
            parentGeneFeatureProperties = parentGene.featureProperties
            transcriptList = transcriptService.getTranscripts(parentGene)
        }

//        log.debug "Parent gene Dbxrefs: ${parentGeneDbxrefs}"
//        log.debug "Parent gene Feature Properties: ${parentGeneFeatureProperties}"

        if (currentFeatureJsonObject.has(FeatureStringEnum.PARENT_TYPE.value)) {
            currentFeatureJsonObject.get(FeatureStringEnum.PARENT_TYPE.value).name = topLevelFeatureType
        }
        currentFeatureJsonObject.get(FeatureStringEnum.TYPE.value).name = type
        currentFeatureJsonObject.put(FeatureStringEnum.USERNAME.value, currentFeatureJsonObject.get(FeatureStringEnum.OWNER.value.toLowerCase()))
        currentFeatureJsonObject.remove(FeatureStringEnum.PARENT_ID.value)
        currentFeatureJsonObject.remove(FeatureStringEnum.ID.value)
        currentFeatureJsonObject.remove(FeatureStringEnum.OWNER.value.toLowerCase())
        currentFeatureJsonObject.remove(FeatureStringEnum.DATE_CREATION.value)
        currentFeatureJsonObject.remove(FeatureStringEnum.DATE_LAST_MODIFIED.value)
        if (currentFeatureJsonObject.has(FeatureStringEnum.CHILDREN.value)) {
            for (JSONObject childFeature : currentFeatureJsonObject.get(FeatureStringEnum.CHILDREN.value)) {
                childFeature.remove(FeatureStringEnum.ID.value)
                childFeature.remove(FeatureStringEnum.OWNER.value.toLowerCase())
                childFeature.remove(FeatureStringEnum.DATE_CREATION.value)
                childFeature.remove(FeatureStringEnum.DATE_LAST_MODIFIED.value)
                childFeature.get(FeatureStringEnum.PARENT_TYPE.value).name = type
            }
        }

        if (!FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(originalType) && FeatureTypeMapper.RNA_FEATURE_TYPES.contains(type)) {
            // *RNA to *RNA
            if (transcriptList.size() == 1) {
                featureRelationshipService.deleteFeatureAndChildren(parentGene)
            } else {
                featureRelationshipService.removeFeatureRelationship(parentGene, feature)
                featureRelationshipService.deleteFeatureAndChildren(feature)
            }

//            log.debug "Converting ${originalType} to ${type}"
            Transcript transcript = null
            if (type == MRNA.cvTerm) {
                // *RNA to mRNA
                transcript = generateTranscript(currentFeatureJsonObject, sequence, true, configWrapperService.useCDS())
                setLongestORF(transcript)
            } else {
                // *RNA to *RNA
                transcript = addFeature(currentFeatureJsonObject, sequence, user, true)
                setLongestORF(transcript)
            }
            println "generated transcript for ${type} of class ${transcript.class.name} ${transcript.cvTerm}"

            Gene newGene = transcriptService.getGene(transcript)
            newGene.symbol = parentGeneSymbol
            newGene.description = parentGeneDescription
            if (parentStatus) {
                newGene.status = new Status(value: parentStatus.value, feature: newGene).save()
            }

            parentGeneDbxrefs.each { it ->
                DBXref dbxref = new DBXref(
                    db: it.db,
                    accession: it.accession,
                    version: it.version,
                    description: it.description
                ).save()
                newGene.addToFeatureDBXrefs(dbxref)
            }

            parentGeneFeatureProperties.each { it ->
                if (it.instanceOf(Comment.class)) {
                    featurePropertyService.addComment(newGene, it.value)
                } else if (it.instanceOf(Status)) {
                    // do nothing
                } else {
                    FeatureProperty fp = new FeatureProperty(
                        type: it.type,
                        value: it.value,
                        rank: it.rank,
                        tag: it.tag,
                        feature: newGene
                    ).save()
                    newGene.addToFeatureProperties(fp)
                }
            }
            newGene.save(flush: true)
            newFeature = transcript
        } else if (!FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(originalType) && FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(type)) {
            // *RNA to singleton
            if (transcriptList.size() == 1) {
                featureRelationshipService.deleteFeatureAndChildren(parentGene)
            } else {
                featureRelationshipService.removeFeatureRelationship(parentGene, feature)
                featureRelationshipService.deleteFeatureAndChildren(feature)
            }
            currentFeatureJsonObject.put(FeatureStringEnum.UNIQUENAME.value, uniqueName)
            currentFeatureJsonObject.remove(FeatureStringEnum.CHILDREN.value)
            currentFeatureJsonObject.remove(FeatureStringEnum.PARENT_TYPE.value)
            currentFeatureJsonObject.remove(FeatureStringEnum.PARENT_ID.value)
            currentFeatureJsonObject.get(FeatureStringEnum.LOCATION.value).strand = 0
            Feature singleton = addFeature(currentFeatureJsonObject, sequence, user, true)
            newFeature = singleton
        } else if (FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(originalType) && FeatureTypeMapper.SINGLETON_FEATURE_TYPES.contains(type)) {
            // singleton to singleton
            currentFeatureJsonObject.put(FeatureStringEnum.UNIQUENAME.value, uniqueName)
            featureRelationshipService.deleteFeatureAndChildren(feature)
            Feature singleton = addFeature(currentFeatureJsonObject, sequence, user, true)
            newFeature = singleton
        } else {
            log.error "Not enough information available to change ${uniqueName} from ${originalType} -> ${type}."
        }

        // TODO: synonyms, featureSynonyms, featureGenotypes, featurePhenotypes

        return newFeature
    }

    def addFeature(JSONObject jsonFeature, Sequence sequence, User user, boolean suppressHistory, boolean useName = false) {
        Feature returnFeature = null

        if (FeatureTypeMapper.RNA_FEATURE_TYPES.contains(jsonFeature.get(FeatureStringEnum.TYPE.value).name)) {
            Gene gene = jsonFeature.has(FeatureStringEnum.PARENT_ID.value) ? (Gene) Feature.findByUniqueName(jsonFeature.getString(FeatureStringEnum.PARENT_ID.value)) : null
            Transcript transcript = null

            if (gene) {
                // Scenario I - if 'parent_id' attribute is given then find the gene
                transcript = (Transcript) convertJSONToFeature(jsonFeature, sequence)
                transcript.save()
                if (transcript.fmin < 0 || transcript.fmax < 0) {
                    throw new AnnotationException("Feature cannot have negative coordinates")
                }

                setOwner(transcript, user)

                addTranscriptToGene(gene, transcript)
                if (!suppressHistory) {
                    String name = nameService.generateUniqueName(transcript)
                    transcript.name = name
                }

                // set the original name for feature
                if (useName && jsonFeature.has(FeatureStringEnum.NAME.value)) {
                    transcript.name = jsonFeature.get(FeatureStringEnum.NAME.value)
                }
                transcript.save()

            } else {
                // Scenario II - find and overlapping isoform and if present, add current transcript to its gene.
                // Disabling Scenario II since there is no appropriate overlapper to determine overlaps between non-coding transcripts.
            }

            if (gene == null) {
//                log.debug "gene is still NULL"
                // Scenario III - create a de-novo gene
                JSONObject jsonGene = new JSONObject()
                if (jsonFeature.has(FeatureStringEnum.PARENT.value)) {
                    // Scenario IIIa - use the 'parent' attribute, if provided, from feature JSON
                    jsonGene = JSON.parse(jsonFeature.getString(FeatureStringEnum.PARENT.value)) as JSONObject
                    jsonGene.put(FeatureStringEnum.CHILDREN.value, new JSONArray().put(jsonFeature))
                } else {
                    // Scenario IIIb - use the current mRNA's featurelocation for gene
                    jsonGene.put(FeatureStringEnum.CHILDREN.value, new JSONArray().put(jsonFeature))
                    jsonGene.put(FeatureStringEnum.LOCATION.value, jsonFeature.getJSONObject(FeatureStringEnum.LOCATION.value))
                    String cvTermString = jsonFeature.get(FeatureStringEnum.TYPE.value).name == Transcript.cvTerm ? Pseudogene.cvTerm : Gene.cvTerm
                    jsonGene.put(FeatureStringEnum.TYPE.value, convertCVTermToJSON(FeatureStringEnum.CV.value, cvTermString))
                }

                String geneName = null
                if (jsonGene.has(FeatureStringEnum.NAME.value)) {
                    geneName = jsonGene.getString(FeatureStringEnum.NAME.value)
//                    log.debug "jsonGene already has 'name': ${geneName}"
                } else if (jsonFeature.has(FeatureStringEnum.PARENT_NAME.value)) {
                    String principalName = jsonFeature.getString(FeatureStringEnum.PARENT_NAME.value)
                    geneName = nameService.makeUniqueGeneName(sequence.organism, principalName, false)
//                    log.debug "jsonFeature has 'parent_name' attribute; using ${principalName} to generate ${geneName}"
                } else if (jsonFeature.has(FeatureStringEnum.NAME.value)) {
                    geneName = jsonFeature.getString(FeatureStringEnum.NAME.value)
//                    log.debug "jsonGene already has 'name': ${geneName}"
                } else {
                    geneName = nameService.makeUniqueGeneName(sequence.organism, sequence.name, false)
                    log.debug "Making a new unique gene name: ${geneName}"
                }

                if (!suppressHistory) {
                    geneName = nameService.makeUniqueGeneName(sequence.organism, geneName, true)
                }

                // set back to the original gene name
                if (jsonFeature.has(FeatureStringEnum.GENE_NAME.value)) {
                    geneName = jsonFeature.getString(FeatureStringEnum.GENE_NAME.value)
                }
                jsonGene.put(FeatureStringEnum.NAME.value, geneName)

                gene = (Gene) convertJSONToFeature(jsonGene, sequence)
                gene.save(flush: true)
                setSequenceForChildFeatures(gene, sequence)
                gene.save(flush: true)

                if (gene.fmin < 0 || gene.fmax < 0) {
                    throw new AnnotationException("Feature cannot have negative coordinates")
                }

                transcript = transcriptService.getTranscripts(gene).first()
//                transcript.save()
                transcript.save(flush: true)
                removeExonOverlapsAndAdjacenciesForFeature(gene)
                if (!suppressHistory) {
                    String name = nameService.generateUniqueName(transcript, geneName)
                    transcript.name = name
                }

                // setting back the original name for the feature
                if (useName && jsonFeature.has(FeatureStringEnum.NAME.value)) {
                    transcript.name = jsonFeature.get(FeatureStringEnum.NAME.value)
                }

                gene.save(insert: true)
                transcript.save(flush: true)

                setOwner(gene, user);
                setOwner(transcript, user);
            }

            removeExonOverlapsAndAdjacencies(transcript)
            CDS cds = transcriptService.getCDS(transcript)
            if (cds != null) {
                featureRelationshipService.deleteChildrenForTypes(transcript, CDS.ontologyId)
                if (cds.parentFeatureRelationships) featureRelationshipService.deleteChildrenForTypes(cds, StopCodonReadThrough.ontologyId)
                cds.delete()
            }
            nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript)
            returnFeature = transcript
        } else {
            if (!jsonFeature.containsKey(FeatureStringEnum.NAME.value) && jsonFeature.containsKey(FeatureStringEnum.CHILDREN.value)) {
                JSONArray childArray = jsonFeature.getJSONArray(FeatureStringEnum.CHILDREN.value)
                if (childArray?.size() == 1 && childArray.getJSONObject(0).containsKey(FeatureStringEnum.NAME.value)) {
                    jsonFeature.put(FeatureStringEnum.NAME.value, childArray.getJSONObject(0).getString(FeatureStringEnum.NAME.value))
                }
            }
//            log.debug "input sequence ${sequence}"
//            log.debug "as JSON ${sequence as JSON}"
            Feature feature = convertJSONToFeature(jsonFeature, sequence)
            feature.save(flush: true)
            log.debug "feature ${feature} -> name: ${feature.name}"
            if (!suppressHistory) {
                String name = nameService.generateUniqueName(feature, feature.name)
                log.debug "not suppressing history withi uqniue name ${name} -> name: ${feature}"
                feature.name = name
            }
            setSequenceForChildFeatures(feature, sequence)

            // setting back the original name for feature
            if (useName && jsonFeature.has(FeatureStringEnum.NAME.value)) {
                feature.name = jsonFeature.get(FeatureStringEnum.NAME.value)
            }

            setOwner(feature, user);
            feature.save(flush: true)
            if (jsonFeature.get(FeatureStringEnum.TYPE.value).name == Gene.cvTerm ||
                FeatureTypeMapper.PSEUDOGENIC_FEATURE_TYPES.contains(jsonFeature.get(FeatureStringEnum.TYPE.value).name)) {
                Transcript transcript = transcriptService.getTranscripts(feature).iterator().next()
                setOwner(transcript, user);
                removeExonOverlapsAndAdjacencies(transcript)
                CDS cds = transcriptService.getCDS(transcript)
                if (cds != null) {
                    featureRelationshipService.deleteChildrenForTypes(transcript, CDS.ontologyId)
                    if (cds.parentFeatureRelationships) featureRelationshipService.deleteChildrenForTypes(cds, StopCodonReadThrough.ontologyId)
                    cds.delete()
                }
                nonCanonicalSplitSiteService.findNonCanonicalAcceptorDonorSpliceSites(transcript)
                transcript.save(flush: true)
                returnFeature = transcript
            } else {
                returnFeature = feature
            }
        }

        return returnFeature
    }

    def addNonPrimaryDbxrefs(Feature feature, String dbString, String accessionString) {
        DB db = DB.findByName(dbString)
        if (!db) {
            db = new DB(name: dbString).save()
        }
        DBXref dbxref = DBXref.findOrSaveByAccessionAndDb(accessionString, db)
        dbxref.save(flush: true)
        feature.addToFeatureDBXrefs(dbxref)
        feature.save()
    }

    def addNonReservedProperties(Feature feature, String tagString, String valueString) {
        FeatureProperty featureProperty = new FeatureProperty(
            feature: feature,
            value: valueString,
            tag: tagString
        ).save()
        featurePropertyService.addProperty(feature, featureProperty)
        feature.save()
    }

    Boolean checkForComment(Feature feature, String value) {
        return (FeatureProperty.executeQuery("MATCH (f:Feature)--(c:Comment) where f.uniqueName=${feature.uniqueName} and c.value = ${value} return count(c)").first() as Integer) > 0
    }
}
