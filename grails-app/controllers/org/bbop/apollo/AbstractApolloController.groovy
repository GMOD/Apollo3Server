package org.bbop.apollo

import grails.converters.JSON
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONException
import org.grails.web.json.JSONObject

abstract class AbstractApolloController {

    public static String REST_OPERATION = "operation"
    public static String REST_FEATURES = "features"
    public static REST_USERNAME = "username"
    public static REST_PERMISSION = "permission"
    public static REST_TRANSLATION_TABLE = "translation_table"
    public static REST_START_PROTEINS = "start_proteins"
    public static REST_STOP_PROTEINS = "stop_proteins"

    protected String underscoreToCamelCase(String underscore) {
        if (!underscore || underscore.isAllWhitespace()) {
            return ''
        }
        return underscore.replaceAll(/_\w/) { it[1].toUpperCase() }
    }


    protected def findPost() {
        for (p in params) {
            String key = p.key
            if (key.contains("operation")) {
                return (JSONObject) JSON.parse(key)
            }
        }
    }
}
