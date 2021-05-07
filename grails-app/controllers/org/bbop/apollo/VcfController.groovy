package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import htsjdk.variant.vcf.VCFFileReader
import io.micronaut.http.annotation.Controller
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.organism.Organism
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

@Controller(value = "/vcf",tags = "VCF Services: Methods for retrieving VCF track data as JSON")
@Transactional
class VcfController {

    def permissionService
    def vcfService
    def trackService

//    def beforeInterceptor = {
//        if (params.action == "featuresByLocation") {
//            response.setHeader("Access-Control-Allow-Origin", "*")
//        }
//    }


    @Operation(value = "Get VCF track data for a given range as JSON", nickname = "/vcf/<organism_name>/<track_name>/<sequence_name>:<fmin>..<fmax>.<type>?includeGenotypes=<includeGenotypes>&ignoreCache=<ignoreCache>", httpMethod = "get")
    @Parameters([
            @Parameter(name = "organismString", type = "string", paramType = "query", example = "Organism common name or ID (required)"),
            @Parameter(name = "trackName", type = "string", paramType = "query", example = "Track name by label in trackList.json (required)"),
            @Parameter(name = "sequence", type = "string", paramType = "query", example = "Sequence name (required)"),
            @Parameter(name = "fmin", type = "integer", paramType = "query", example = "Minimum range (required)"),
            @Parameter(name = "fmax", type = "integer", paramType = "query", example = "Maximum range (required)"),
            @Parameter(name = "type", type = "string", paramType = "query", example = ".json (required)"),
            @Parameter(name = "includeGenotypes", type = "boolean", paramType = "query", example = "(default: false).  If true, will include genotypes associated with variants from VCF."),
            @Parameter(name = "ignoreCache", type = "boolean", paramType = "query", example = "(default: false).  Use cache for request, if available."),
    ])
    def featuresByLocation(String organismString, String trackName, String sequence, Long fmin, Long fmax, String type, boolean includeGenotypes) {
        if(!trackService.checkPermission(response, organismString)) return

        JSONArray featuresArray = new JSONArray()
        Organism organism = permissionService.getOrganismForToken(organismString)
        JSONObject trackListObject = trackService.getTrackList(organism.directory)

        Boolean ignoreCache = params.ignoreCache != null ? Boolean.valueOf(params.ignoreCache) : false
        if (!ignoreCache) {
            String responseString = trackService.checkCache(organismString, trackName, sequence, fmin, fmax, type, null)
            if (responseString) {
                render JSON.parse(responseString) as JSON
                return
            }
        }

        String trackUrlTemplate = null
        for(JSONObject track : trackListObject.getJSONArray(FeatureStringEnum.TRACKS.value)) {
            log.debug "comparing ${track.label} to ${trackName}"
            if(track.getString(FeatureStringEnum.LABEL.value) == trackName) {
                log.debug "found ${track} -> ${track.urlTemplate}"
                trackUrlTemplate = track.urlTemplate
                break
            }
        }
        if(!trackUrlTemplate){
            throw new RuntimeException("Track url template not found for '${trackName}'")
        }

        File file = new File(organism.directory + File.separator + trackUrlTemplate)
        try {
            VCFFileReader vcfFileReader = new VCFFileReader(file)
            featuresArray = vcfService.processVcfRecords(organism, vcfFileReader, sequence, fmin, fmax, includeGenotypes)
        }
        catch (IOException e) {
            log.error(e.stackTrace)
        }

        trackService.cacheRequest(featuresArray.toString(), organismString, trackName, sequence, fmin, fmax, type, null)
        render featuresArray as JSON
    }

}
