package org.bbop.apollo.permission

import grails.converters.JSON
import org.bbop.apollo.organism.Organism
import org.grails.web.json.JSONArray

class UserOrganismPermission extends UserPermission{
    
    String permissions // JSONArray wrapping PermissionEnum

    List<String> getPermissionValues(){
        def returnList = []
        if(permissions){
            JSONArray jsonArray = JSON.parse(permissions) as JSONArray
            for(int i = 0  ; i < jsonArray.size() ; i++){
                returnList << jsonArray.getString(i)
            }
        }

        return returnList
    }
    
    static belongsTo = [Organism ]

    static constraints = {
    }

    static mapping = {
    }
    


}
