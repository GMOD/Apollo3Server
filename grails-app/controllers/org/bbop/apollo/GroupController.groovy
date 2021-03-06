package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.gwt.shared.PermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.permission.GroupOrganismPermission
import org.bbop.apollo.user.User
import org.bbop.apollo.user.UserGroup
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import org.springframework.http.HttpStatus

@Api(value = "/group",tags = "Group Services: Methods for managing groups")
class GroupController {

    def permissionService
    def groupService

    @ApiOperation(value = "Get organism permissions for group", nickname = "/getOrganismPermissionsForGroup", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "id", type = "long", paramType = "query", example = "Group ID (or specify the name)")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name")
    ]
    )
    def getOrganismPermissionsForGroup() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        UserGroup group = UserGroup.findById(dataObject.groupId)
        if (!group) {
            group = UserGroup.findByName(dataObject.name)
        }
        if (!group) {
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to get organism permissions")
            render jsonObject as JSON
            return
        }

//        String query = "MATCH (p:GroupOrganismPermission)--(g:UserGroup) where g.name = '${group.name}' return p"
        String query = "MATCH (p:GroupOrganismPermission)--(g:UserGroup) where g.name = '${group.name}' return { permission: p }"
//        List<GroupOrganismPermission> groupOrganismPermissions = GroupOrganismPermission.executeQuery(query)
        def groupOrganismPermissions = GroupOrganismPermission.executeQuery(query)
        if (groupOrganismPermissions) {
            JSONObject permissionsJSONObject = new JSONObject()
            def permissionObject = groupOrganismPermissions.first().permission
            log.debug "keys ${permissionObject.keys()}"
            permissionsJSONObject.permissions = permissionObject.get(FeatureStringEnum.PERMISSIONS.value).asString()

            log.debug "keys object ${permissionsJSONObject as JSON}"
            JSONArray jsonArray = new JSONArray()
            jsonArray.add(permissionsJSONObject)
            render jsonArray as JSON
        }
        else{
//        List<GroupOrganismPermission> groupOrganismPermissions = GroupOrganismPermission.findAllByGroup(group)
            log.warn "found NO permissions ${groupOrganismPermissions} ??"
            render groupOrganismPermissions as JSON
        }
    }

    @ApiOperation(value = "Load all groups", nickname = "/loadGroups", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "groupId", type = "long", paramType = "query", example = "Optional only load a specific groupId")
    ])
    def loadGroups() {
        try {
            log.debug "loadGroups"
            JSONObject dataObject = permissionService.handleInput(request, params)
            // allow instructor to view groups
            if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.INSTRUCTOR)) {
                render status: HttpStatus.UNAUTHORIZED
                return
            }
            // to support webservice, get current user from session or input object
            def currentUser = permissionService.getCurrentUser(dataObject)
            JSONArray returnArray = new JSONArray()
            def allowableOrganisms = permissionService.getOrganisms((User) currentUser)

            Map<String, List<GroupOrganismPermission>> groupOrganismPermissionMap = new HashMap<>()

//            List<GroupOrganismPermission> groupOrganismPermissionList = GroupOrganismPermission.findAllByOrganismInList(allowableOrganisms as List)
            // TODO: fix this
            List<GroupOrganismPermission> groupOrganismPermissionList = []
            for (GroupOrganismPermission groupOrganismPermission in groupOrganismPermissionList) {
                List<GroupOrganismPermission> groupOrganismPermissionListTemp = groupOrganismPermissionMap.get(groupOrganismPermission.group.name)
                if (groupOrganismPermissionListTemp == null) {
                    groupOrganismPermissionListTemp = new ArrayList<>()
                }
                groupOrganismPermissionListTemp.add(groupOrganismPermission)
                groupOrganismPermissionMap.put(groupOrganismPermission.group.name, groupOrganismPermissionListTemp)
            }

            // restricted groups
            def groups = dataObject.groupId ? [UserGroup.findById(dataObject.groupId)] : UserGroup.all
            def filteredGroups = groups

            // if user is admin, then include all
            // if group has metadata with the creator or no metadata then include
            // instead of using !permissionService.isAdmin() because it only works for login user but doesn't work for webservice
            if (!permissionService.isUserGlobalAdmin(currentUser)) {
                log.debug "filtering groups"

                filteredGroups = groups.findAll() {
                    // permissionService.currentUser is None when accessing by webservice
                    it.metadata == null || it.getMetaData(FeatureStringEnum.CREATOR.value) == (currentUser.id as String) || permissionService.isGroupAdmin(it, currentUser)
                }
            }

            filteredGroups.each {
                def groupObject = new JSONObject()
                groupObject.id = it.id
                groupObject.name = it.name
                groupObject.public = it.isPublicGroup()
                groupObject.numberOfUsers = it.users?.size()

                JSONArray userArray = new JSONArray()
                it.users.each { user ->
                    JSONObject userObject = new JSONObject()
                    userObject.id = user.id
                    userObject.email = user.username
                    userObject.firstName = user.firstName
                    userObject.lastName = user.lastName
                    userArray.add(userObject)
                }
                groupObject.users = userArray

                JSONArray adminArray = new JSONArray()

                String otherQuery = "MATCH (g:UserGroup)-[admin:ADMIN]-(u:User) where g.name = '${it.name}' return { admin: u } limit 1"
                def admins = User.executeQuery(otherQuery)
                if (admins) {
                    def admin = admins.first().admin
                    JSONObject userObject = new JSONObject()
//                    userObject.id = user.id
                    userObject.email = admin.get(FeatureStringEnum.USERNAME.value).asString()
                    userObject.username = admin.get(FeatureStringEnum.USERNAME.value).asString()
                    userObject.firstName = admin.get("firstName")
                    userObject.lastName = admin.get("lastName")
                    adminArray.add(userObject)
                    groupObject.admin = adminArray
                }
//                it.admin.each { user ->
//                    JSONObject userObject = new JSONObject()
//                    userObject.id = user.id
//                    userObject.email = user.username
//                    userObject.firstName = user.firstName
//                    userObject.lastName = user.lastName
//                    adminArray.add(userObject)
//                }

                // add organism permissions
                JSONArray organismPermissionsArray = new JSONArray()
                def groupOrganismPermissionList3 = groupOrganismPermissionMap.get(it.name)
                List<Long> organismsWithPermissions = new ArrayList<>()
                for (GroupOrganismPermission groupOrganismPermission in groupOrganismPermissionList3) {
                    if (allowableOrganisms.contains(groupOrganismPermission.organism)) {
                        JSONObject organismJSON = new JSONObject()
                        organismJSON.organism = groupOrganismPermission.organism.commonName
                        organismJSON.permissions = groupOrganismPermission.permissions
                        organismJSON.permissionArray = groupOrganismPermission.permissionValues
                        organismJSON.groupId = groupOrganismPermission.groupId
                        organismJSON.id = groupOrganismPermission.id
                        organismPermissionsArray.add(organismJSON)
                        organismsWithPermissions.add(groupOrganismPermission.organism.id)
                    }
                }

                Set<Organism> organismList = allowableOrganisms.findAll() {
                    !organismsWithPermissions.contains(it.id)
                }

                for (Organism organism in organismList) {
                    JSONObject organismJSON = new JSONObject()
                    organismJSON.organism = organism.commonName
                    organismJSON.permissions = "[]"
                    organismJSON.permissionArray = new JSONArray()
                    organismJSON.groupId = it.id
                    organismPermissionsArray.add(organismJSON)
                }


                groupObject.organismPermissions = organismPermissionsArray
                returnArray.put(groupObject)
            }
            render returnArray as JSON
        }
        catch (Exception e) {
            response.status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            def error = [error: e.message]
//            log.error error
            log.error "Error: ${error as JSON}"
            render error as JSON
        }
    }


    @ApiOperation(value = "Create group", nickname = "/createGroup", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name to add, or a comma-delimited list of names")
    ]
    )
    @Transactional
    def createGroup() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        // allow instructor to create Group
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.INSTRUCTOR)) {
            render status: HttpStatus.UNAUTHORIZED
            return
        }
        log.info "Creating group"
        // permissionService.currentUser is None when accessing by webservice
        // to support webservice, get current user from session or input object
        def currentUser = permissionService.getCurrentUser(dataObject)
        String[] names = dataObject.name.split(",")
        log.info("adding groups ${names as JSON}")

        List<UserGroup> groups = groupService.createGroups(dataObject?.metadata?.toString(), currentUser, names)

        String groupNames = groups.name.collect { "'${it}'" }
        String otherQuery = "MATCH (g:UserGroup)-[admin:ADMIN]-(u:User) where g.name in ${groupNames} return { group: g, admin: u } "
        def userGroups = UserGroup.executeQuery(otherQuery)

        JSONArray returnArray = new JSONArray()
        for (def userGroup in userGroups) {
            JSONObject jsonObject = new JSONObject()
            returnArray.add(jsonObject)
            def group = userGroup.group
            def admin = userGroup.admin
            jsonObject.name = group.get(FeatureStringEnum.NAME.value).asString()
            JSONObject adminObject = new JSONObject()
            adminObject.firstName = admin.get("firstName")?.asString()
            adminObject.lastName = admin.get("lastName")?.asString()
            adminObject.username = admin.get("username").asString()
            adminObject.email = admin.get("username").asString()
            jsonObject.admin = adminObject
        }

        if (returnArray.size() == 1) {
            render returnArray[0] as JSON
        } else {
            // TODO
            render returnArray as JSON
//            render groups as JSON
        }
    }

    @ApiOperation(value = "Delete a group", nickname = "/deleteGroup", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "id", type = "long", paramType = "query", example = "Group ID to remove (or specify the name)")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name or comma-delimited list of names to remove")
    ]
    )
    @Transactional
    def deleteGroup() {
        JSONObject dataObject = permissionService.handleInput(request, params)

        def currentUser = permissionService.getCurrentUser(dataObject)

        List<UserGroup> groupList
        if (dataObject.id) {
            List<Long> ids
            if (dataObject.id instanceof Integer) {
                ids = [dataObject.id as Integer]
            }
            if (dataObject.id instanceof String) {
                ids = dataObject.id.split(',').collect() as Long
            }
            groupList = UserGroup.findAllByIdInList(ids)
        } else if (dataObject.name) {
            List<String> splitGroups = dataObject.name.split(",") as List<String>
            groupList = UserGroup.findAllByNameInList(splitGroups)
        }
        if (!groupList) {
            def error = [error: "Group ${dataObject.name} not found"]
            log.error(error.error)
            render error as JSON
            return
        }

        groupService.deleteGroups(dataObject, currentUser, groupList)

        render new JSONObject() as JSON
    }

    @ApiOperation(value = "Update group", nickname = "/updateGroup", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "id", type = "long", paramType = "query", example = "Group ID to update")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name to change to (the only editable optoin)")
    ]
    )
    @Transactional
    def updateGroup() {
        log.info "Updating group"
        JSONObject dataObject = permissionService.handleInput(request, params)
        UserGroup group = UserGroup.findById(dataObject.id)
        if (!group) {
            group = UserGroup.findByName(dataObject.name)
        }
        if (!group) {
            JSONObject jsonObject = new JSONObject()
            jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to delete the group")
            render jsonObject as JSON
            return
        }
        // to support webservice, get current user from session or input object
        def currentUser = permissionService.getCurrentUser(dataObject)
        String creatorMetaData = group.getMetaData(FeatureStringEnum.CREATOR.value)
        // allow global admin, group creator, and group admin to update the group
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData) && !permissionService.isGroupAdmin(group, currentUser)) {
            render status: HttpStatus.UNAUTHORIZED.value()
            return
        }

        // the only thing that can really change
        log.info "Updated group ${group.name} to use name ${dataObject.name}"
        group.name = dataObject.name
        // also allow update metadata
        group.metadata = dataObject.metadata ? dataObject.metadata.toString() : group.metadata
        group.save(flush: true)
    }

    /**
     * Only changing one of the boolean permissions
     * @return
     */
    @ApiOperation(value = "Update organism permission", nickname = "/updateOrganismPermission", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "groupId", type = "long", paramType = "query", example = "Group ID to modify permissions for (must provide this or 'name')")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name to modify permissions for (must provide this or 'groupId')")
        , @ApiImplicitParam(name = "organism", type = "string", paramType = "query", example = "Organism common name")

        , @ApiImplicitParam(name = "ADMINISTRATE", type = "boolean", paramType = "query", example = "Indicate if user has administrative and all lesser (including user/group) privileges for the organism")
        , @ApiImplicitParam(name = "WRITE", type = "boolean", paramType = "query", example = "Indicate if user has write and all lesser privileges for the organism")
        , @ApiImplicitParam(name = "EXPORT", type = "boolean", paramType = "query", example = "Indicate if user has export and all lesser privileges for the organism")
        , @ApiImplicitParam(name = "READ", type = "boolean", paramType = "query", example = "Indicate if user has read and all lesser privileges for the organism")
    ]
    )
    @Transactional
    def updateOrganismPermission() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        if (!permissionService.hasPermissions(dataObject, PermissionEnum.ADMINISTRATE)) {
            render status: HttpStatus.UNAUTHORIZED.value()
            return
        }
        log.info "Trying to update group organism permissions"
        GroupOrganismPermission groupOrganismPermission = GroupOrganismPermission.findById(dataObject.id)


        UserGroup group
        if (dataObject.groupId) {
            group = UserGroup.findById(dataObject.groupId as Long)
        }
        if (!group) {
            group = UserGroup.findByName(dataObject.name)
        }
        if (!group) {
            render([(FeatureStringEnum.ERROR.value): "Failed to find group for ${dataObject.name} and ${dataObject.groupId}"] as JSON)
            return
        }

        log.debug "Finding organism by ${dataObject.organism}"
        Organism organism = permissionService.getOrganismForToken(dataObject.organism)
        if (!organism) {
            render([(FeatureStringEnum.ERROR.value): "Failed to find organism for ${dataObject.organism}"] as JSON)
            return
        }


        log.debug "found ${groupOrganismPermission}"
        if (!groupOrganismPermission) {
            groupOrganismPermission = GroupOrganismPermission.findByGroupAndOrganism(group, organism)
        }

        JSONArray permissionsArray = new JSONArray()
        if (dataObject.getBoolean(PermissionEnum.ADMINISTRATE.name())) {
            permissionsArray.add(PermissionEnum.ADMINISTRATE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.WRITE.name())) {
            permissionsArray.add(PermissionEnum.WRITE.name())
        }
        if (dataObject.getBoolean(PermissionEnum.EXPORT.name())) {
            permissionsArray.add(PermissionEnum.EXPORT.name())
        }
        if (dataObject.getBoolean(PermissionEnum.READ.name())) {
            permissionsArray.add(PermissionEnum.READ.name())
        }

        if (!groupOrganismPermission && permissionsArray) {
            groupOrganismPermission = new GroupOrganismPermission(
                group: group
                , organism: organism
                , permissions: permissionsArray.toString()
            ).save(insert: true, flush: true)
            log.debug "created new permissions! ${groupOrganismPermission} "
        } else
        if (permissionsArray.size() == 0) {
            groupOrganismPermission.delete(flush: true)
            render groupOrganismPermission as JSON
            return
        } else {
            groupOrganismPermission.permissions = permissionsArray
        }

        log.info "Updated permissions for group ${group.name} and organism ${organism?.commonName} and permissions ${permissionsArray?.toString()}"

        log.info "group organism permissions: ${groupOrganismPermission.permissions} rendering ${groupOrganismPermission as JSON}"

        String query = "MATCH (p:GroupOrganismPermission)--(g:UserGroup) where g.name = '${group.name}' create (p)<-[ug:GROUP]-(g) return { permission: p }"
//        String query = "MATCH (p:GroupOrganismPermission)--(g:UserGroup)  return p"
//        String query = "MATCH (p:GroupOrganismPermission) return { permission: p }"
        def groupOrganismPermissions = GroupOrganismPermission.executeQuery(query)
        if (groupOrganismPermissions) {
//        List<GroupOrganismPermission> groupOrganismPermissions = GroupOrganismPermission.findAllByGroup(group)
//            println "post-save found permissions ${groupOrganismPermissions} ... ${groupOrganismPermissions as JSON}"
//        JSONArray permissionsArray =
            JSONObject permissionsJSONObject = new JSONObject()
            def permissionObject = groupOrganismPermissions.first().permission
//            println "keys ${permissionObject.keys()}"
            permissionsJSONObject.permissions = permissionObject.get(FeatureStringEnum.PERMISSIONS.value).asString()

//            println "keys ${permissionsJSONObject as JSON}"

//            permissionObject.permissions = groupOrganismPermissions
            render permissionsJSONObject as JSON
        } else {
//
            render groupOrganismPermission as JSON
        }


    }

    @ApiOperation(value = "Update group membership", nickname = "/updateMembership", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "groupId", type = "long", paramType = "query", example = "Group ID to alter membership of")
        , @ApiImplicitParam(name = "users", type = "JSONArray", paramType = "query", example = "A JSON array of strings of emails of users the now belong to the group")
        , @ApiImplicitParam(name = "memberships", type = "JSONArray", paramType = "query", example = "Bulk memberships (instead of users and groupId) to update of the form: [ {groupId: <groupId>,users: [\"user1\", \"user2\", \"user3\"]}, {groupId:<another-groupId>, users: [\"user2\", \"user8\"]}]")
    ]
    )
    @Transactional
    def updateMembership() {
        JSONObject dataObject = permissionService.handleInput(request, params)

        def currentUser = permissionService.getCurrentUser(dataObject)

        if (dataObject.memberships) {

            def memberships = dataObject.memberships

            memberships.each { membership ->
                groupService.updateMembership(dataObject, currentUser, membership.groupId, membership.users)
            }
        } else {
            groupService.updateMembership(dataObject, currentUser, dataObject.groupId, dataObject.users)
        }
        loadGroups()
    }

    @ApiOperation(value = "Update group admin", nickname = "/updateGroupAdmin", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "groupId", type = "long", paramType = "query", example = "Group ID to alter membership of")
        , @ApiImplicitParam(name = "users", type = "JSONArray", paramType = "query", example = "A JSON array of strings of emails of users the now belong to the group")
    ]
    )
    @Transactional
    def updateGroupAdmin() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        UserGroup groupInstance = UserGroup.findById(dataObject.groupId)
        // to support webservice, get current user from session or input object
        def currentUser = permissionService.getCurrentUser(dataObject)
        String creatorMetaData = groupInstance.getMetaData(FeatureStringEnum.CREATOR.value)
        // allow global admin, group creator, and group admin to update the group membership
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN) && !(creatorMetaData && currentUser.id.toString() == creatorMetaData) && !permissionService.isGroupAdmin(groupInstance, currentUser)) {

            render status: HttpStatus.UNAUTHORIZED.value()
            return
        }
        log.info "Trying to update group admin"

        List<User> oldUsers = groupInstance.admin as List
        //Fixed bug on passing array through web services: cannot cast String to List
        JSONArray arr = new JSONArray(dataObject.users)
        List<String> usernames = new ArrayList<String>()
        for (int i = 0; i < arr.length(); i++) {
            usernames.add(arr.getString(i))
        }
        List<User> newUsers = User.findAllByUsernameInList(usernames)
        List<User> usersToAdd = newUsers - oldUsers
        List<User> usersToRemove = oldUsers - newUsers
        usersToAdd.each {
            String query = "MATCH (g:UserGroup ), (u:User) where g.name= '${groupInstance.name}' and (u.id = ${it.id} OR u.username = '${it.username}') create (g)-[admin:ADMIN]->(u)"
            def updates = User.executeUpdate(query)
            log.debug "updates ${updates}"
//            groupInstance.addToAdmin(it)
//            it.addToGroupAdmins(groupInstance)
            it.save()
        }
        usersToRemove.each {
            groupInstance.removeFromAdmin(it)
            it.removeFromGroupAdmins(groupInstance)
            it.save()
        }

        groupInstance.save(flush: true)
        log.info "Updated group ${groupInstance.name} admin ${newUsers.join(' ')}"
        loadGroups()
    }

    @ApiOperation(value = "Get group admins, returns group admins as JSONArray", nickname = "/getGroupAdmin", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name")
    ])
    def getGroupAdmin() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        log.debug "data: ${dataObject}"
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN)) {
            def error = [error: 'not authorized to view the metadata']
            log.error(error.error)
            render error as JSON
            return
        }
        UserGroup groupInstance = UserGroup.findByName(dataObject.name)
        if (!groupInstance) {
            def error = [error: 'The group does not exist']
            log.error(error.error)
            render error as JSON
            return
        }
        JSONArray returnArray = new JSONArray()
//        def adminList = groupInstance.admin
//        println "admin = ${adminList}"
//        adminList.each {
//            JSONObject user = new JSONObject()
//            user.id = it.id
//            user.firstName = it.firstName
//            user.lastName = it.lastName
//            user.username = it.username
//            returnArray.put(user)
//        }


        String otherQuery = "MATCH (g:UserGroup)-[admin:ADMIN]-(u:User) where g.name = '${dataObject.name}' return { admin: u } limit 1"
        def admins = User.executeQuery(otherQuery)
        log.debug "admins ${admins}"
        if (admins) {
            def admin = admins.first().admin
            JSONObject userObject = new JSONObject()
            userObject.email = admin.get(FeatureStringEnum.USERNAME.value).asString()
            userObject.username = admin.get(FeatureStringEnum.USERNAME.value).asString()
            userObject.firstName = admin.get("firstName")
            userObject.lastName = admin.get("lastName")
            returnArray.add(userObject)
        }


        render returnArray as JSON

    }

    @ApiOperation(value = "Get creator metadata for group, returns userId as JSONObject", nickname = "/getGroupCreator", httpMethod = "POST")
    @ApiImplicitParams([
        @ApiImplicitParam(name = "username", type = "email", paramType = "query")
        , @ApiImplicitParam(name = "password", type = "password", paramType = "query")
        , @ApiImplicitParam(name = "name", type = "string", paramType = "query", example = "Group name")
    ])
    def getGroupCreator() {
        JSONObject dataObject = permissionService.handleInput(request, params)
        log.debug "data: ${dataObject}"
        if (!permissionService.hasGlobalPermissions(dataObject, GlobalPermissionEnum.ADMIN)) {
            def error = [error: 'not authorized to view the metadata']
            log.error(error.error)
            render error as JSON
            return
        }
        UserGroup groupInstance = UserGroup.findByName(dataObject.name)
        if (!groupInstance) {
            def error = [error: 'The group does not exist']
            log.error(error.error)
            render error as JSON
            return
        }
        JSONObject metaData = new JSONObject()
        metaData.creator = groupInstance.getMetaData(FeatureStringEnum.CREATOR.value)
        render metaData as JSON

    }


}
