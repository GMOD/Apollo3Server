package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Transactional
import io.swagger.v3.oas.annotations.Api
import io.swagger.v3.oas.annotations.ApiImplicitParam
import io.swagger.v3.oas.annotations.ApiImplicitParams
import io.swagger.v3.oas.annotations.ApiOperation
import org.bbop.apollo.attributes.CannedComment
import org.bbop.apollo.attributes.CannedCommentOrganismFilter
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.organism.Organism
import org.grails.web.json.JSONObject

import static org.springframework.http.HttpStatus.*

@Controller(value = "/cannedComment", tags = "Canned Comments Services: Methods for managing default comments")
@Transactional(readOnly = true)
class CannedCommentController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def permissionService

//    def beforeInterceptor = {
//        if (!permissionService.checkPermissions(PermissionEnum.ADMINISTRATE)) {
//            forward action: "notAuthorized", controller: "annotator"
//            return
//        }
//    }

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        def cannedComments = CannedComment.list(params)
        def organismFilterMap = [:]
        CannedCommentOrganismFilter.findAllByCannedCommentInList(cannedComments).each() {
            List filterList = organismFilterMap.containsKey(it.cannedComment) ? organismFilterMap.get(it.cannedComment) : []
            filterList.add(it)
            organismFilterMap[it.cannedComment] = filterList
        }
        respond cannedComments, model: [cannedCommentInstanceCount: CannedComment.count(), organismFilters: organismFilterMap]
    }

    def show(CannedComment cannedCommentInstance) {
        respond cannedCommentInstance, model: [organismFilters: CannedCommentOrganismFilter.findAllByCannedComment(cannedCommentInstance)]
    }

    def create() {
        respond new CannedComment(params)
    }

    @Transactional
    def save(CannedComment cannedCommentInstance) {
        if (cannedCommentInstance == null) {
            notFound()
            return
        }

        if (cannedCommentInstance.hasErrors()) {
            respond cannedCommentInstance.errors, view: 'create'
            return
        }


        cannedCommentInstance.save()

        if (params.organisms instanceof String) {
            params.organisms = [params.organisms]
        }

        params?.organisms.each {
            Organism organism = Organism.findById(it)
            new CannedCommentOrganismFilter(
                    organism: organism,
                    cannedComment: cannedCommentInstance
            ).save()
        }

        cannedCommentInstance.save flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'cannedComment.label', default: 'CannedComment'), cannedCommentInstance.id])
                redirect cannedCommentInstance
            }
            '*' { respond cannedCommentInstance, [status: CREATED] }
        }
    }

    def edit(CannedComment cannedCommentInstance) {
        respond cannedCommentInstance, model: [organismFilters: CannedCommentOrganismFilter.findAllByCannedComment(cannedCommentInstance)]
    }

    @Transactional
    def update(CannedComment cannedCommentInstance) {
        if (cannedCommentInstance == null) {
            notFound()
            return
        }

        if (cannedCommentInstance.hasErrors()) {
            respond cannedCommentInstance.errors, view: 'edit'
            return
        }

        cannedCommentInstance.save()

        CannedCommentOrganismFilter.deleteAll(CannedCommentOrganismFilter.findAllByCannedComment(cannedCommentInstance))

        if (params.organisms instanceof String) {
            params.organisms = [params.organisms]
        }

        params?.organisms.each {
            Organism organism = Organism.findById(it)
            new CannedCommentOrganismFilter(
                    organism: organism,
                    cannedComment: cannedCommentInstance
            ).save()
        }

        cannedCommentInstance.save(flush: true)

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'CannedComment.label', default: 'CannedComment'), cannedCommentInstance.id])
                redirect cannedCommentInstance
            }
            '*' { respond cannedCommentInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(CannedComment cannedCommentInstance) {

        if (cannedCommentInstance == null) {
            notFound()
            return
        }

        cannedCommentInstance.delete flush: true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'CannedComment.label', default: 'CannedComment'), cannedCommentInstance.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'cannedComment.label', default: 'CannedComment'), params.id])
                redirect action: "index", method: "GET"
            }
            '*' { render status: NOT_FOUND }
        }
    }

    @Operation(value = "Create canned comment", nickname = "/createComment", httpMethod = "POST")
    @Parameters([
            @Parameter(name = "username", type = "email", paramType = "query")
            , @Parameter(name = "password", type = "password", paramType = "query")
            , @Parameter(name = "comment", type = "string", paramType = "query", example = "Canned comment to add")
            , @Parameter(name = "metadata", type = "string", paramType = "query", example = "Optional additional information")
    ]
    )
    @Transactional
    def createComment() {
        JSONObject commentJson = permissionService.handleInput(request, params)
        try {
            if (permissionService.isUserGlobalAdmin(permissionService.getCurrentUser(commentJson))) {
                if (!commentJson.comment) {
                    throw new Exception('empty fields detected')
                }

                if (!commentJson.metadata) {
                    commentJson.metadata = ""
                }

                log.debug "Adding canned comment ${commentJson.comment}"
                CannedComment comment = new CannedComment(
                        comment: commentJson.comment,
                        metadata: commentJson.metadata
                ).save(flush: true)

                render comment as JSON
            } else {
                def error = [error: 'not authorized to add CannedComment']
                render error as JSON
                log.error(error.error)
            }
        } catch (e) {
            def error = [error: 'problem saving CannedComment: ' + e]
            render error as JSON
            e.printStackTrace()
            log.error(error.error)
        }
    }

    @Operation(value = "Update canned comment", nickname = "/updateComment", httpMethod = "POST")
    @Parameters([
            @Parameter(name = "username", type = "email", paramType = "query")
            , @Parameter(name = "password", type = "password", paramType = "query")
            , @Parameter(name = "id", type = "long", paramType = "query", example = "Canned comment ID to update (or specify the old_comment)")
            , @Parameter(name = "old_comment", type = "string", paramType = "query", example = "Canned comment to update")
            , @Parameter(name = "new_comment", type = "string", paramType = "query", example = "Canned comment to change to (the only editable option)")
            , @Parameter(name = "metadata", type = "string", paramType = "query", example = "Optional additional information")
    ]
    )
    @Transactional
    def updateComment() {
        try {
            JSONObject commentJson = permissionService.handleInput(request, params)
            log.debug "Updating canned comment ${commentJson}"
            if (permissionService.isUserGlobalAdmin(permissionService.getCurrentUser(commentJson))) {

                log.debug "Canned comment ID: ${commentJson.id}"
                CannedComment comment = CannedComment.findById(commentJson.id) ?: CannedComment.findByComment(commentJson.old_comment)

                if (!comment) {
                    JSONObject jsonObject = new JSONObject()
                    jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to update the canned comment")
                    render jsonObject as JSON
                    return
                }

                comment.comment = commentJson.new_comment

                if (commentJson.metadata) {
                    comment.metadata = commentJson.metadata
                }

                comment.save(flush: true)

                log.info "Success updating canned comment: ${comment.id}"
                render new JSONObject() as JSON
            } else {
                def error = [error: 'not authorized to edit canned comment']
                log.error(error.error)
                render error as JSON
            }
        }
        catch (Exception e) {
            def error = [error: 'problem editing canned comment: ' + e]
            log.error(error.error)
            render error as JSON
        }
    }

    @Operation(value = "Remove a canned comment", nickname = "/deleteComment", httpMethod = "POST")
    @Parameters([
            @Parameter(name = "username", type = "email", paramType = "query")
            , @Parameter(name = "password", type = "password", paramType = "query")
            , @Parameter(name = "id", type = "long", paramType = "query", example = "Canned comment ID to remove (or specify the name)")
            , @Parameter(name = "comment", type = "string", paramType = "query", example = "Canned comment to delete")
    ])
    @Transactional
    def deleteComment() {
        try {
            JSONObject commentJson = permissionService.handleInput(request, params)
            log.debug "Deleting canned comment ${commentJson}"
            if (permissionService.isUserGlobalAdmin(permissionService.getCurrentUser(commentJson))) {

                CannedComment comment = CannedComment.findById(commentJson.id) ?: CannedComment.findByComment(commentJson.comment)

                if (!comment) {
                    JSONObject jsonObject = new JSONObject()
                    jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to delete the canned comment")
                    render jsonObject as JSON
                    return
                }

                comment.delete()

                log.info "Success deleting canned comment: ${commentJson}"
                render new JSONObject() as JSON
            } else {
                def error = [error: 'not authorized to delete canned comment']
                log.error(error.error)
                render error as JSON
            }
        }
        catch (Exception e) {
            def error = [error: 'problem deleting canned comment: ' + e]
            log.error(error.error)
            render error as JSON
        }
    }

    @Operation(value = "Returns a JSON array of all canned comments, or optionally, gets information about a specific canned comment", nickname = "/showComment", httpMethod = "POST")
    @Parameters([
            @Parameter(name = "username", type = "email", paramType = "query")
            , @Parameter(name = "password", type = "password", paramType = "query")
            , @Parameter(name = "id", type = "long", paramType = "query", example = "Comment ID to show (or specify a comment)")
            , @Parameter(name = "comment", type = "string", paramType = "query", example = "Comment to show")
    ])
    @Transactional
    def showComment() {
        try {
            JSONObject commentJson = permissionService.handleInput(request, params)
            log.debug "Showing canned comment ${commentJson}"
            if (!permissionService.hasGlobalPermissions(commentJson, GlobalPermissionEnum.ADMIN)) {
                render status: UNAUTHORIZED
                return
            }

            if (commentJson.id || commentJson.comment) {
                CannedComment comment = CannedComment.findById(commentJson.id) ?: CannedComment.findByComment(commentJson.comment)

                if (!comment) {
                    JSONObject jsonObject = new JSONObject()
                    jsonObject.put(FeatureStringEnum.ERROR.value, "Failed to delete the canned comments")
                    render jsonObject as JSON
                    return
                }

                log.info "Success showing comment: ${commentJson}"
                render comment as JSON
            } else {
                def comments = CannedComment.all

                log.info "Success showing all canned comments"
                render comments as JSON
            }
        }
        catch (Exception e) {
            def error = [error: 'problem showing canned comments: ' + e]
            log.error(error.error)
            render error as JSON
        }
    }
}
