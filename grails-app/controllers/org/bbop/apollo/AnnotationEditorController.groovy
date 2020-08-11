package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import groovy.json.JsonBuilder
import org.apache.shiro.SecurityUtils
import org.bbop.apollo.event.AnnotationEvent
import org.bbop.apollo.event.AnnotationListener
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.sequence.TranslationTable
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONException
import org.grails.web.json.JSONObject
//// import io.swagger.annotations.*
import org.springframework.http.HttpStatus
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.SendTo

import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths
import java.security.Principal

import static grails.async.Promises.task

/**
 * From the WA1 AnnotationEditorService class.
 *
 * This code primarily provides integration with genomic editing functionality visible in the JBrowse window.
 */
//// @Api(value = "Annotation Services: Methods for running the annotation engine")
@Transactional
class AnnotationEditorController extends AbstractApolloController implements AnnotationListener {


    def featureService
    def sequenceService
    def configWrapperService
    def featureRelationshipService
    def featurePropertyService
    def requestHandlingService
    def permissionService
//    def preferenceService
    def sequenceSearchService
    def featureEventService
    def annotationEditorService
    def organismService
    def jsonWebUtilityService
    def cannedCommentService
    def cannedAttributeService
    def availableStatusService
    def brokerMessagingTemplate


    def index() {
        log.debug "bang "
    }

    // Map the operation specified in the URL to a controller
    def handleOperation(String track, String operation) {
        JSONObject postObject = findPost()
        operation = postObject.get(REST_OPERATION)
        def mappedAction = underscoreToCamelCase(operation)
        log.debug "handleOperation ${params.controller} ${operation} -> ${mappedAction}"
        forward action: "${mappedAction}", params: [data: postObject]
    }

    /**
     * @return
     */

    def getUserPermission() {
        log.debug "getUserPermission ${params.data}"
        JSONObject returnObject = permissionService.handleInput(request, params)

        String username = SecurityUtils.subject.principal
        if (username) {
            int permission = PermissionEnum.NONE.value

            User user = User.findByUsername(username)
            log.debug "getting user permission for ${user}, returnObject"
//            Organism organism = preferenceService.getOrganismFromPreferences(user,null,returnObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
//            Organism organism = preferenceService.getCurrentOrganismForCurrentUser(returnObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
            Organism organism = permissionService.getOrganismForToken(returnObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
            if (!organism) {
                log.error "somehow no organism shown, getting for all"
            }
            Map<String, Integer> permissions
            List<PermissionEnum> permissionEnumList = permissionService.getOrganismPermissionsForUser(organism, user)
            permission = permissionService.findHighestEnumValue(permissionEnumList)
            permissions = new HashMap<>()
            permissions.put(username, permission)
            permissions = permissionService.getPermissionsForUser(user)
            if (permissions) {
                session.setAttribute("permissions", permissions);
            }
            if (permissions.values().size() > 0) {
                permission = permissions.values().iterator().next();
            }
            returnObject.put(REST_PERMISSION, permission)
            returnObject.put(REST_USERNAME, username)
            render returnObject
        } else {
            def errorMessage = [message: "You must first login before editing"]
            response.status = 401
            render errorMessage as JSON
        }
    }

    //TODO: parse permissions
    def getDataAdapters() {
        log.debug "getDataAdapters"
        JSONObject returnObject = permissionService.handleInput(request, params)
        def set = configWrapperService.getDataAdapterTools()

        def obj = new JsonBuilder(set)
        def jre = ["data_adapters": obj.content]
        render jre as JSON
    }

//    // @ApiOperation(value = "Gets history for features", nickname = "/annotationEditor/getHistoryForFeatures", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "Sequence name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects unique names.")
//    ])
    def getHistoryForFeatures() {
        log.debug "getHistoryForFeatures ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!inputObject.track && inputObject.sequence) {
            inputObject.track = inputObject.sequence  // support some legacy
        }
        inputObject.put(FeatureStringEnum.USERNAME.value, SecurityUtils.subject.principal)
        JSONArray featuresArray = inputObject.getJSONArray(FeatureStringEnum.FEATURES.value)
        permissionService.checkPermissions(inputObject, PermissionEnum.READ)

        JSONObject historyContainer = jsonWebUtilityService.createJSONFeatureContainer();
        historyContainer = featureEventService.generateHistory(historyContainer, featuresArray)

        render historyContainer as JSON
    }


//    // @ApiOperation(value = "Returns a translation table as JSON", nickname = "/annotationEditor/getTranslationTable", httpMethod = "POST")
//    // @ApiImplicitParams([])
    def getTranslationTable() {
        log.debug "getTranslationTable"
        JSONObject returnObject = permissionService.handleInput(request, params)
        log.debug "return object ${returnObject as JSON}"
//        Organism organism = preferenceService.getCurrentOrganismForCurrentUser(returnObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
        Organism organism = permissionService.getOrganismForToken(returnObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
        log.debug "has organism ${organism}"
        // use the over-wridden one
        TranslationTable translationTable = organismService.getTranslationTable(organism)

        JSONObject ttable = new JSONObject()
        for (Map.Entry<String, String> t : translationTable.getTranslationTable().entrySet()) {
            ttable.put(t.getKey(), t.getValue())
        }

        JSONArray startProteins = new JSONArray()
        JSONArray stopProteins = new JSONArray()

        for (String startCodon in translationTable.getStartCodons()) {
            startProteins.add(translationTable.getTranslationTable().get(startCodon))
        }
        for (String stopCodon in translationTable.getStopCodons()) {
            stopProteins.add(translationTable.getTranslationTable().get(stopCodon))
        }

        returnObject.put(REST_TRANSLATION_TABLE, ttable)
        returnObject.put(REST_START_PROTEINS, startProteins.unique())
        returnObject.put(REST_STOP_PROTEINS, stopProteins.unique())
        render returnObject
    }


//    // @ApiOperation(value = "Add non-coding genomic feature", nickname = "/annotationEditor/addFeature", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "suppressHistory", type = "boolean", paramType = "query", example = "Suppress the history of this operation")
//            // , @ApiImplicitParam(name = "suppressEvents", type = "boolean", paramType = "query", example = "Suppress instant update of the user interface")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ])
    def addFeature() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addFeature(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set exon feature boundaries", nickname = "/annotationEditor/setExonBoundaries", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "suppressHistory", type = "boolean", paramType = "query", example = "Suppress the history of this operation")
//            // , @ApiImplicitParam(name = "suppressEvents", type = "boolean", paramType = "query", example = "Suppress instant update of the user interface")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def setExonBoundaries() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setExonBoundaries(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Add an exon", nickname = "/annotationEditor/addExon", httpMethod = "POST"
//    )
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "suppressHistory", type = "boolean", paramType = "query", example = "Suppress the history of this operation")
//            // , @ApiImplicitParam(name = "suppressEvents", type = "boolean", paramType = "query", example = "Suppress instant update of the user interface")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def addExon() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addExon(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Add comments", nickname = "/annotationEditor/addComments", httpMethod = "POST"
//    )
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects ('uniquename' required) that include an added 'comments' JSONArray described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def addComments() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addComments(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete comments", nickname = "/annotationEditor/deleteComments", httpMethod = "POST"
//    )
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects ('uniquename' required) that include an added 'comments' JSONArray described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def deleteComments() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteComments(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Update comments", nickname = "/annotationEditor/updateComments", httpMethod = "POST"
//    )
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects ('uniquename' required) that include an added 'old_comments','new_comments' JSONArray described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def updateComments() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.updateComments(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Get comments", nickname = "/annotationEditor/getComments", httpMethod = "POST"
//    )
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects ('uniquename' required) JSONArray described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ]
//    )
    def getComments() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            render requestHandlingService.getComments(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Add transcript", nickname = "/annotationEditor/addTranscript", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "suppressHistory", type = "boolean", paramType = "query", example = "Suppress the history of this operation")
//            // , @ApiImplicitParam(name = "suppressEvents", type = "boolean", paramType = "query", example = "Suppress instant update of the user interface")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of JSON feature objects described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/Feature.groovy")
//    ])
    def addTranscript() {
        try {
            log.debug "addTranscript ${params}"
            JSONObject inputObject = permissionService.handleInput(request, params)
            if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
                render requestHandlingService.addTranscript(inputObject)
            } else {
                render status: HttpStatus.UNAUTHORIZED
            }
        }
        catch (Exception e) {
            def error = [error: e.message]
            render error as JSON
        }
    }

//    // @ApiOperation(value = "Duplicate transcript", nickname = "/annotationEditor/duplicateTranscript", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "suppressHistory", type = "boolean", paramType = "query", example = "Suppress the history of this operation")
//            // , @ApiImplicitParam(name = "suppressEvents", type = "boolean", paramType = "query", example = "Suppress instant update of the user interface")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing a single JSONObject feature that contains 'uniquename'")
//    ])
    def duplicateTranscript() {
        log.debug "duplicateTranscript ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.duplicateTranscript(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set translation start", nickname = "/annotationEditor/setTranslationStart", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing a single JSONObject feature that contains {'uniquename':'ABCD-1234','location':{'fmin':12}}")
//    ])
    def setTranslationStart() {
        log.debug "setTranslationStart ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setTranslationStart(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set translation end", nickname = "/annotationEditor/setTranslationEnd", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing a single JSONObject feature that contains {'uniquename':'ABCD-1234','location':{'fmax':12}}")
//    ])
    def setTranslationEnd() {
        log.debug "setTranslationEnd ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setTranslationEnd(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set longest ORF", nickname = "/annotationEditor/setLongestOrf", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing a single JSONObject feature that contains {'uniquename':'ABCD-1234'}")
//    ])
    def setLongestOrf() {
        log.debug "setLongestORF ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setLongestOrf(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set boundaries of genomic feature", nickname = "/annotationEditor/setBoundaries", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing feature objects with the location object defined {'uniquename':'ABCD-1234','location':{'fmin':2,'fmax':12}}")
//    ])
    def setBoundaries() {
        log.debug "setBoundaries ${params}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setBoundaries(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Get all annotated features for a sequence", nickname = "/annotationEditor/getFeatures", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//    ])
    def getFeatures() {
        JSONObject returnObject = permissionService.handleInput(request, params)
        try {
            permissionService.checkPermissions(returnObject, PermissionEnum.READ)
            render requestHandlingService.getFeatures(returnObject)
        } catch (e) {
            def error = [error: 'problem getting features: ' + e.fillInStackTrace()]
            render error as JSON
            log.error(error.error)
        }
    }


//    // @ApiOperation(value = "Get sequence alterations for a given sequence", nickname = "/annotationEditor/getSequenceAlterations", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//    ])
    def getSequenceAlterations() {
        JSONObject returnObject = permissionService.handleInput(request, params)
        Sequence sequence = permissionService.checkPermissions(returnObject, PermissionEnum.READ)
        JSONArray jsonFeatures = new JSONArray()
        returnObject.put(FeatureStringEnum.FEATURES.value, jsonFeatures)

        List<SequenceAlterationArtifact> sequenceAlterationList = Feature.executeQuery("select f from Feature f join f.featureLocations fl join fl.sequence s where s = :sequence and f.class in :sequenceTypes"
                , [sequence: sequence, sequenceTypes: requestHandlingService.viewableAlterations])
        for (SequenceAlterationArtifact alteration : sequenceAlterationList) {
            jsonFeatures.put(featureService.convertFeatureToJSON(alteration, true));
        }

        render returnObject
    }


    def getAnnotationInfoEditorConfiguration() {
        JSONObject annotationInfoEditorConfigContainer = new JSONObject();
        JSONArray annotationInfoEditorConfigs = new JSONArray();
        annotationInfoEditorConfigContainer.put(FeatureStringEnum.ANNOTATION_INFO_EDITOR_CONFIGS.value, annotationInfoEditorConfigs);
        JSONObject annotationInfoEditorConfig = new JSONObject();
        annotationInfoEditorConfigs.put(annotationInfoEditorConfig);

        annotationInfoEditorConfig.put(FeatureStringEnum.HASDBXREFS.value, configWrapperService.hasDbxrefs());
        annotationInfoEditorConfig.put(FeatureStringEnum.HASATTRIBUTES.value, configWrapperService.hasAttributes());
        annotationInfoEditorConfig.put(FeatureStringEnum.HASPUBMEDIDS.value, configWrapperService.hasPubmedIds());
        annotationInfoEditorConfig.put(FeatureStringEnum.HASGOIDS.value, configWrapperService.hasGoIds());
        annotationInfoEditorConfig.put(FeatureStringEnum.HASCOMMENTS.value, configWrapperService.hasComments());
        JSONArray supportedTypes = new JSONArray();
        supportedTypes.add(FeatureStringEnum.DEFAULT.value)
        annotationInfoEditorConfig.put(FeatureStringEnum.SUPPORTED_TYPES.value, supportedTypes);
        log.debug "return config ${annotationInfoEditorConfigContainer}"
        render annotationInfoEditorConfigContainer
    }

//    // @ApiOperation(value = "Set name of a feature", nickname = "/annotationEditor/setName", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','name':'gene01'}")
//    ])
    def setName() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setName(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set description for a feature", nickname = "/annotationEditor/setDescription", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','description':'some descriptive test'}")
//    ])
    def setDescription() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setDescription(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set symbol of a feature", nickname = "/annotationEditor/setSymbol", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','symbol':'Pax6a'}")
//    ])
    def setSymbol() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setSymbol(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set status of a feature", nickname = "/annotationEditor/setStatus", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','status':'existing-status-string'}.  Available status found here: /availableStatus/ ")
//    ])
    def setStatus() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setStatus(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Add attribute (key,value pair) to feature", nickname = "/annotationEditor/addAttribute", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','non_reserved_properties':[{'tag':'clockwork','value':'orange'},{'tag':'color','value':'purple'}]}.  Available status found here: /availableStatus/ ")
//    ])
    def addAttribute() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addNonReservedProperties(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete attribute (key,value pair) for feature", nickname = "/annotationEditor/deleteAttribute", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','non_reserved_properties':[{'tag':'clockwork','value':'orange'},{'tag':'color','value':'purple'}]}.  Available status found here: /availableStatus/ ")
//    ])
    def deleteAttribute() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteNonReservedProperties(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Update attribute (key,value pair) for feature", nickname = "/annotationEditor/updateAttribute", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','old_non_reserved_properties':[{'color': 'red'}], 'new_non_reserved_properties': [{'color': 'green'}]}.")
//    ])
    def updateAttribute() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.updateNonReservedProperties(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Add dbxref (db,id pair) to feature", nickname = "/annotationEditor/addDbxref", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','dbxrefs': [{'db': 'PMID', 'accession': '19448641'}]}.")
//    ])
    def addDbxref() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addNonPrimaryDbxrefs(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Update dbxrefs (db,id pairs) for a feature", nickname = "/annotationEditor/updateDbxref", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','old_dbxrefs': [{'db': 'PMID', 'accession': '19448641'}], 'new_dbxrefs': [{'db': 'PMID', 'accession': '19448642'}]}.")
//    ])
    def updateDbxref() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.updateNonPrimaryDbxrefs(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete dbxrefs (db,id pairs) for a feature", nickname = "/annotationEditor/deleteDbxref", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','dbxrefs': [{'db': 'PMID', 'accession': '19448641'}]}.")
//    ])
    def deleteDbxref() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteNonPrimaryDbxrefs(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Get information about a sequence alteration object e.g,. features[{'uniquename':'someunqiuenamestring'}],", nickname = "/annotationEditor/getInformation", httpMethod = "POST")
//    // @ApiImplicitParams([
//      // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//      // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//      // , @ApiImplicitParam(name = "array of uniquename features", type = "string", paramType = "query", example = "Uniquename of sequence alteration retrieve stringsgs embedded in a features array.")
//    ])
    def getInformation() {
        JSONObject featureContainer = jsonWebUtilityService.createJSONFeatureContainer();
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.checkPermissions(PermissionEnum.WRITE)) {
            render new JSONObject() as JSON
            return
        }
        JSONArray featuresArray = inputObject.getJSONArray(FeatureStringEnum.FEATURES.value)

        for (int i = 0; i < featuresArray.size(); ++i) {
            JSONObject jsonFeature = featuresArray.getJSONObject(i);
            String uniqueName = jsonFeature.getString(FeatureStringEnum.UNIQUENAME.value);
            Feature gbolFeature = Feature.findByUniqueName(uniqueName)
            JSONObject info = new JSONObject();
            info.put(FeatureStringEnum.UNIQUENAME.value, uniqueName)
            info.put("time_accessioned", gbolFeature.lastUpdated)
            info.put("owner", gbolFeature.owner ? gbolFeature.owner.username : "N/A")
            info.put("location", gbolFeature.featureLocation.fmin)
            if(gbolFeature instanceof SequenceAlterationArtifact){
                info.put("length", gbolFeature.offset)
            }
            if(gbolFeature instanceof SequenceAlteration && gbolFeature.alterationResidue){
                info.put("length", gbolFeature?.alterationResidue?.size())
            }
            String parentIds = "";
            featureRelationshipService.getParentForFeature(gbolFeature).each {
                if (parentIds.length() > 0) {
                    parentIds += ", ";
                }
                parentIds += it.getUniqueName();
            }
            if (parentIds.length() > 0) {
                info.put("parent_ids", parentIds);
            }
            def featureProperties = featurePropertyService.getNonReservedProperties(gbolFeature);
            featureProperties.each {
                info.put(it.tag, it.value);
            }
            featureContainer.getJSONArray(FeatureStringEnum.FEATURES.value).put(info);
        }

        render featureContainer
    }

//    // @ApiOperation(value = "Get attribute (key/value) pairs for a feature", nickname = "/annotationEditor/getAttributes", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "feature", type = "JSONObject", paramType = "query", example = "object containing JSON objects with {'uniquename':'ABCD-1234','dbxrefs': [{'db': 'PMID', 'accession': '19448641'}]}.")
//    ])
    def getAttributes() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            String uniqueName = inputObject.getString(FeatureStringEnum.UNIQUENAME.value)
            Feature feature = Feature.findByUniqueName(uniqueName)
            JSONArray attributes = new JSONArray()
            feature.featureProperties.each {
                if (it.ontologyId != Comment.ontologyId) {
                    JSONObject attributeObject = new JSONObject()
                    attributeObject.put(FeatureStringEnum.TAG.value, it.tag)
                    attributeObject.put(FeatureStringEnum.VALUE.value, it.value)
                    attributes.add(attributeObject)
                }
            }
            JSONObject returnObject = new JSONObject()
            returnObject.put(FeatureStringEnum.ATTRIBUTES.value, attributes)
            render returnObject as JSON
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Get dbxrefs (db,id pairs) for a feature", nickname = "/annotationEditor/getDbxrefs", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing JSON objects with {'uniquename':'ABCD-1234','dbxrefs': [{'db': 'PMID', 'accession': '19448641'}]}.")
//    ])
    def getDbxrefs() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            String uniqueName = inputObject.getString(FeatureStringEnum.UNIQUENAME.value)
            Feature feature = Feature.findByUniqueName(uniqueName)
            JSONArray annotations = new JSONArray()
            feature.featureDBXrefs.each {
                JSONObject dbxrefObject = new JSONObject()
                dbxrefObject.put(FeatureStringEnum.TAG.value, it.db.name)
                dbxrefObject.put(FeatureStringEnum.VALUE.value, it.accession)
                annotations.add(dbxrefObject)

            }
            JSONObject returnObject = new JSONObject()
            returnObject.put("annotations", annotations)
            render returnObject as JSON
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Set readthrough stop codon", nickname = "/annotationEditor/setReadthroughStopCodon", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with one feature object {'uniquename':'ABCD-1234'}")
//    ])
    def setReadthroughStopCodon() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.setReadthroughStopCodon(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Add sequence alteration", nickname = "/annotationEditor/addSequenceAlteration", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with Sequence Alteration (Insertion, Deletion, Substituion) objects described by https://github.com/GMOD/Apollo/blob/master/grails-app/domain/org/bbop/apollo/")
//    ])
    def addSequenceAlteration() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.addSequenceAlteration(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete sequence alteration", nickname = "/annotationEditor/deleteSequenceAlteration", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with Sequence Alteration identified by unique names {'uniquename':'ABC123'}")
//    ])
    def deleteSequenceAlteration() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteSequenceAlteration(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Flip strand", nickname = "/annotationEditor/flipStrand", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with with objects of features defined as {'uniquename':'ABC123'}")
//    ])
    def flipStrand() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.flipStrand(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Merge exons", nickname = "/annotationEditor/mergeExons", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with with two objects of referred to as defined as {'uniquename':'ABC123'}")
//    ])
    def mergeExons() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.mergeExons(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Split exons", nickname = "/annotationEditor/splitExon", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing feature objects with the location object defined {'uniquename':'ABCD-1234','location':{'fmin':2,'fmax':12}}")
//    ])
    def splitExon() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.splitExon(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Delete feature", nickname = "/annotationEditor/deleteFeature", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of features objects to delete defined by unique name {'uniquename':'ABC123'}")
//    ])
    def deleteFeature() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteFeature(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }


//    // @ApiOperation(value = "Delete variant effects for sequences", nickname = "/annotationEditor/deleteVariantEffectsForSequences", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "sequence", type = "JSONArray", paramType = "query", example = "JSONArray of sequence id object to delete defined by {id:<sequence.id>} ")
//    ])
    def deleteVariantEffectsForSequences() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.removeVariantEffect(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete features for sequences", nickname = "/annotationEditor/deleteFeaturesForSequences", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "sequence", type = "JSONArray", paramType = "query", example = "JSONArray of sequence id object to delete defined by {id:<sequence.id>} ")
//    ])
    def deleteFeaturesForSequences() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            // create features from sequences
            JSONArray features = new JSONArray()
            inputObject.features = features
            List<Long> sequenceList = inputObject.sequence.collect {
                return Long.valueOf(it.id)
            }
            List<String> featureUniqueNames = Feature.executeQuery("select f.uniqueName from Feature f left join f.parentFeatureRelationships pfr  join f.featureLocations fl join fl.sequence s   where f.childFeatureRelationships is empty and s.id in (:sequenceList) and f.class in (:viewableTypes) ", [sequenceList: sequenceList, viewableTypes: requestHandlingService.viewableAnnotationList])
            featureUniqueNames.each {
                def jsonObject = new JSONObject()
                jsonObject.put(FeatureStringEnum.UNIQUENAME.value, it)
                features.add(jsonObject)
            }
            inputObject.remove("sequence")
            render requestHandlingService.deleteFeature(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Delete exons", nickname = "/annotationEditor/deleteExon", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of features objects, where the first is the parent transcript and the remaining are exons all defined by a unique name {'uniquename':'ABC123'}")
//    ])
    def deleteExon() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.deleteExon(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Make intron", nickname = "/annotationEditor/makeIntron", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray containing a single JSONObject feature that contains {'uniquename':'ABCD-1234','location':{'fmin':12}}")
//    ])
    def makeIntron() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.makeIntron(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Split transcript", nickname = "/annotationEditor/splitTranscript", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with with two exon objects referred to their unique names {'uniquename':'ABC123'}")
//    ])
    def splitTranscript() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.splitTranscript(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Merge transcripts", nickname = "/annotationEditor/mergeTranscripts", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray with with two transcript objects referred to their unique names {'uniquename':'ABC123'}")
//    ])
    def mergeTranscripts() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (permissionService.hasPermissions(inputObject, PermissionEnum.WRITE)) {
            render requestHandlingService.mergeTranscripts(inputObject)
        } else {
            render status: HttpStatus.UNAUTHORIZED
        }
    }

//    // @ApiOperation(value = "Get sequence for feature", nickname = "/annotationEditor/getSequence", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "sequence", type = "string", paramType = "query", example = "(optional) Sequence name")
//            // , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "(optional) Organism ID or common name")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of features objects to export defined by a unique name {'uniquename':'ABC123'}")
//    ])
    def getSequence() {
        log.debug "getSequence ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        try{
            permissionService.hasPermissions(inputObject, PermissionEnum.EXPORT)
            JSONObject featureContainer = jsonWebUtilityService.createJSONFeatureContainer()
            JSONObject sequenceObject = sequenceService.getSequenceForFeatures(inputObject)
            featureContainer.getJSONArray(FeatureStringEnum.FEATURES.value).put(sequenceObject)
            render featureContainer
        }
        catch (AnnotationException ae) {
            def error = [error: ae.message]
            render error as JSON
        }
    }

//    // @ApiOperation(value = "Get sequences search tools", nickname = "/annotationEditor/getSequenceSearchTools")
    def getSequenceSearchTools() {
        log.debug "getSequenceSearchTools ${params.data}"
        def set = configWrapperService.getSequenceSearchTools()
        def obj = new JsonBuilder(set)
        def jre = ["sequence_search_tools": obj.content]
        render jre as JSON
    }

    private String getOntologyIdForType(String type) {
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
        return featureService.convertJSONToOntologyId(cvTerm)
    }

    private List<FeatureType> getFeatureTypeListForType(String type) {
        String ontologyId = getOntologyIdForType(type)
        return FeatureType.findAllByOntologyId(ontologyId)
    }

//    // @ApiOperation(value = "Get canned comments", nickname = "/annotationEditor/getCannedComments", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//    ])
    def getCannedComments() {
        log.debug "canned comment data ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }

        Organism organism = Organism.findById(inputObject.getLong(FeatureStringEnum.ORGANISM_ID.value))
        String type = inputObject.getString(FeatureStringEnum.TYPE.value)
        List<FeatureType> featureTypeList = getFeatureTypeListForType(type)
        render cannedCommentService.getCannedComments(organism, featureTypeList) as JSON
    }

//    // @ApiOperation(value = "Get canned keys", nickname = "/annotationEditor/getCannedKeys", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//    ])
    def getCannedKeys() {
        log.debug "canned key data ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }

        Organism organism = Organism.findById(inputObject.getLong(FeatureStringEnum.ORGANISM_ID.value))
        String type = inputObject.getString(FeatureStringEnum.TYPE.value)
        List<FeatureType> featureTypeList = getFeatureTypeListForType(type)
        render cannedAttributeService.getCannedKeys(organism, featureTypeList) as JSON
    }

//    // @ApiOperation(value = "Get canned values", nickname = "/annotationEditor/getCannedValues", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//    ])
    def getCannedValues() {
        log.debug "canned value data ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }

        Organism organism = Organism.findById(inputObject.getLong(FeatureStringEnum.ORGANISM_ID.value))
        String type = inputObject.getString(FeatureStringEnum.TYPE.value)
        List<FeatureType> featureTypeList = getFeatureTypeListForType(type)
        render cannedAttributeService.getCannedValues(organism, featureTypeList) as JSON
    }

//    // @ApiOperation(value = "Get available statuses", nickname = "/annotationEditor/getAvailableStatuses", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "organismId", type = "string", paramType = "query")
//            // , @ApiImplicitParam(name = "type", type = "string", paramType = "query")
//    ])
    def getAvailableStatuses() {
        log.debug "get available statuses${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(inputObject, PermissionEnum.READ)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }

        Organism organism = Organism.findById(inputObject.getLong(FeatureStringEnum.ORGANISM_ID.value))
        String type = null
        if (inputObject.containsKey(FeatureStringEnum.TYPE.value)) {
            type = inputObject.getString(FeatureStringEnum.TYPE.value)
        }
        List<FeatureType> featureTypeList = type ? getFeatureTypeListForType(type) : []
        log.debug "type ${type} ${featureTypeList}"
        render availableStatusService.getAvailableStatuses(organism, featureTypeList) as JSON
    }

//    // @ApiOperation(value = "Search sequences", nickname = "/annotationEditor/searchSequences", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "search", type = "JSONObject", paramType = "query", example = "{'key':'blat_prot','residues':'ATACTAGAGATAC':'database_id':'abc123'}")
//    ])
    def searchSequence() {
        log.debug "sequenceSearch data ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        try{
            permissionService.hasPermissions(inputObject, PermissionEnum.READ)
//            Organism organism = preferenceService.getCurrentOrganismForCurrentUser(inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
            Organism organism = permissionService.getOrganismForToken(inputObject.getString(FeatureStringEnum.CLIENT_TOKEN.value))
            log.debug "Organism to string:  ${organism as JSON}"
            render sequenceSearchService.searchSequence(inputObject, organism.getBlatdb())
        }
        catch (AnnotationException ae) {
            def error = [error: ae.message]
            render error as JSON
        }
    }


//    // @ApiOperation(value = "Get gff3", nickname = "/annotationEditor/getGff3", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "features", type = "JSONArray", paramType = "query", example = "JSONArray of features objects to export defined by a unique name {'uniquename':'ABC123'}")
//    ])
    def getGff3() {
        log.debug "getGff3 ${params.data}"
        JSONObject inputObject = permissionService.handleInput(request, params)
        try {
            permissionService.hasPermissions(inputObject, PermissionEnum.EXPORT)
            File outputFile = File.createTempFile("feature", ".gff3")
            sequenceService.getGff3ForFeature(inputObject, outputFile)
            Charset encoding = Charset.defaultCharset()
            byte[] encoded = Files.readAllBytes(Paths.get(outputFile.getAbsolutePath()))
            String gff3String = new String(encoded, encoding)
            outputFile.delete() // deleting temp file
            render gff3String

        }
        catch (AnnotationException ae) {
            def error = [error: ae.message]
            render error as JSON
        }
        catch (IOException e) {
            log.debug("Cannot create a temp file for 'get GFF3' operation", e)
            e.printStackTrace()
        }
    }

//    // @ApiOperation(value = "Get genes created or updated in the past, Returns JSON hash gene_name:organism", nickname = "/annotationEditor/getRecentAnnotations", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "days", type = "Integer", paramType = "query", example = "Number of past days to retrieve annotations from.")
//
//    ])
    def getRecentAnnotations() {
        JSONObject inputObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(inputObject, PermissionEnum.EXPORT)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }

        if (inputObject.get('days') instanceof Integer) {
            JsonBuilder updatedGenes = annotationEditorService.recentAnnotations(inputObject.get('days'))
            render updatedGenes
        } else {
            def error = [error: inputObject.get('days') + ' Param days must be an Integer']
            render error as JSON
        }
    }


    @MessageMapping("/AnnotationNotification")
    @SendTo("/topic/AnnotationNotification")
    protected String annotationEditor(String inputString, Principal principal) {
        inputString = annotationEditorService.cleanJSONString(inputString)
        JSONObject rootElement = (JSONObject) JSON.parse(inputString)
        rootElement.put(FeatureStringEnum.USERNAME.value, principal.name)

        String operation = ((JSONObject) rootElement).get(REST_OPERATION)

        String operationName = underscoreToCamelCase(operation)
        log.debug "operationName: ${operationName}"
        def p = task {
            switch (operationName) {
                case "logout":
                    SecurityUtils.subject.logout()
                    break
                case "setToDownstreamDonor": requestHandlingService.setDonor(rootElement, false)
                    break
                case "setToUpstreamDonor": requestHandlingService.setDonor(rootElement, true)
                    break
                case "setToDownstreamAcceptor": requestHandlingService.setAcceptor(rootElement, false)
                    break
                case "setToUpstreamAcceptor": requestHandlingService.setAcceptor(rootElement, true)
                    break
                default:
                    boolean foundMethod = false
                    String returnString = null
                    requestHandlingService.getClass().getMethods().each { method ->
                        if (method.name == operationName) {
                            foundMethod = true
                            log.debug "found the method ${operationName}"
                            Feature.withNewSession {
                                try {
                                    returnString = method.invoke(requestHandlingService, rootElement)
                                } catch (e) {
                                    log.error("CAUGHT ERROR through websocket call: " + e)
                                    if (e instanceof InvocationTargetException || !e.message) {
                                        log.error("THROWING PARENT ERROR instead through reflection: " + e.getCause())
                                        return sendError(e.getCause(), principal?.name)
                                    } else {
                                        return sendError(e, principal?.name)
                                    }
                                }
                            }
                            return returnString
                        }
                    }
                    if (foundMethod) {
                        return returnString
                    } else {
                        log.error "METHOD NOT found ${operationName}"
                        throw new AnnotationException("Operation ${operationName} not found")
                    }
                    break
            }
        }
        try {
            def results = p.get()
            return results
        } catch (Exception ae) {
            // TODO: should be returning nothing, but then broadcasting specifically to this user
            log.error("Error for user ${principal?.name} when exexecting ${inputString}" + ae?.message)
            return sendError(ae, principal.name)
        }

    }

// TODO: handle errors without broadcasting
    protected def sendError(Throwable exception, String username) {
        log.error "exception ${exception}"
        log.error "exception message ${exception.message}"
        log.error "username ${username}"

        JSONObject errorObject = new JSONObject()
        errorObject.put(REST_OPERATION, FeatureStringEnum.ERROR.name())
        errorObject.put(FeatureStringEnum.ERROR_MESSAGE.value, exception.message)
        errorObject.put(FeatureStringEnum.USERNAME.value, username)

        def destination = "/topic/AnnotationNotification/user/" + username
        log.error "error destination message: ${destination}"
        brokerMessagingTemplate.convertAndSend(destination, exception.message ?: exception.fillInStackTrace().fillInStackTrace())

        return errorObject.toString()
    }


    @SendTo("/topic/AnnotationNotification")
    protected String sendAnnotationEvent(String returnString) {
        log.debug "sendAnnotationEvent ${returnString?.size()}"
        return returnString
    }

    synchronized void handleChangeEvent(AnnotationEvent... events) {
        log.debug "handleChangeEvent ${events.length}"
        if (events.length == 0) {
            return;
        }
        JSONArray operations = new JSONArray();
        for (AnnotationEvent event : events) {
            JSONObject features = event.getFeatures();
            try {
                features.put("operation", event.getOperation().name());
                features.put("sequenceAlterationEvent", event.isSequenceAlterationEvent());
                operations.put(features);
            }
            catch (JSONException e) {
                log.error("error handling change event ${event}: ${e}")
            }
        }

        sendAnnotationEvent(operations.toString())

    }


}
