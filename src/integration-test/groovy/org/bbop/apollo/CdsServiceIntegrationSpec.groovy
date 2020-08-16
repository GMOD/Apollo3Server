package org.bbop.apollo

import grails.converters.JSON
import grails.gorm.transactions.Rollback
import grails.test.neo4j.Neo4jSpec
import grails.testing.mixin.integration.Integration
import org.apache.shiro.crypto.hash.Sha256Hash
import org.apache.shiro.util.ThreadContext
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager
import org.bbop.apollo.feature.*
import org.bbop.apollo.gwt.shared.FeatureStringEnum
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.relationship.FeatureRelationship
import org.bbop.apollo.user.Role
import org.bbop.apollo.user.User
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

@Integration
@Rollback
//class CdsServiceIntegrationSpec extends AbstractIntegrationSpec{
class CdsServiceIntegrationSpec extends Neo4jSpec {

    def sequenceService
    def userService
    def requestHandlingService
    def transcriptService

    def shiroSecurityManager
    String password = "testPass"
    String passwordHash = new Sha256Hash(password).toHex()

    def setupData() {
        if (User.findByUsername('test@test.com')) {
            println "return the user ? ${User.findByUsername('test@test.com')}"
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
        adminRole.addToUsers(testUser)
//        testUser.addToRoles(adminRole)
        testUser.save(flush: true)

        shiroSecurityManager.sessionManager = new DefaultWebSessionManager()
        ThreadContext.bind(shiroSecurityManager)
//        def authToken = new UsernamePasswordToken(testUser.username,password as String)
//        Subject subject = SecurityUtils.getSubject();
//        subject.login(authToken)

    }

    String getTestCredentials(String organismCommonName = "sampleAnimal") {
//        String returnString = "\"${FeatureStringEnum.CLIENT_TOKEN.value}\":\"${clientToken}\",\"${FeatureStringEnum.USERNAME.value}\":\"test@test.com\","
        String returnString = "\"${FeatureStringEnum.USERNAME.value}\":\"test@test.com\","
//        if (Organism.count == 1) {
//            returnString += "\"${FeatureStringEnum.ORGANISM.value}\":\"${Organism.all.first().id}\","
        returnString += "\"${FeatureStringEnum.ORGANISM.value}\":\"${organismCommonName}\","
//        }
        return returnString
    }

    void "adding a gene model, a stop codon readthrough and getting its modified sequence"() {

        given: "a gene model with 1 mRNA, 3 exons, and UTRs"
        setupData()
        String jsonString = "{ ${testCredentials} \"operation\":\"add_transcript\",\"features\":[{\"location\":{\"fmin\":734606,\"strand\":1,\"fmax\":735570},\"name\":\"GB40828-RA\",\"children\":[{\"location\":{\"fmin\":734606,\"strand\":1,\"fmax\":734733},\"type\":{\"name\":\"exon\",\"cv\":{\"name\":\"sequence\"}}},{\"location\":{\"fmin\":735446,\"strand\":1,\"fmax\":735570},\"type\":{\"name\":\"exon\",\"cv\":{\"name\":\"sequence\"}}},{\"location\":{\"fmin\":734606,\"strand\":1,\"fmax\":734766},\"type\":{\"name\":\"exon\",\"cv\":{\"name\":\"sequence\"}}},{\"location\":{\"fmin\":734930,\"strand\":1,\"fmax\":735014},\"type\":{\"name\":\"exon\",\"cv\":{\"name\":\"sequence\"}}},{\"location\":{\"fmin\":735245,\"strand\":1,\"fmax\":735570},\"type\":{\"name\":\"exon\",\"cv\":{\"name\":\"sequence\"}}},{\"location\":{\"fmin\":734733,\"strand\":1,\"fmax\":735446},\"type\":{\"name\":\"CDS\",\"cv\":{\"name\":\"sequence\"}}}],\"type\":{\"name\":\"mRNA\",\"cv\":{\"name\":\"sequence\"}}}],\"track\":\"Group1.10\"}"
        "{ \"track\": \"Group1.10\", \"features\": [{\"location\":{\"fmin\":1248797,\"fmax\":1249052,\"strand\":-1},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"mRNA\"},\"name\":\"GB40722-RA\",\"children\":[{\"location\":{\"fmin\":1248797,\"fmax\":1249052,\"strand\":-1},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"exon\"}},{\"location\":{\"fmin\":1248797,\"fmax\":1249052,\"strand\":-1},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"CDS\"}}]}], \"operation\": \"add_transcript\" }"
        JSONObject jsonObject = JSON.parse(jsonString) as JSONObject
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
//        organism.addToSequences(sequence)
//        organism.save(flush: true, failOnError: true)
        String updateQuery = "MATCH (o:Organism),(s:Sequence) where o.commonName = '${organism.commonName}' and s.name = '${sequence.name}' create (o)-[r:SEQUENCES]->(s)"
        println "update query ${updateQuery}"
        def updateResult = Organism.executeUpdate(updateQuery)
        println "update result ${updateResult}"

        expect: "everything to be there "
        assert Organism.count == 1
        assert Sequence.count == 1
        assert User.count == 1
//        MATCH (a:Person),(b:Person)
//        WHERE a.name = 'A' AND b.name = 'B'
//        CREATE (a)-[r:RELTYPE]->(b)
//        RETURN type(r)

        when: "gene model is added"

        JSONObject returnObject = requestHandlingService.addTranscript(jsonObject)

        then: "we should see the appropriate model"
        assert Sequence.count == 1
        assert Gene.count == 1
        assert MRNA.count == 1
        assert Exon.count == 3
        assert CDS.count == 1
//        assert FeatureLocation.count == 6 + FlankingRegion.count
        assert FeatureRelationship.count == 5

        when: "a stopCodonReadThrough is created"
        Transcript transcript = Transcript.findByName("GB40828-RA-00001")
        CDS cds = transcriptService.getCDS(transcript)
        String setReadThroughStopCodonString = "{ ${testCredentials} \"operation\":\"set_readthrough_stop_codon\",\"features\":[{\"readthrough_stop_codon\":true,\"uniquename\":\"@UNIQUENAME@\"}],\"track\":\"Group1.10\",\"clientToken\":\"1231232\"}"
        setReadThroughStopCodonString = setReadThroughStopCodonString.replace("@UNIQUENAME@", transcript.uniqueName)
        JSONObject setReadThroughRequestObject = JSON.parse(setReadThroughStopCodonString) as JSONObject
        JSONObject setReadThroughReturnObject = requestHandlingService.setReadthroughStopCodon(setReadThroughRequestObject)
        println "${setReadThroughReturnObject.toString()}"

        then: "we have a StopCodonReadThrough feature"
        assert StopCodonReadThrough.count == 1

        JSONArray childrenArray = setReadThroughReturnObject.features.children
        for (def child : childrenArray) {
            if (child['name'].contains("-CDS")) {
                println child['children'].location.fmin
                println child['children'].location.fmax
                int size = (child['children'].location.fmax[0] - child['children'].location.fmin[0])
                assert size == 3
            }
        }

        when: "a request is sent for the CDS sequence with the read through stop codon"
        String getSequenceString = "{ ${testCredentials} \"operation\":\"get_sequence\",\"features\":[{\"uniquename\":\"@UNIQUENAME@\"}],\"track\":\"Group1.10\",\"type\":\"@SEQUENCE_TYPE@\"}"
        String getCdsSequenceString = getSequenceString.replaceAll("@UNIQUENAME@", transcript.uniqueName)
        getCdsSequenceString = getCdsSequenceString.replaceAll("@SEQUENCE_TYPE@", FeatureStringEnum.TYPE_CDS.value)
        JSONObject commandObject = JSON.parse(getCdsSequenceString) as JSONObject
        JSONObject getCDSSequenceReturnObject = sequenceService.getSequenceForFeatures(commandObject)

        then: "we should get the anticipated CDS sequence"
        assert getCDSSequenceReturnObject.residues != null
        String expectedCdsSequence = "ATGGAATCTGCTATTGTTCATCTTGAACAAAGCGTGCAAAAGGCTGATGGAAAACTAGACATGATTGCATGGCAAATTGATGCTTTTGAAAAAGAATTTGAAGATCCTGGTAGTGAGATTTCTGTGCTTCGTCTATTACGGTCTGTTCATCAAGTCACAAAAGATTATCAGAACCTTCGGCAAGAAATATTGGAGGTTCAACAATTGCAAAAGCAACTTTCAGATTCCCTTAAAGCACAATTATCTCAAGTGCATGGACATTTTAACTTATTACGCAATAAAATAGTAGGACAAAATAAAAATCTACAATTAAAATAAGATTAA"
        assert getCDSSequenceReturnObject.residues == expectedCdsSequence
    }
}
