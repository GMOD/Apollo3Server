package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.NotTransactional
import grails.gorm.transactions.Transactional
import io.micronaut.http.annotation.Controller
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.apache.shiro.SecurityUtils
import org.apache.shiro.session.Session
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.organism.SequenceCache
import org.bbop.apollo.report.SequenceSummary
import org.bbop.apollo.sequence.Strand
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import javax.servlet.http.HttpServletResponse

import static org.springframework.http.HttpStatus.NOT_FOUND

@Controller(value = "/sequence" , tags = "Sequence Services: Methods for retrieving sequence data")
@Transactional(readOnly = true)
class SequenceController {


    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def sequenceService
    def permissionService
    def reportService

    @NotTransactional
    def setCurrentSequenceLocation(String name, Integer start, Integer end) {
        render new JSONObject() as JSON
//        try {
//            UserOrganismPreferenceDTO userOrganismPreference = preferenceService.setCurrentSequenceLocation(name, start, end, params[FeatureStringEnum.CLIENT_TOKEN.value].toString())
//            if (params.suppressOutput) {
//                render new JSONObject() as JSON
//            } else {
//                render userOrganismPreference.sequence as JSON
//            }
//        } catch (NumberFormatException e) {
//            //  we can ignore this specific exception as null is an acceptable value for start / end
//        }
//        catch (Exception e) {
//            def error = [error: e.message]
//            log.error e.message
//            render error as JSON
//        }
    }

    @Transactional
    def setCurrentSequenceForNameAndOrganism(Organism organism) {
        JSONObject inputObject = permissionService.handleInput(request, params)
        Sequence sequence = Sequence.findByNameAndOrganism(inputObject.sequenceName, organism)
        setCurrentSequence(sequence)
    }

    /**
     * ID is the organism ID
     * Sequence is the default sequence name
     *
     * If no sequence name is set, pull the preferences, otherwise just choose a random one.
     * @param id
     * @param sequenceName
     * @return
     */

    @Transactional
    def setCurrentSequence(Sequence sequenceInstance) {
        JSONObject inputObject = permissionService.handleInput(request, params)
//        String token = inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value)
        Organism organism = sequenceInstance.organism

//        User currentUser = permissionService.currentUser
//        UserOrganismPreferenceDTO userOrganismPreference = preferenceService.setCurrentSequence(currentUser, sequenceInstance, token)

        Session session = SecurityUtils.subject.getSession(false)
        session.setAttribute(FeatureStringEnum.DEFAULT_SEQUENCE_NAME.value, sequenceInstance.name)
        session.setAttribute(FeatureStringEnum.SEQUENCE_NAME.value, sequenceInstance.name)
        session.setAttribute(FeatureStringEnum.ORGANISM_JBROWSE_DIRECTORY.value, organism.directory)
        session.setAttribute(FeatureStringEnum.ORGANISM_ID.value, organism.id)

        JSONObject sequenceObject = new JSONObject()
        sequenceObject.put("id", sequenceInstance.id)
        sequenceObject.put("name", sequenceInstance.name)
        sequenceObject.put("length", sequenceInstance.length)
        sequenceObject.put("start", sequenceInstance.start)
        sequenceObject.put("end", sequenceInstance.end)
//        sequenceObject.startBp = userOrganismPreference.startbp
//        sequenceObject.endBp = userOrganismPreference.endbp

        render sequenceObject as JSON
    }


    @Transactional
    def loadSequences(Organism organism) {
        if (!organism.sequences) {
            sequenceService.loadRefSeqs(organism)
        }

        JSONArray sequenceArray = new JSONArray()
        for (Sequence sequence in organism.sequences) {
            JSONObject jsonObject = new JSONObject()
            jsonObject.put("id", sequence.id)
            jsonObject.put("name", sequence.name)
            jsonObject.put("length", sequence.length)
            jsonObject.put("start", sequence.start)
            jsonObject.put("end", sequence.end)
            sequenceArray.put(jsonObject)
        }

        render sequenceArray as JSON
    }


    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'sequence.label', default: 'Sequence'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }


    @Transactional
    def lookupSequenceByName(String q, String clientToken) {
        try {
//          Organism organism = preferenceService.getCurrentOrganismForCurrentUser(clientToken)
            Organism organism = permissionService.getOrganismForToken(clientToken)
          def sequences = Sequence.findAllByNameIlikeAndOrganism(q + "%", organism, ["sort": "name", "order": "asc", "max": 20]).collect() {
              it.name
          }
          render sequences as JSON
        } catch (e) {
          log.warn(e.getMessage())
          render new JSONArray() as JSON
        }
    }

    /**
     * @deprecated TODO: will be removed as standalone will likely not be supported in the future.
     * @return
     */
    def lookupSequenceByNameAndOrganism(String clientToken) {
        JSONObject j;
        for (k in params) {
            j = JSON.parse(k.key)
            break;
        }
        def organism
        if (!j.name || !j.organism) {
//            organism = preferenceService.getCurrentOrganismForCurrentUser(clientToken)
            organism = permissionService.getOrganismForToken(clientToken)
        } else {
            organism = Organism.findById(j.organism)
        }
        def seqid = j.name
        def sequenceId = Sequence.findByNameAndOrganism(seqid, organism).id
        JSONObject jsonObject = new JSONObject()
        jsonObject.put(FeatureStringEnum.ID.value, sequenceId)
        jsonObject.put(FeatureStringEnum.ORGANISM_ID.value, organism.id)
        render jsonObject as JSON
    }

    @Transactional
    def getSequences(String name, Integer start, Integer length, String sort, Boolean asc, Integer minFeatureLength, Integer maxFeatureLength, String clientToken) {
        try {
//            Organism organism = preferenceService.getCurrentOrganismForCurrentUser(clientToken)
            Organism organism = permissionService.getOrganismForToken(clientToken)

            if (!organism) {
                render([] as JSON)
                return
            }
            def sequences = Sequence.createCriteria().list() {
                if (name) {
                    ilike('name', '%' + name + '%')
                }
                eq('organism', organism)
                gt('length', minFeatureLength ?: 0)
                lt('length', maxFeatureLength ?: Integer.MAX_VALUE)
                if (sort == "length") {
                    order('length', asc ? "asc" : "desc")
                }
                if (sort == "name") {
                    order('name', asc ? "asc" : "desc")
                }
            }
            def sequenceCounts = Feature.executeQuery("select fl.sequence.name, count(fl.sequence) from Feature f join f.featureLocations fl where fl.sequence.organism = :organism and fl.sequence.length < :maxFeatureLength and fl.sequence.length > :minFeatureLength and f.class in :viewableAnnotationList group by fl.sequence.name", [minFeatureLength: minFeatureLength ?: 0, maxFeatureLength: maxFeatureLength ?: Integer.MAX_VALUE, viewableAnnotationList: FeatureTypeMapper.VIEWABLE_ANNOTATION_LIST, organism: organism])
            def map = [:]
            sequenceCounts.each {
                map[it[0]] = it[1]
            }
            def results = sequences.collect { s ->
                [id: s.id, length: s.length, start: s.start, end: s.end, count: map[s.name] ?: 0, name: s.name, sequenceCount: sequences.size()]
            }
            if (sort == "count") {
                results = results.sort { it.count }
                if (!asc) {
                    results = results.reverse()
                }
            }
            render results ? results[start..Math.min(start + length - 1, results.size() - 1)] as JSON : new JSONObject() as JSON
        }
        catch (PermissionException e) {
            def error = [error: "Error: " + e]
            render error as JSON
        }
    }

    /**
     * Permissions handled upstream
     * @param organism
     * @param max
     * @return
     */
    def report(Organism organism, Integer max) {
        organism = organism ?: Organism.first()
        params.max = Math.min(max ?: 20, 100)

        List<SequenceSummary> sequenceInstanceList = new ArrayList<>()
        List<Sequence> sequences = Sequence.findAllByOrganism(organism, params)

        sequences.each {
            sequenceInstanceList.add(reportService.generateSequenceSummary(it))
        }

        def organisms = permissionService.getOrganismsWithMinimumPermission(permissionService.currentUser,PermissionEnum.ADMINISTRATE)

        int sequenceInstanceCount = Sequence.countByOrganism(organism)
        render view: "report", model: [sequenceInstanceList: sequenceInstanceList, organisms: organisms, organism: organism, sequenceInstanceCount: sequenceInstanceCount]
    }

    @Operation(value = "Get sequence data within a range", nickname = "/<organism name>/<sequence name>:<fmin>..<fmax>?ignoreCache=<ignoreCache>", httpMethod = "get")
    @Parameters([
            @Parameter(name = "organismString", type = "string", paramType = "query", example = "Organism common name or ID(required)")
            , @Parameter(name = "sequenceName", type = "string", paramType = "query", example = "Sequence name(required)")
            , @Parameter(name = "fmin", type = "integer", paramType = "query", example = "Minimum range(required)")
            , @Parameter(name = "fmax", type = "integer", paramType = "query", example = "Maximum range (required)")
            , @Parameter(name = "ignoreCache", type = "boolean", paramType = "query", example = "(default false).  Use cache for request if available.")
    ])
    @Transactional
    String sequenceByLocation(String organismString, String sequenceName, int fmin, int fmax) {

        Boolean ignoreCache = params.ignoreCache != null ? Boolean.valueOf(params.ignoreCache) : false
        Map paramMap = new TreeMap<>()

        if (!ignoreCache) {
            String responseString = sequenceService.checkCache(organismString, sequenceName, fmin, fmax,  paramMap)
            if (responseString) {
                render responseString
                return
            }
        }

        Organism organism = Organism.findByCommonName(organismString) ?: Organism.findById(organismString as Long)
        Sequence sequence = Sequence.findByNameAndOrganism(sequenceName, organism)

        Strand strand = params.strand ? Strand.getStrandForValue(params.strand as Integer) : Strand.POSITIVE
        String sequenceString = sequenceService.getGenomicResiduesFromSequenceWithAlterations(sequence, fmin, fmax, strand)
        sequenceService.cacheRequest(sequenceString, organismString, sequenceName, fmin, fmax,  paramMap)
        render sequenceString

    }

    @Operation(value = "Get sequence data as for a selected name", nickname = "/<organism name>/<sequence name>/<feature name>.<type>?ignoreCache=<ignoreCache>", httpMethod = "get")
    @Parameters([
            @Parameter(name = "organismString", type = "string", paramType = "query", example = "Organism common name or ID (required)")
            , @Parameter(name = "sequenceName", type = "string", paramType = "query", example = "Sequence name (required)")
            , @Parameter(name = "featureName", type = "string", paramType = "query", example = "The uniqueName (UUID) or given name of the feature (typically transcript) of the element to retrieve sequence from")
            , @Parameter(name = "type", type = "string", paramType = "query", example = "(default genomic) Return type: genomic, cds, cdna, peptide")
            , @Parameter(name = "ignoreCache", type = "boolean", paramType = "query", example = "(default false).  Use cache for request if available.")
    ])
    @Deprecated
    @Transactional
    String sequenceByName(String organismString, String sequenceName, String featureName, String type) {

        log.debug "getting sequence by name"
        Boolean ignoreCache = params.ignoreCache != null ? Boolean.valueOf(params.ignoreCache) : false
        Map paramMap = new TreeMap<>()
        paramMap.put("name", featureName)


        if (!ignoreCache) {
            String responseString = sequenceService.checkCache(organismString, sequenceName, featureName, type, paramMap)
            if (responseString) {
                render responseString
                return
            }
        }

        def feature = FeatureTypeMapper.castNeo4jFeature(Feature.executeQuery("MATCH (f:Feature) where f.uniqueName = ${featureName} return f")[0])
        log.debug "retrieving feature ${feature}"
        if (!feature) {
            def features = Feature.findAllByName(featureName)
            log.debug "found by features ${features}"

            for (int i = 0; i < features.size() && !feature; i++) {
                Feature f = features.get(i)
                Sequence s = f.featureLocation.to
                if (f.featureLocation.to.name == sequenceName
                        && (s.organism.commonName == organismString || s.organism.id == organismString)
                ) {
                    feature = f
                }
            }
        }

        if (feature) {
            log.debug "feature found ${feature} for type ${type}"
            String sequenceString = sequenceService.getSequenceForFeature(feature, type)
            log.debug "sequence string returned '${sequenceString}'"
            if(sequenceString?.trim()){
                render sequenceString
                sequenceService.cacheRequest(sequenceString, organismString, sequenceName, featureName, type, paramMap)
                return
            }
        }
        response.status = 404
    }

    @Operation(value = "Remove sequence cache for an organism and sequence", nickname = "/cache/clear/<organism name>/<sequence name>", httpMethod = "get")
    @Parameters([
            @Parameter(name = "organismName", type = "string", paramType = "query", example = "Organism common name (required)")
            , @Parameter(name = "sequenceName", type = "string", paramType = "query", example = "Sequence name (required)")
    ])
    @Transactional
    def clearSequenceCache(String organismName, String sequenceName) {
        if (!checkPermission(organismName)) return
        int removed = SequenceCache.countByOrganismNameAndSequenceName(organismName, sequenceName)
        SequenceCache.deleteAll(SequenceCache.findAllByOrganismNameAndSequenceName(organismName, sequenceName))
        render new JSONObject(removed: removed) as JSON
    }

    @Operation(value = "Remove sequence cache for an organism", nickname = "/cache/clear/<organism name>", httpMethod = "get")
    @Parameters([
            @Parameter(name = "organismName", type = "string", paramType = "query", example = "Organism common name (required) or 'ALL' if admin")
    ])
    @Transactional
    def clearOrganismCache(String organismName) {
        if (organismName.toLowerCase().equals("all") && permissionService.isAdmin()) {
            log.info "Deleting cache for all organisms"
            JSONArray jsonArray = new JSONArray()
            Organism.all.each { organism ->
                int removed = SequenceCache.countByOrganismName(organism.commonName)
                SequenceCache.deleteAll(SequenceCache.findAllByOrganismName(organism.commonName))
                JSONObject jsonObject = new JSONObject(name: organism.commonName, removed: removed) as JSONObject
                jsonArray.add(jsonObject)
            }

            render jsonArray as JSON
        } else {
            log.info "Deleting cache for ${organismName}"
            if (!checkPermission(organismName)) return
            int removed = SequenceCache.countByOrganismName(organismName)
            SequenceCache.deleteAll(SequenceCache.findAllByOrganismName(organismName))
            render new JSONObject(removed: removed) as JSON
        }

    }

    def checkPermission(String organismString) {
//        Organism organism = preferenceService.getOrganismForToken(organismString)
        Organism organism = permissionService.getOrganismForToken(organismString)
        if (organism.publicMode || permissionService.checkPermissions(PermissionEnum.READ)) {
            return true
        } else {
            // not accessible to the public
            response.status = HttpServletResponse.SC_FORBIDDEN
            render ""
            return false
        }

    }
}
