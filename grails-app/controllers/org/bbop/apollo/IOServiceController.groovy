package org.bbop.apollo

import com.google.common.base.Splitter
import grails.converters.JSON
import io.swagger.v3.oas.annotations.Api
import io.swagger.v3.oas.annotations.ApiImplicitParam
import io.swagger.v3.oas.annotations.ApiImplicitParams
import io.swagger.v3.oas.annotations.ApiOperation
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.sequence.DownloadFile
import org.bbop.apollo.sequence.Strand
import org.bbop.apollo.variant.SequenceAlteration
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.springframework.http.HttpStatus

import java.util.zip.GZIPOutputStream

@Controller(value = "/IOService", tags= "IO Services: Methods for bulk importing and exporting sequence data")
class IOServiceController extends AbstractApolloController {

    def sequenceService
    def gff3HandlerService
    def fastaHandlerService
    def chadoHandlerService
    def permissionService
    def configWrapperService
    def vcfHandlerService
    def trackService
    def fileService
    def gpad2HandlerService
    def gpiHandlerService

    // fileMap of uuid / filename
    // see #464
    private Map<String, DownloadFile> fileMap = new HashMap<>()

    def index() {}

    def handleOperation(String operation) {
        log.debug "Requested parameterMap: ${request.parameterMap.keySet()}"
        log.debug "upstream params: ${params}"
        def mappedAction = underscoreToCamelCase(operation)
        forward action: "${mappedAction}", params: params
    }

    @Operation(value = "Write out genomic data.  An example script is used in the https://github.com/GMOD/Apollo/blob/master/docs/web_services/examples/groovy/get_gff3.groovy"
        , nickname = "/write", httpMethod = "POST"
    )
    @Parameters([
        @Parameter(name = "username", type = "email", paramType = "query")
        , @Parameter(name = "password", type = "password", paramType = "query")

        , @Parameter(name = "type", type = "string", paramType = "query", example = "Type of annotated genomic features to export 'FASTA','GFF3','CHADO'.")

        , @Parameter(name = "seqType", type = "string", paramType = "query", example = "Type of output sequence 'peptide','cds','cdna','genomic'.")
        , @Parameter(name = "format", type = "string", paramType = "query", example = "'gzip' or 'text'")
        , @Parameter(name = "sequences", type = "string", paramType = "query", example = "Names of references sequences to add (default is all).")
        , @Parameter(name = "organism", type = "string", paramType = "query", example = "Name of organism that sequences belong to (will default to last organism).")
        , @Parameter(name = "output", type = "string", paramType = "query", example = "Output method 'file','text'")
        , @Parameter(name = "exportAllSequences", type = "boolean", paramType = "query", example = "Export all reference sequences for an organism (over-rides 'sequences')")
        , @Parameter(name = "region", type = "String", paramType = "query", example = "Highlighted genomic region to export in form sequence:min..max  e.g., chr3:1001..1034")
    ]
    )

    def write() {
        File outputFile = null
        try {
            long current = System.currentTimeMillis()
            JSONObject dataObject = permissionService.handleInput(request, params)
            if (!permissionService.hasPermissions(dataObject, PermissionEnum.EXPORT)) {
                render status: HttpStatus.UNAUTHORIZED
                return
            }
            String typeOfExport = dataObject.type
            String sequenceType = dataObject.seqType
            Boolean exportAllSequences = dataObject.exportAllSequences ? Boolean.valueOf(dataObject.exportAllSequences) : false
//            // always export all
//            if(typeOfExport == FeatureStringEnum.TYPE_JBROWSE.value){
//                exportAllSequences = true
//            }

//            Boolean exportFullJBrowse = dataObject.exportFullJBrowse ? Boolean.valueOf(dataObject.exportFullJBrowse) : false
            Boolean exportJBrowseSequence = dataObject.exportJBrowseSequence ? Boolean.valueOf(dataObject.exportJBrowseSequence) : false
            Boolean exportGff3Fasta = dataObject.exportGff3Fasta ? Boolean.valueOf(dataObject.exportGff3Fasta) : false
            String output = dataObject.output
            String format = dataObject.format
            String region = dataObject.region
            String adapter = dataObject.adapter
            if (region && !adapter) {
                adapter = FeatureStringEnum.HIGHLIGHTED_REGION.value
            }

            def sequences = dataObject.sequences // can be array or string
            Organism organism = dataObject.organism ? permissionService.getOrganismForToken(dataObject.organism) : permissionService.getOrganismsForCurrentUser(dataObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))

            def st = System.currentTimeMillis()
            def queryParams = [organism: organism]
            def features = []

            if (exportAllSequences) {
                sequences = []
            }
            if (sequences) {
                queryParams.sequences = sequences
            }

            if (typeOfExport == FeatureStringEnum.TYPE_VCF.value) {
                queryParams['viewableAnnotationList'] = FeatureTypeMapper.VIEWABLE_SEQUENCE_ALTERATION_LIST
                features = SequenceAlteration.createCriteria().list() {
                    featureLocation {
                        sequence {
                            eq('organism', organism)
                            if (sequences) {
                                'in'('name', sequences)
                            }
                        }
                    }
                    'in'('class', FeatureTypeMapper.VIEWABLE_SEQUENCE_ALTERATION_LIST)
                }

                log.debug "IOService query: ${System.currentTimeMillis() - st}ms"
            } else {
//                queryParams['viewableAnnotationList'] = requestHandlingService.nonCodingAnnotationTranscriptList
//                // request nonCoding transcripts that can lack an exon
////                def genesNoExon = Gene.executeQuery("select distinct f from Gene f join fetch f.featureLocations fl join fetch f.parentFeatureRelationships pr join fetch pr.childFeature child join fetch child.featureLocations where fl.sequence.organism = :organism and child.class in (:viewableAnnotationList)" + (sequences ? " and fl.sequence.name in (:sequences) " : ""),queryParams)
//                String genesNoExonQuery = "MATCH (g:Gene)--(t:Transcript)--(f:Feature),(g)--(s:Sequence)--(o:Organism) where (o.commonName = '${organism.commonName}' or o.id = ${organism.id}) and NOT (t:MRNA) " + (sequences ? "and s.name in ${sequences}" : "") + " RETURN distinct g"
////                println "query: [${genesNoExonQuery}]"
//                def genesNoExon = Gene.executeQuery(genesNoExonQuery) as List<Feature>
                def genesNoExon = null
//                if (genesNoExon.id) {
//                    queryParams['geneIds'] = genesNoExon.id
//                }
//
//                // captures 3 level indirection, joins feature locations only. joining other things slows it down
//                queryParams['viewableAnnotationList'] = requestHandlingService.viewableAnnotationList
////                def genes = Gene.executeQuery("select distinct f from Gene f join fetch f.featureLocations fl join fetch f.parentFeatureRelationships pr join fetch pr.childFeature child join fetch child.featureLocations join fetch child.childFeatureRelationships join fetch child.parentFeatureRelationships cpr join fetch cpr.childFeature subchild join fetch subchild.featureLocations join fetch subchild.childFeatureRelationships left join fetch subchild.parentFeatureRelationships where fl.sequence.organism = :organism  ${genesNoExon.id ? " and f.id not in (:geneIds)": ""}  and f.class in (:viewableAnnotationList)" + (sequences ? " and fl.sequence.name in (:sequences)" : ""), queryParams)
//                String genesQuery = "MATCH (g:Gene)--(t:Transcript)--(f:Feature),(g)--(s:Sequence)--(o:Organism) where (o.commonName = '${organism.commonName}' or o.id = ${organism.id}) " + (genesNoExon.id!=null ? " and not g.id in ${genesNoExon.id}" : "") + (sequences ? "and s.name in ${sequences}" : "") + " RETURN distinct g"
//                def genes = Gene.executeQuery(genesQuery) as List<Feature>
////                 captures rest of feats
//                def otherFeatsQuery = "MATCH (f:Feature),(f)--(s:Sequence)--(o:Organism) where (o.commonName = ${organism.commonName} or o.id = ${organism.id}) and NOT (f:gene) " + (sequences ? "and s.name in ${sequences}" : "") + " RETURN distinct f"
////                println otherFeatsQuery
//                def otherFeats = Feature.executeQuery(otherFeatsQuery) as List<Feature>
////                def otherFeats = Feature.createCriteria().list() {
////                    featureLocations {
////                        sequence {
////                            eq('organism', organism)
////                            if (sequences) {
////                                'in'('name', sequences)
////                            }
////                        }
////                    }
////                    'in'('class', requestHandlingService.viewableAlterations + requestHandlingService.viewableAnnotationFeatureList)
////                }
//                log.debug "${otherFeats}"
////                features = (genes + otherFeats + genesNoExon ) as List<Feature>
//

//                String fullGenesQuery = "MATCH (g:Gene)--(t:Transcript)--(f:Feature),(g)--(s:Sequence)--(o:Organism) where (o.commonName = '${organism.commonName}' or o.id = ${organism.id}) " + (genesNoExon.id ? " and g.id not in ${queryParams.geneIds}" : "")   +  (sequences ? "and s.name in ${sequences}" :"")  + " RETURN distinct g"
//                String fullGenesQuery = "MATCH (g:Gene)--(t:Transcript)--(f:Feature),(g)--(s:Sequence)--(o:Organism) where (o.commonName = '${organism.commonName}' or o.id = ${organism.id}) " + (genesNoExon.id ? " and g.id not in ${queryParams.geneIds}" : "")   +  (sequences ? "and s.name in ${sequences}" :"")  + " RETURN distinct g"

                // query transcripts

                String sequenceString
                println "input sequence string"
                println sequences
                if(sequences && sequences instanceof JSONArray){
                    sequenceString = "["  +  sequences.collect{  return "\"${it}\"" }.join(",") + "]"
                }
                else
                if(sequences && sequences instanceof String){
                    sequenceString = sequences
                }
                println "output sequence string"
                println sequenceString

                // TODO: note that "type" is being passed in for debugging only
//                String fullGenesQuery = "MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence)-[fl:FEATURELOCATION]-(f:Transcript), " +
//                    "(f)-[owner:OWNERS]-(u) " +
//                    "WHERE (o.id=${organism.id} or o.commonName='${organism.commonName}') " + (sequences ? "and s.name in ${sequenceString} " : " ")  +
//                    "OPTIONAL MATCH (o)--(s)-[cl:FEATURELOCATION]-(parent:Gene)-[gfr]->(f) " +
//                    "WHERE (o.id=${organism.id} or o.commonName='${organism.commonName}') " + (sequences ? "and s.name in ${sequenceString} " : " ")  +
//                    "OPTIONAL MATCH (o)--(s)-[pl:FEATURELOCATION]-(f)-[fr]->(child:Feature)-[pl2:FEATURELOCATION]-(s) " +
//                    "WHERE (o.id=${organism.id} or o.commonName='${organism.commonName}') " + (sequences ? "and s.name in ${sequenceString} " : " ")  +
//                    "RETURN f"
//                "RETURN {type: labels(f),sequence: s,feature: f,location: fl,children: collect(DISTINCT {type: labels(child), location: pl2,r1: fr,feature: child,sequence: s}), " +
//                        "owners: collect(distinct u),parent: { type: labels(parent), location: pl,r2:gfr,feature:parent }}"

                String fullGenesQuery = "MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence)-[fl:FEATURELOCATION]-(g:Gene), " +
                    "(g)-[owner:OWNERS]-(u) " +
                    "WHERE (o.id=${organism.id} or o.commonName='${organism.commonName}') " + (sequences ? "and s.name in ${sequenceString} " : " ")  +
                    "RETURN g"
//
                println "full genes query ${fullGenesQuery}"

//
                def neo4jFeatureNodes = Feature.executeQuery(fullGenesQuery)
                println "neo4j nodes ${neo4jFeatureNodes as JSON}"


                // TODO: query single-level (RR, etc.), excluding if a parent or child feature relationship
                // TODO: must exclude prior
                String singleLevelQuery = "MATCH (o:Organism)-[r:SEQUENCES]-(s:Sequence)-[fl:FEATURELOCATION]-(f:Feature),(f)-[owner:OWNERS]-(u) " +
                    "WHERE (o.id=${organism.id} or o.commonName='${organism.commonName}')" + (sequences ? "and s.name in ${sequenceString} " : " ")  +
//                  " AND TYPE(f) <> 'Gene' " +
                    " AND NOT (f)-[:FEATURERELATIONSHIP]-(:Feature) " +
                    "RETURN f "

                println "single level query ${singleLevelQuery}"
                neo4jFeatureNodes += Feature.executeQuery(singleLevelQuery).unique()

                neo4jFeatureNodes.each{
                    features.add( FeatureTypeMapper.castNeo4jFeature(it))
                }
//                features = neo4jFeatureNodes
//                println "features ${features as JSON}"
                println "features ${features}"

                println "IOService query: ${System.currentTimeMillis() - st}ms"

            }

            def sequenceList = Sequence.createCriteria().list() {
                eq('organism', organism)
                if (sequences) {
                    'in'('name', sequences)
                }
            }
            println "sequenceList ${sequenceList}"

            outputFile = File.createTempFile("Annotations", "." + typeOfExport.toLowerCase())
            String fileName

            if (typeOfExport == FeatureStringEnum.TYPE_GFF3.getValue()) {
                // adding sequence alterations to list of features to export
                if (!exportAllSequences && sequences != null && !(sequences.class == JSONArray.class)) {
                    fileName = "Annotations-" + sequences + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                } else {
                    fileName = "Annotations" + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                }
                // call gff3HandlerService
                if (exportGff3Fasta) {
                    gff3HandlerService.writeNeo4jFeaturesToText(outputFile.path, features, grailsApplication.config.apollo.gff3.source as String, true, sequenceList)
                } else {
//                    gff3HandlerService.writeFeaturesToText(outputFile.path, features, grailsApplication.config.apollo.gff3.source as String)
                    gff3HandlerService.writeNeo4jFeaturesToText(outputFile.path, features, grailsApplication.config.apollo.gff3.source as String)
                }
            } else if (typeOfExport == FeatureStringEnum.TYPE_GO.value) {
                String sequenceString = organism.commonName
                if (sequences) {
                    sequenceString += "-" + sequences.join("_")
                }
                if (sequenceType == FeatureStringEnum.TYPE_GPAD2.value) {
                    fileName = "GoAnnotations" + sequenceString + "." + sequenceType.toLowerCase() + (format == "gzip" ? ".gz" : "")
                    gpad2HandlerService.writeFeaturesToText(outputFile.path, features)
                } else if (sequenceType == FeatureStringEnum.TYPE_GPI2.value) {
                    fileName = "GoAnnotations" + sequenceString + "." + sequenceType.toLowerCase() + (format == "gzip" ? ".gz" : "")
                    gpiHandlerService.writeFeaturesToText(outputFile.path, features)
                }
            } else if (typeOfExport == FeatureStringEnum.TYPE_VCF.value) {
                if (!exportAllSequences && sequences != null && !(sequences.class == JSONArray.class)) {
                    fileName = "Annotations-" + sequences + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                } else {
                    fileName = "Annotations" + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                }
                // call vcfHandlerService
                vcfHandlerService.writeVariantsToText(organism, features, outputFile.path, grailsApplication.config.apollo.gff3.source as String)
            } else if (typeOfExport == FeatureStringEnum.TYPE_FASTA.getValue()) {
                println "output fasta sequence"

                String singleSequenceName = (sequences.class != JSONArray.class) ? sequences : null
                singleSequenceName = (singleSequenceName == null && sequences.class == JSONArray.class && sequences.size() == 1) ? sequences[0] : null


                if (!exportAllSequences && singleSequenceName) {
                    String regionString = (region && adapter == FeatureStringEnum.HIGHLIGHTED_REGION.value) ? region : ""
                    fileName = "Annotations-${regionString}." + sequenceType + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                } else {
                    fileName = "Annotations" + "." + sequenceType + "." + typeOfExport.toLowerCase() + (format == "gzip" ? ".gz" : "")
                }

                // call fastaHandlerService
                if (region && adapter == FeatureStringEnum.HIGHLIGHTED_REGION.value) {
                    String track = region.split(":")[0]
                    String locationString = region.split(":")[1]
                    Integer min = locationString.split("\\.\\.")[0] as Integer
                    Integer max = locationString.split("\\.\\.")[1] as Integer
                    // its an exclusive fmin, so must subtract one
                    --min
                    Sequence sequence = Sequence.findByOrganismAndName(organism, track)

                    String defline = String.format(">Genomic region %s - %s\n", region, sequence.organism.commonName);
                    String genomicSequence = defline
                    genomicSequence += Splitter.fixedLength(FastaHandlerService.NUM_RESIDUES_PER_LINE).split(sequenceService.getGenomicResiduesFromSequenceWithAlterations(sequence, min, max, Strand.POSITIVE)).join("\n")
                    outputFile.text = genomicSequence
                } else {
                    println "write output features ${features}"
                    fastaHandlerService.writeFeatures(features, sequenceType, ["name"] as Set, outputFile.path, FastaHandlerService.Mode.WRITE, FastaHandlerService.Format.TEXT, region)
                    println "finished writing"
                }
            } else if (typeOfExport == FeatureStringEnum.TYPE_CHADO.getValue()) {
                if (sequences) {
                    render chadoHandlerService.writeFeatures(organism, sequenceList, features)
                } else {
                    render chadoHandlerService.writeFeatures(organism, [], features, exportAllSequences)
                }
                return // no other export neeed
            } else if (typeOfExport == FeatureStringEnum.TYPE_JBROWSE.getValue()) {
                // does not quite work correctly
                fileName = "JBrowse-" + organism.commonName.replaceAll(" ", "_") + ".tar.gz"
                String pathToJBrowseBinaries = servletContext.getRealPath("/jbrowse/bin")
                if (exportJBrowseSequence) {
                    File inputGff3File = File.createTempFile("temp", ".gff")
                    gff3HandlerService.writeFeaturesToText(inputGff3File.absolutePath, features, grailsApplication.config.apollo.gff3.source as String)
                    File outputJsonDir = File.createTempDir()
                    trackService.generateJSONForGff3(inputGff3File, outputJsonDir.absolutePath, pathToJBrowseBinaries)
                    fileService.compressTarArchive(outputFile, outputJsonDir, ".")
                } else {
                    gff3HandlerService.writeFeaturesToText(outputFile.path, features, grailsApplication.config.apollo.gff3.source as String)
                    trackService.generateJSONForGff3(outputFile, organism.directory, pathToJBrowseBinaries)
                }
            }

            //generating a html fragment with the link for download that can be rendered on client side
            String uuidString = UUID.randomUUID().toString()
            DownloadFile downloadFile = new DownloadFile(
                uuid: uuidString
                , path: outputFile.path
                , fileName: fileName
            )
            println "${uuidString}"
            println "output ${output}"
            println "jsonObject ${outputFile}"
            fileMap.put(uuidString, downloadFile)

            if (output == "file") {

                def jsonObject = [
                    "uuid"      : uuidString,
                    "exportType": typeOfExport,
                    "seqType"   : sequenceType,
                    "format"    : format,
                    "filename"  : fileName
                ]
                render jsonObject as JSON
            } else {
                render text: outputFile.text
            }
            log.debug "Total IOService export time ${System.currentTimeMillis() - current}ms"
        }
        catch (Exception e) {
            def error = [error: e.message]
            e.printStackTrace()
            render error as JSON
        }
        if (outputFile?.exists()) {
            outputFile.deleteOnExit()
        }
    }

    @Operation(value = "This is used to retrieve the a download link once the write operation was initialized using output: file."
        , nickname = "/download", httpMethod = "POST"
    )
    @Parameters([
        @Parameter(name = "username", type = "email", paramType = "query")
        , @Parameter(name = "password", type = "password", paramType = "query")
        , @Parameter(name = "uuid", type = "string", paramType = "query", example = "UUID that holds the key to the stored download.")
        , @Parameter(name = "format", type = "string", paramType = "query", example = "'gzip' or 'text'")
    ]
    )

    def download() {
        String uuid = params.uuid
        DownloadFile downloadFile = fileMap.remove(uuid)
        def file
        if (downloadFile) {
            file = new File(downloadFile.path)
            if (!file.exists()) {
                render text: "Error: file does not exist"
                return
            }
        } else {
            render text: "Error: uuid did not map to file. Please try to re-download"
            return
        }

        response.setHeader("Content-disposition", "attachment; filename=${downloadFile.fileName}")
//        if (params.format == "tar.gz") {
//            println "just downloading the bytes directly "
//            def outputStream = response.outputStream
//            outputStream << file.bytes
//            outputStream.flush()
//            outputStream.close()
//        }
//        else
        if (params.format == "gzip") {
            new GZIPOutputStream(new BufferedOutputStream(response.outputStream)).withWriter { it << file.text }
        } else {
            def outputStream = response.outputStream
            outputStream << file.text
            outputStream.flush()
            outputStream.close()
        }

        file.delete()
    }

    def chadoExportStatus() {
        JSONObject returnObject = new JSONObject()
        returnObject.export_status = configWrapperService.hasChadoDataSource().toString()
        render returnObject
    }
}
