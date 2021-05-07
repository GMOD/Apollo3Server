package org.bbop.apollo

import grails.gorm.transactions.Transactional
import io.swagger.v3.oas.annotations.Api
import org.bbop.apollo.attributes.FeatureType

import static org.springframework.http.HttpStatus.*

@Controller(value ="/featureType",tags = "Manage feature types for canned values and statuses")
@Transactional(readOnly = true)
class FeatureTypeController {

    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]


    def index(Integer max) {
        params.max = Math.min(max ?: 30, 200)
        respond FeatureType.list(params), model:[featureTypeInstanceCount: FeatureType.count()]
    }

    def show(FeatureType featureTypeInstance) {
        respond featureTypeInstance
    }

    def create() {
        respond new FeatureType(params)
    }

    @Transactional
    def save(FeatureType featureTypeInstance) {
        if (featureTypeInstance == null) {
            notFound()
            return
        }

        if (featureTypeInstance.hasErrors()) {
            respond featureTypeInstance.errors, view:'create'
            return
        }

        featureTypeInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.created.message', args: [message(code: 'featureType.label', default: 'FeatureType'), featureTypeInstance.id])
                redirect featureTypeInstance
            }
            '*' { respond featureTypeInstance, [status: CREATED] }
        }
    }

    def edit(FeatureType featureTypeInstance) {
        respond featureTypeInstance
    }

    @Transactional
    def update(FeatureType featureTypeInstance) {
        if (featureTypeInstance == null) {
            notFound()
            return
        }

        if (featureTypeInstance.hasErrors()) {
            respond featureTypeInstance.errors, view:'edit'
            return
        }

        featureTypeInstance.save flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.updated.message', args: [message(code: 'FeatureType.label', default: 'FeatureType'), featureTypeInstance.id])
                redirect featureTypeInstance
            }
            '*'{ respond featureTypeInstance, [status: OK] }
        }
    }

    @Transactional
    def delete(FeatureType featureTypeInstance) {

        if (featureTypeInstance == null) {
            notFound()
            return
        }

        featureTypeInstance.delete flush:true

        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.deleted.message', args: [message(code: 'FeatureType.label', default: 'FeatureType'), featureTypeInstance.id])
                redirect action:"index", method:"GET"
            }
            '*'{ render status: NO_CONTENT }
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'featureType.label', default: 'FeatureType'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }
}
