package org.bbop.apollo

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback

@Integration
@Rollback
class PhoneHomeServiceIntegrationSpec extends AbstractIntegrationSpec {

    def phoneHomeService

//    void "test ping"() {
//        when: "we ping the server"
//        def json = phoneHomeService.pingServer()
//
//        then: "we should get an empty response"
//        assert "{}" == json.toString()
//    }

}
