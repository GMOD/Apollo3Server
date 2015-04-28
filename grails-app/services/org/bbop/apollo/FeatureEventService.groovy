package org.bbop.apollo

import grails.converters.JSON
import grails.transaction.Transactional
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.history.FeatureOperation
import grails.util.Environment
import org.codehaus.groovy.grails.web.json.JSONArray
import org.codehaus.groovy.grails.web.json.JSONObject

@Transactional
class FeatureEventService {

    def permissionService
    def featureService

    FeatureEvent addNewFeatureEvent(FeatureOperation featureOperation, Feature feature,User user) {
        if (Environment.current == Environment.TEST) {
            return addNewFeatureEventWithUser(featureOperation, feature, null)
        }
        addNewFeatureEventWithUser(featureOperation, feature,user)
    }


    FeatureEvent addNewFeatureEvent(FeatureOperation featureOperation, String uniqueName, JSONObject jsonObject,User user) {
        if (Environment.current == Environment.TEST) {
            return addNewFeatureEventWithUser(featureOperation, uniqueName, jsonObject, (User) null)
        }
        addNewFeatureEventWithUser(featureOperation, uniqueName, jsonObject, user)
    }

    FeatureEvent addNewFeatureEventWithUser(FeatureOperation featureOperation, String uniqueName, JSONObject jsonObject, User user) {

        FeatureEvent featureEvent
        JSONArray newFeatureArray = new JSONArray()
        newFeatureArray.add(jsonObject)
        featureEvent = new FeatureEvent(
                editor: user
                , uniqueName: uniqueName
                , operation: featureOperation.name()
                , current: true
                , newFeaturesJsonArray: newFeatureArray.toString()
                , oldFeaturesJsonArray: new JSONArray().toString()
                , dateCreated: new Date()
                , lastUpdated: new Date()
        ).save()

        return featureEvent

    }

    FeatureEvent addNewFeatureEventWithUser(FeatureOperation featureOperation, Feature feature, User user) {
        return addNewFeatureEventWithUser(featureOperation, feature.uniqueName, featureService.convertFeatureToJSON(feature), user)
    }


//    FeatureEvent addNewFeatureEvent(FeatureOperation featureOperation, String uniqueName, JSONObject oldJsonObject,JSONObject newJsonObject) {
//        return addNewFeatureEventWithUser(featureOperation,uniqueName,oldJsonObject,newJsonObject,User user)
//    }

    def addNewFeatureEvent(FeatureOperation featureOperation, String uniqueName, JSONObject oldJsonObject, JSONObject newJsonObject, User user ) {
        JSONArray newFeatureArray = new JSONArray()
        newFeatureArray.add(newJsonObject)
        JSONArray oldFeatureArray = new JSONArray()
        oldFeatureArray.add(oldJsonObject)
        FeatureEvent featureEvent = new FeatureEvent(
                editor: user
                , uniqueName: uniqueName
                , operation: featureOperation.name()
                , current: true
                , newFeaturesJsonArray: newFeatureArray.toString()
                , oldFeaturesJsonArray: oldFeatureArray.toString()
                , dateCreated: new Date()
                , lastUpdated: new Date()
        ).save()

        return featureEvent
    }

    def deleteHistory(String uniqueName) {
        FeatureEvent.deleteAll(FeatureEvent.findAllByUniqueName(uniqueName))
    }
}