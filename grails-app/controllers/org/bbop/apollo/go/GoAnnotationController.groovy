package org.bbop.apollo.go

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.micronaut.http.annotation.Post
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import io.swagger.v3.oas.annotations.enums.ParameterIn
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.user.User
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.history.FeatureOperation
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import static org.springframework.http.HttpStatus.NOT_FOUND

@Transactional(readOnly = true)
class GoAnnotationController {


  def permissionService
  def goAnnotationService
  def featureEventService
  def featureService

  @Post
  @Operation(description = "Load Go Annotations for feature", summary = "/goAnnotation", method= "POST")
  @Parameters([
      @Parameter(name = "username",  in = ParameterIn.HEADER)
      , @Parameter(name = "password",  in = ParameterIn.HEADER)
      , @Parameter(name = "uniqueName",  in = ParameterIn.HEADER , example = "Feature name to query on")
  ]
  )
  def index() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.READ)
    Feature feature = Feature.findByUniqueName(dataObject.uniqueName as String)
    if (feature) {
      JSONObject annotations = goAnnotationService.getAnnotations(feature)
      // TODO: register with marshaller
      render annotations as JSON
    } else {
      render status: NOT_FOUND
    }
  }

//        {"gene":"e35ea570-f700-41fb-b479-70aa812174ad",
//        "goTerm":"GO:0060841",
//        "geneRelationship":"RO:0002616",
//        "evidenceCode":"ECO:0000335",
//        "negate":false,
//        "withOrFrom":["withprefix:12312321"],
//        "references":["refprefix:44444444"]}
  @Operation(value = "Save New Go Annotations for feature", nickname = "/goAnnotation/save", httpMethod = "POST")
  @Parameters([
      @Parameter(name = "username", type = "email", paramType = "query")
      , @Parameter(name = "password", type = "password", paramType = "query")
      , @Parameter(name = "feature", type = "string", paramType = "query", example = "uniqueName of feature feature to query on")
      , @Parameter(name = "goTerm", type = "string", paramType = "query", example = "GO CURIE")
      , @Parameter(name = "goTermLabel", type = "string", paramType = "query", example = "GO Term Label")
      , @Parameter(name = "aspect", type = "string", paramType = "query", example = "(required) BP, MF, CC")
      , @Parameter(name = "geneRelationship", type = "string", paramType = "query", example = "Gene relationship (RO) CURIE")
      , @Parameter(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
      , @Parameter(name = "evidenceCodeLAbel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
      , @Parameter(name = "negate", type = "boolean", paramType = "query", example = "Negate evidence (default false)")
      , @Parameter(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312]]\"]}")
      , @Parameter(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312]]\"]}")
  ]
  )
  @Transactional
  def save() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)
    GoAnnotation goAnnotation = new GoAnnotation()
    Feature feature = Feature.findByUniqueName(dataObject.feature)

    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

    goAnnotation.feature = feature
    goAnnotation.aspect = dataObject.aspect
    goAnnotation.goRef = dataObject.goTerm
    goAnnotation.geneProductRelationshipRef = dataObject.geneRelationship
    goAnnotation.evidenceRef = dataObject.evidenceCode
    goAnnotation.goRefLabel = dataObject.goTermLabel
    goAnnotation.evidenceRefLabel = dataObject.evidenceCodeLabel
    goAnnotation.negate = dataObject.negate ?: false
    goAnnotation.withOrFromArray = dataObject.withOrFrom
    goAnnotation.notesArray = dataObject.notes
    goAnnotation.reference = dataObject.reference
    goAnnotation.lastUpdated = new Date()
    goAnnotation.dateCreated = new Date()
    goAnnotation.addToOwners(user)
    feature.addToGoAnnotations(goAnnotation)
    goAnnotation.save(flush: true, failOnError: true)

    JSONArray oldFeaturesJsonArray = new JSONArray()
    oldFeaturesJsonArray.add(originalFeatureJsonObject)
    JSONArray newFeaturesJsonArray = new JSONArray()
    JSONObject currentFeatureJsonObject = featureService.convertFeatureToJSON(feature)
    newFeaturesJsonArray.add(currentFeatureJsonObject)

    featureEventService.addNewFeatureEvent(FeatureOperation.ADD_GO_ANNOTATION,
        feature.name,
        feature.uniqueName,
        dataObject,
        oldFeaturesJsonArray,
        newFeaturesJsonArray,
        user)

    JSONObject annotations = goAnnotationService.getAnnotations(feature)
    render annotations as JSON
  }

  @Operation(value = "Update existing Go Annotations for feature", nickname = "/goAnnotation/update", httpMethod = "POST")
  @Parameters([
      @Parameter(name = "username", type = "email", paramType = "query")
      , @Parameter(name = "password", type = "password", paramType = "query")
      , @Parameter(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to update (required)")
      , @Parameter(name = "feature", type = "string", paramType = "query", example = "uniqueName of feature to query on")
      , @Parameter(name = "goTerm", type = "string", paramType = "query", example = "GO CURIE")
      , @Parameter(name = "goTermLabel", type = "string", paramType = "query", example = "GO Term Label")
      , @Parameter(name = "aspect", type = "string", paramType = "query", example = "(required) BP, MF, CC")
      , @Parameter(name = "geneRelationship", type = "string", paramType = "query", example = "Gene relationship (RO) CURIE")
      , @Parameter(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
      , @Parameter(name = "evidenceCodeLabel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
      , @Parameter(name = "negate", type = "boolean", paramType = "query", example = "Negate evidence (default false)")
      , @Parameter(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312]]\"]}")
      , @Parameter(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312]]\"]}")
  ]
  )
  @Transactional
  def update() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)
    Feature feature = Feature.findByUniqueName(dataObject.feature)

    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)


    GoAnnotation goAnnotation = GoAnnotation.findById(dataObject.id)
    goAnnotation.aspect = dataObject.aspect
    goAnnotation.goRef = dataObject.goTerm
    goAnnotation.geneProductRelationshipRef = dataObject.geneRelationship
    goAnnotation.evidenceRef = dataObject.evidenceCode
    goAnnotation.goRefLabel = dataObject.goTermLabel
    goAnnotation.evidenceRefLabel = dataObject.evidenceCodeLabel
    goAnnotation.negate = dataObject.negate ?: false
    goAnnotation.withOrFromArray = dataObject.withOrFrom
    goAnnotation.notesArray = dataObject.notes
    goAnnotation.reference = dataObject.reference
    goAnnotation.lastUpdated = new Date()
    goAnnotation.addToOwners(user)
    goAnnotation.save(flush: true, failOnError: true, insert: false)

    JSONArray oldFeaturesJsonArray = new JSONArray()
    oldFeaturesJsonArray.add(originalFeatureJsonObject)
    JSONArray newFeaturesJsonArray = new JSONArray()
    JSONObject currentFeatureJsonObject = featureService.convertFeatureToJSON(feature)
    newFeaturesJsonArray.add(currentFeatureJsonObject)

    featureEventService.addNewFeatureEvent(FeatureOperation.UPDATE_GO_ANNOTATION,
        feature.name,
        feature.uniqueName,
        dataObject,
        oldFeaturesJsonArray,
        newFeaturesJsonArray,
        user)

    JSONObject annotations = goAnnotationService.getAnnotations(feature)
    render annotations as JSON
  }

  @Operation(value = "Delete existing Go Annotations for feature", nickname = "/goAnnotation/delete", httpMethod = "POST")
  @Parameters([
      @Parameter(name = "username", type = "email", paramType = "query")
      , @Parameter(name = "password", type = "password", paramType = "query")
      , @Parameter(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to delete (required)")
      , @Parameter(name = "uniqueName", type = "string", paramType = "query", example = "Gene uniqueName to remove feature from")
  ]
  )
  @Transactional
  def delete() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)

    Feature feature = Feature.findByUniqueName(dataObject.feature)
    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

    GoAnnotation goAnnotation = GoAnnotation.findById(dataObject.id)
    feature.removeFromGoAnnotations(goAnnotation)
    goAnnotation.delete(flush: true)

    JSONArray oldFeaturesJsonArray = new JSONArray()
    oldFeaturesJsonArray.add(originalFeatureJsonObject)
    JSONArray newFeaturesJsonArray = new JSONArray()
    JSONObject currentFeatureJsonObject = featureService.convertFeatureToJSON(feature)
    newFeaturesJsonArray.add(currentFeatureJsonObject)

    featureEventService.addNewFeatureEvent(FeatureOperation.REMOVE_GO_ANNOTATION,
        feature.name,
        feature.uniqueName,
        dataObject,
        oldFeaturesJsonArray,
        newFeaturesJsonArray,
        user)

    JSONObject annotations = goAnnotationService.getAnnotations(feature)
    render annotations as JSON
  }
}
