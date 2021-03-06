package org.bbop.apollo


import grails.testing.gorm.DomainUnitTest
import grails.testing.web.controllers.ControllerUnitTest
import org.bbop.apollo.system.Proxy
import spock.lang.*

//@TestFor(ProxyController)
//@Mock(Proxy)
class ProxyControllerSpec extends Specification implements ControllerUnitTest<ProxyController>, DomainUnitTest<Proxy>{

    def populateValidParams(params) {
        assert params != null
        // TODO: Populate valid properties like...
        params["referenceUrl"] = 'http://someValidName.com'
        params["targetUrl"] = 'http://someOtherValidName.com'
        params["active"] = true
    }

    void "Test the index action returns the correct model"() {

        when:"The index action is executed"
            controller.index()

        then:"The model is correct"
            !model.proxyList
            model.proxyInstanceCount == 0
    }

    void "Test the create action returns the correct model"() {
        when:"The create action is executed"
            controller.create()

        then:"The model is correctly created"
            model.proxy != null
    }

    void "Test the save action correctly persists an instance"() {

        when:"The save action is executed with an invalid instance"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'POST'
            def proxy = new Proxy()
            proxy.validate()
            controller.save(proxy)

        then:"The create view is rendered again with the correct model"
            model.proxy!= null
            view == 'create'

        when:"The save action is executed with a valid instance"
            response.reset()
            populateValidParams(params)
            proxy = new Proxy(params)

            controller.save(proxy)

        then:"A redirect is issued to the show action"
            response.redirectedUrl == '/proxy/show/1'
            controller.flash.message != null
            Proxy.count() == 1
    }

    void "Test that the show action returns the correct model"() {
        when:"The show action is executed with a null domain"
            controller.show(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the show action"
            populateValidParams(params)
            def proxy = new Proxy(params)
            controller.show(proxy)

        then:"A model is populated containing the domain instance"
            model.proxy == proxy
    }

    void "Test that the edit action returns the correct model"() {
        when:"The edit action is executed with a null domain"
            controller.edit(null)

        then:"A 404 error is returned"
            response.status == 404

        when:"A domain instance is passed to the edit action"
            populateValidParams(params)
            def proxy = new Proxy(params)
            controller.edit(proxy)

        then:"A model is populated containing the domain instance"
            model.proxy == proxy
    }

    void "Test the update action performs an update on a valid domain instance"() {
        when:"Update is called for a domain instance that doesn't exist"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'PUT'
            controller.update(null)

        then:"A 404 error is returned"
            response.redirectedUrl == '/proxy/index'
            flash.message != null


        when:"An invalid domain instance is passed to the update action"
            response.reset()
            def proxy = new Proxy()
            proxy.validate()
            controller.update(proxy)

        then:"The edit view is rendered again with the invalid instance"
            view == 'edit'
            model.proxy == proxy

        when:"A valid domain instance is passed to the update action"
            response.reset()
            populateValidParams(params)
            proxy = new Proxy(params).save(flush: true)
            controller.update(proxy)

        then:"A redirect is issues to the show action"
            response.redirectedUrl == "/proxy/show/$proxy.id"
            flash.message != null
    }

    void "Test that the delete action deletes an instance if it exists"() {
        when:"The delete action is called for a null instance"
            request.contentType = FORM_CONTENT_TYPE
            request.method = 'DELETE'
            controller.delete(null)

        then:"A 404 is returned"
            response.redirectedUrl == '/proxy/index'
            flash.message != null

        when:"A domain instance is created"
            response.reset()
            populateValidParams(params)
            def proxy = new Proxy(params).save(flush: true)

        then:"It exists"
            Proxy.count() == 1

        when:"The domain instance is passed to the delete action"
            controller.delete(proxy)

        then:"The instance is deleted"
            Proxy.count() == 0
            response.redirectedUrl == '/proxy/index'
            flash.message != null
    }
}
