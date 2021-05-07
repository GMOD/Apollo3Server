package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.Parameters
import org.bbop.apollo.feature.Feature
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.history.FeatureOperation
import org.bbop.apollo.provenance.Provenance
import org.bbop.apollo.user.User
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import static org.springframework.http.HttpStatus.NOT_FOUND

@Transactional(readOnly = true)
class ProvenanceController {


  def permissionService
  def provenanceService
  def featureEventService
  def featureService

  @Operation(value = "Load Annotations for feature", nickname = "/provenance", httpMethod = "POST")
  @Parameters([
    @Parameter(name = "username", type = "email", paramType = "query")
    , @Parameter(name = "password", type = "password", paramType = "query")
    , @Parameter(name = "uniqueName", type = "Feature uniqueName", paramType = "query", example = "Feature name to query on")
  ]
  )
  def index() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.READ)
      Feature feature = Feature.findByUniqueName(dataObject.uniqueName as String)
    if (feature) {
      JSONObject annotations = provenanceService.getAnnotations(feature)
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
  @Operation(value = "Save New Go Annotations for feature", nickname = "/provenance/save", httpMethod = "POST")
  @Parameters([
    @Parameter(name = "username", type = "email", paramType = "query")
    , @Parameter(name = "password", type = "password", paramType = "query")
          , @Parameter(name = "feature", type = "string", paramType = "query", example = "Feature uniqueName to query on")
    , @Parameter(name = "field", type = "string", paramType = "query", example = "Field type to annotate ")
    , @Parameter(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
    , @Parameter(name = "evidenceCodeLabel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
    , @Parameter(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312]]\"]}")
      , @Parameter(name = "notes", type = "string", paramType = "query", example = "JSON Array of notes  {[\"A simple note\"]}")
          , @Parameter(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312\"]}")
  ]
  )
  @Transactional
  def save() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)
      Provenance provenance = new Provenance()
    Feature feature = Feature.findByUniqueName(dataObject.feature)

    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

    provenance.feature = feature
    provenance.field = dataObject.field
    provenance.evidenceRef = dataObject.evidenceCode
    provenance.evidenceRefLabel = dataObject.evidenceCodeLabel
    provenance.withOrFromArray = dataObject.withOrFrom
    provenance.notesArray = dataObject.notes
    provenance.reference = dataObject.reference
    provenance.lastUpdated = new Date()
    provenance.dateCreated = new Date()
    provenance.addToOwners(user)
    feature.addToProvenances(provenance)
    provenance.save(flush: true, failOnError: true)

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

    JSONObject annotations = provenanceService.getAnnotations(feature)
    render annotations as JSON
  }

  @Operation(value = "Update existing annotations for feature", nickname = "/provenance/update", httpMethod = "POST")
  @Parameters([
    @Parameter(name = "username", type = "email", paramType = "query")
    , @Parameter(name = "password", type = "password", paramType = "query")
    , @Parameter(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to update (required)")
    , @Parameter(name = "feature", type = "string", paramType = "query", example = "uniqueName of feature to query on")
    , @Parameter(name = "field", type = "string", paramType = "query", example = "field type annotated")
    , @Parameter(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
    , @Parameter(name = "evidenceCodeLabel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
    , @Parameter(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312]]\"]}")
    , @Parameter(name = "notes", type = "string", paramType = "query", example = "JSON Array of notes  {[\"A simple note\"]}")
    , @Parameter(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312\"]}")
  ]
  )
  @Transactional
  def update() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)
    Feature feature = Feature.findByUniqueName(dataObject.feature)

    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)


    Provenance provenance = Provenance.findById(dataObject.id)
    provenance.feature = feature
    provenance.field = dataObject.field
    provenance.evidenceRef = dataObject.evidenceCode
    provenance.evidenceRefLabel = dataObject.evidenceCodeLabel
    provenance.withOrFromArray = dataObject.withOrFrom
    provenance.notesArray = dataObject.notes
    provenance.reference = dataObject.reference
    provenance.lastUpdated = new Date()
    provenance.dateCreated = new Date()
    provenance.addToOwners(user)
    provenance.save(flush: true, failOnError: true, insert: false)

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

    JSONObject annotations = provenanceService.getAnnotations(feature)
    render annotations as JSON
  }

  @Operation(value = "Delete existing annotations for feature", nickname = "/provenance/delete", httpMethod = "POST")
  @Parameters([
    @Parameter(name = "username", type = "email", paramType = "query")
    , @Parameter(name = "password", type = "password", paramType = "query")
    , @Parameter(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to delete (required)")
    , @Parameter(name = "uniqueName", type = "string", paramType = "query", example = "Feature uniqueName to remove feature from")
  ]
  )
  @Transactional
  def delete() {
    JSONObject dataObject = permissionService.handleInput(request, params)
    permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
    User user = permissionService.getCurrentUser(dataObject)

    Feature feature = Feature.findByUniqueName(dataObject.feature)
    JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

    Provenance provenance = Provenance.findById(dataObject.id)
    feature.removeFromProvenances(provenance)
    provenance.delete(flush: true)

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

    JSONObject annotations = provenanceService.getAnnotations(feature)
    render annotations as JSON
  }
}
