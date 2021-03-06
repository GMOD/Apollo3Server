package org.bbop.apollo


import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import org.bbop.apollo.attributes.CannedValue
import org.bbop.apollo.attributes.CannedValueOrganismFilter
import spock.lang.*

//@TestFor(CannedValueController)
//@Mock([CannedValue,CannedValueOrganismFilter])
class CannedValueControllerSpec extends Specification implements ControllerUnitTest<CannedValueController>, DataTest{

    def setup(){
        mockDomain CannedValue
        mockDomain CannedValueOrganismFilter
    }


    def populateValidParams(params) {
        assert params != null
        // TODO: Populate valid properties like...
        params["label"] = 'someValidName'
    }

    void "Test the index action returns the correct model"() {

        when:"The index action is executed"
            controller.index()

        then:"The model is correct"
            !model.cannedValueList
            model.cannedValueInstanceCount == 0
    }

    void "Test the create action returns the correct model"() {
        when:"The create action is executed"
            controller.create()

        then:"The model is correctly created"
            model.cannedValue!= null
    }

    void "Test the save action correctly persists an instance"() {

        when:"The save action is executed with an invalid instance"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'POST'
            def cannedValue = new CannedValue()
            cannedValue.validate()
            controller.save(cannedValue)

        then:"The create view is rendered again with the correct model"
            model.cannedValue!= null
            view == 'create'

        when:"The save action is executed with a valid instance"
            response.reset()
            populateValidParams(params)
            cannedValue = new CannedValue(params)

            controller.save(cannedValue)

        then:"A redirect is issued to the show action"
            response.redirectedUrl == '/cannedValue/show/1'
            controller.flash.message != null
            CannedValue.count() == 1
    }

    void "Test that the show action returns the correct model"() {
        when:"The show action is executed with a null domain"
            controller.show(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the show action"
            populateValidParams(params)
            def cannedValue = new CannedValue(params)
            controller.show(cannedValue)

        then:"A model is populated containing the domain instance"
            model.cannedValue == cannedValue
    }

    void "Test that the edit action returns the correct model"() {
        when:"The edit action is executed with a null domain"
            controller.edit(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the edit action"
            populateValidParams(params)
            def cannedValue = new CannedValue(params)
            controller.edit(cannedValue)

        then:"A model is populated containing the domain instance"
            model.cannedValue == cannedValue
    }

    void "Test the update action performs an update on a valid domain instance"() {
        when:"Update is called for a domain instance that doesn't exist"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'PUT'
            controller.update(null)

        then:"A 404 error is returned"
            response.redirectedUrl == '/cannedValue/index'
            flash.message != null


        when:"An invalid domain instance is passed to the update action"
            response.reset()
            def cannedValue = new CannedValue()
            cannedValue.validate()
            controller.update(cannedValue)

        then:"The edit view is rendered again with the invalid instance"
            view == 'edit'
            model.cannedValue == cannedValue

        when:"A valid domain instance is passed to the update action"
            response.reset()
            populateValidParams(params)
            cannedValue = new CannedValue(params).save(flush: true)
            controller.update(cannedValue)

        then:"A redirect is issues to the show action"
            response.redirectedUrl == "/cannedValue/show/$cannedValue.id"
            flash.message != null
    }

    void "Test that the delete action deletes an instance if it exists"() {
        when:"The delete action is called for a null instance"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'DELETE'
            controller.delete(null)

        then:"A 404 is returned"
            response.redirectedUrl == '/cannedValue/index'
            flash.message != null

        when:"A domain instance is created"
            response.reset()
            populateValidParams(params)
            def cannedValue = new CannedValue(params).save(flush: true)

        then:"It exists"
            CannedValue.count() == 1

        when:"The domain instance is passed to the delete action"
            controller.delete(cannedValue)

        then:"The instance is deleted"
            CannedValue.count() == 0
            response.redirectedUrl == '/cannedValue/index'
            flash.message != null
    }
}
