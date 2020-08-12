package org.bbop.apollo

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.apache.shiro.crypto.hash.Sha256Hash
import org.apache.shiro.util.ThreadContext
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.user.Role
import org.bbop.apollo.user.User
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification

/**
 * Created by nathandunn on 11/4/15.
 */
@Integration
@Rollback
class AbstractIntegrationSpec extends Specification {

    def shiroSecurityManager

    String password = "testPass"
    String passwordHash = new Sha256Hash(password).toHex()

//    def setup() {
//        println "in setup"
//        setupDefaultUserOrg()
//        println "out setup"
//    }

    String getTestCredentials(String organismCommonName = "sampleAnimal") {
//        String returnString = "\"${FeatureStringEnum.CLIENT_TOKEN.value}\":\"${clientToken}\",\"${FeatureStringEnum.USERNAME.value}\":\"test@test.com\","
        String returnString = "\"${FeatureStringEnum.USERNAME.value}\":\"test@test.com\","
//        if (Organism.count == 1) {
//            returnString += "\"${FeatureStringEnum.ORGANISM.value}\":\"${Organism.all.first().id}\","
        returnString += "\"${FeatureStringEnum.ORGANISM.value}\":\"${organismCommonName}\","
//        }
        return returnString
    }

    def setupDefaultUserOrg() {
        println "setup default user org ${Organism.count}"
        if (User.findByUsername('test@test.com')) {
            return
        }

        User testUser = new User(
            username: 'test@test.com'
            , firstName: 'Bob'
            , lastName: 'Test'
            , passwordHash: passwordHash
        ).save(insert: true, flush: true)
        def adminRole = Role.findByName(GlobalPermissionEnum.ADMIN.name())
        testUser.addToRoles(adminRole)
        testUser.save()

        shiroSecurityManager.sessionManager = new DefaultWebSessionManager()
        ThreadContext.bind(shiroSecurityManager)
//        def authToken = new UsernamePasswordToken(testUser.username,password as String)
//        Subject subject = SecurityUtils.getSubject();
//        subject.login(authToken)

        Organism organism = new Organism(
            directory: "src/integration-test/groovy/resources/sequences/honeybee-Group1.10/"
            , commonName: "sampleAnimal"
            , id: 12313
            , genus: "Sample"
            , species: "animal"
        ).save(failOnError: true, flush: true)

        Sequence sequence = new Sequence(
            length: 1405242
            , seqChunkSize: 20000
            , start: 0
            , end: 1405242
            , organism: organism
            , organismId: organism.id
            , name: "Group1.10"
        ).save(failOnError: true, flush: true)


        println "organism ${organism} abnd ${organism as JSON}"
        println "sequence ${sequence} and ${sequence as JSON}"
        println "sequence organism ${sequence.organism} "

//        organism.addToSequences(sequence)
        println "2 organism ${organism} abnd ${organism as JSON}"
        println "2 sequence ${sequence} and ${sequence as JSON}"
        println "2 sequence organism ${sequence.organism} "
        organism.save(flush: true, failOnError: true)

        println "added organissm ${Organism.count}"
        println "added sequence ${Sequence.count}"
        println "added user ${User.count}"
        return  organism
    }

    JSONArray getCodingArray(JSONObject jsonObject) {
        JSONArray mrnaArray = jsonObject.getJSONArray(FeatureStringEnum.FEATURES.value)
        assert 1 == mrnaArray.size()
        return mrnaArray.getJSONObject(0).getJSONArray(FeatureStringEnum.CHILDREN.value)
    }
}
