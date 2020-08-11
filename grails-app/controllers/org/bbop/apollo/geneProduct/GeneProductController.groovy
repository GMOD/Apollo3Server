package org.bbop.apollo.geneProduct

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import org.bbop.apollo.Feature
import org.bbop.apollo.User
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.history.FeatureOperation
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
//// import io.swagger.annotations.*

import static org.springframework.http.HttpStatus.NOT_FOUND

@Transactional(readOnly = true)
class GeneProductController {


    def permissionService
    def geneProductService
    def featureEventService
    def featureService

//    // @ApiOperation(value = "Returns a JSON array of all suggested gene product names", nickname = "/geneProduct/search", httpMethod =  "get")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "query", type = "string", paramType = "query", example = "Query value")
//    ])
    def search() {
        try {
            JSONObject nameJson = permissionService.handleInput(request, params)
            String query = nameJson.getString("query")
            JSONArray searchArray = new JSONArray()
            for(GeneProduct geneProduct in GeneProduct.findAllByProductNameIlike(query+"%")){
                searchArray.add(geneProduct.productName)
            }
            render searchArray as JSON
        } catch (Exception e) {
            def error = [error: 'problem finding gene product names for : '+ e]
            log.error(error.error)
            render error as JSON
        }
    }

//    // @ApiOperation(value = "Load gene product for feature", nickname = "/geneProduct", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "uniqueName", type = "Feature uniqueName", paramType = "query", example = "Gene name to query on")
//    ]
//    )
    def index() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        permissionService.checkPermissions(dataObject, PermissionEnum.READ)
        Feature feature = Feature.findByUniqueName(dataObject.uniqueName as String)
        if (feature) {
            JSONObject annotations = geneProductService.getAnnotations(feature)
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
//    // @ApiOperation(value = "Save New gene product for feature", nickname = "/geneProduct/save", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "feature", type = "string", paramType = "query", example = "uniqueName of gene feature to query on")
//            // , @ApiImplicitParam(name = "productName", type = "string", paramType = "query", example = "Name of gene product")
//            // , @ApiImplicitParam(name = "alternate", type = "boolean", paramType = "query", example = "Alternate (default false)")
//            // , @ApiImplicitParam(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
//            // , @ApiImplicitParam(name = "evidenceCodeLAbel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
//            // , @ApiImplicitParam(name = "negate", type = "boolean", paramType = "query", example = "Negate evidence (default false)")
//            // , @ApiImplicitParam(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312]]\"]}")
//            // , @ApiImplicitParam(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312]]\"]}")
//            // , @ApiImplicitParam(name = "notes", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312]]\"]}")
//    ]
//    )
    @Transactional
    def save() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
        User user = permissionService.getCurrentUser(dataObject)
        GeneProduct geneProduct = new GeneProduct()
        Feature feature = Feature.findByUniqueName(dataObject.feature)

        JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

        geneProduct.feature = feature
        geneProduct.productName = dataObject.productName
        geneProduct.evidenceRef = dataObject.evidenceCode
        geneProduct.evidenceRefLabel = dataObject.evidenceCodeLabel
        geneProduct.alternate = dataObject.alternate ?: false
        geneProduct.withOrFromArray = dataObject.withOrFrom
        geneProduct.notesArray = dataObject.notes
        geneProduct.reference = dataObject.reference
        geneProduct.lastUpdated = new Date()
        geneProduct.dateCreated = new Date()
        geneProduct.addToOwners(user)
        feature.addToGeneProducts(geneProduct)
        geneProduct.save(flush: true, failOnError: true)

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

        JSONObject annotations = geneProductService.getAnnotations(feature)
        render annotations as JSON
    }

//    // @ApiOperation(value = "Update existing gene products for feature", nickname = "/geneProduct/update", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to update (required)")
//            // , @ApiImplicitParam(name = "feature", type = "string", paramType = "query", example = "uniqueName of feature to query on")
//            // , @ApiImplicitParam(name = "productName", type = "string", paramType = "query", example = "gene product name")
//            // , @ApiImplicitParam(name = "alternate", type = "boolean", paramType = "query", example = "(default false) alternate")
//            // , @ApiImplicitParam(name = "evidenceCode", type = "string", paramType = "query", example = "Evidence (ECO) CURIE")
//            // , @ApiImplicitParam(name = "evidenceCodeLabel", type = "string", paramType = "query", example = "Evidence (ECO) Label")
//            // , @ApiImplicitParam(name = "negate", type = "boolean", paramType = "query", example = "Negate evidence (default false)")
//            // , @ApiImplicitParam(name = "withOrFrom", type = "string", paramType = "query", example = "JSON Array of with or from CURIE strings, e.g., {[\"UniProtKB:12312\"]}")
//            // , @ApiImplicitParam(name = "references", type = "string", paramType = "query", example = "JSON Array of reference CURIE strings, e.g., {[\"PMID:12312\"]}")
//            // , @ApiImplicitParam(name = "notes", type = "string", paramType = "query", example = "JSON Array of notes strings, e.g., {[\"This is a note\"]}")
//    ]
//    )
    @Transactional
    def update() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
        User user = permissionService.getCurrentUser(dataObject)
        Feature feature = Feature.findByUniqueName(dataObject.feature)

        JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)


        GeneProduct geneProduct = GeneProduct.findById(dataObject.id)
        geneProduct.feature = feature
        geneProduct.productName = dataObject.productName
        geneProduct.evidenceRef = dataObject.evidenceCode
        geneProduct.evidenceRefLabel = dataObject.evidenceCodeLabel
        geneProduct.alternate = dataObject.alternate ?: false
        geneProduct.withOrFromArray = dataObject.withOrFrom
        geneProduct.notesArray = dataObject.notes
        geneProduct.reference = dataObject.reference
        geneProduct.lastUpdated = new Date()
        geneProduct.dateCreated = new Date()
        geneProduct.addToOwners(user)
        geneProduct.save(flush: true, failOnError: true, insert: false)

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

        JSONObject annotations = geneProductService.getAnnotations(feature)
        render annotations as JSON
    }

//    // @ApiOperation(value = "Delete existing gene product for feature", nickname = "/geneProduct/delete", httpMethod = "POST")
//    // @ApiImplicitParams([
//            // @ApiImplicitParam(name = "username", type = "email", paramType = "query")
//            // , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
//            // , @ApiImplicitParam(name = "id", type = "string", paramType = "query", example = "GO Annotation ID to delete (required)")
//            // , @ApiImplicitParam(name = "uniqueName", type = "string", paramType = "query", example = "Feature uniqueName to remove feature from")
//    ]
//    )
    @Transactional
    def delete() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        permissionService.checkPermissions(dataObject, PermissionEnum.WRITE)
        User user = permissionService.getCurrentUser(dataObject)

        Feature feature = Feature.findByUniqueName(dataObject.feature)
        JSONObject originalFeatureJsonObject = featureService.convertFeatureToJSON(feature)

        GeneProduct geneProduct = GeneProduct.findById(dataObject.id)
        feature.removeFromGeneProducts(geneProduct)
        geneProduct.delete(flush: true)

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

        JSONObject annotations = geneProductService.getAnnotations(feature)
        render annotations as JSON
    }
}
