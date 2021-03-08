package org.bbop.apollo

import grails.converters.JSON
import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.apache.shiro.util.ThreadContext
import org.apache.shiro.web.session.mgt.DefaultWebSessionManager
import org.bbop.apollo.gwt.shared.GlobalPermissionEnum
import org.bbop.apollo.organism.Organism
import org.bbop.apollo.organism.Sequence
import org.bbop.apollo.user.Role
import org.bbop.apollo.user.User
import org.bbop.apollo.variant.SequenceAlteration

//import grails.test.spock.IntegrationSpec
//import org.grails.web.json.JSONObject
import org.grails.web.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Rollback
class VcfHandlerServiceIntegrationSpec extends AbstractIntegrationSpec {


    def vcfHandlerService
    def requestHandlingService

    def setup() {
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
//            , organismId: organism.id
            , name: "Group1.10"
        ).save(failOnError: true, flush: true)
        organism.save(flush: true, failOnError: true)
        return  organism
    }

    @Rollback
    void "Add a handful of variants and export as VCF"() {

        given: "3 variants"
        setup()
        println("output orgs "+Organism.count + " " + Organism.count())
        String addVariant1String = "{ ${testCredentials} \"features\":[{\"reference_allele\":\"G\",\"variant_info\":[{\"tag\":\"dbSNP_150\",\"value\":true},{\"tag\":\"TSA\",\"value\":\"SNV\"},{\"tag\":\"E_Freq\",\"value\":true},{\"tag\":\"E_1000G\",\"value\":true},{\"tag\":\"MA\",\"value\":\"T\"},{\"tag\":\"MAF\",\"value\":\"0.000199681\"},{\"tag\":\"MAC\",\"value\":\"1\"},{\"tag\":\"AA\",\"value\":\"C\"}],\"name\":\"rs576820509\",\"alternate_alleles\":[{\"bases\":\"T\",\"allele_info\":[{\"tag\":\"EAS_AF\",\"value\":\"0.001\"},{\"tag\":\"EUR_AF\",\"value\":\"0\"},{\"tag\":\"AMR_AF\",\"value\":\"0\"},{\"tag\":\"SAS_AF\",\"value\":\"0\"},{\"tag\":\"AFR_AF\",\"value\":\"0\"}]}],\"description\":\"SNV C -> T\",\"location\":{\"strand\":1,\"fmin\":95193,\"fmax\":95194},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"SNV\"}}],\"track\":\"Group1.10\",\"operation\":\"add_variant\"}"
        String addVariant2String = "{ ${testCredentials} \"features\":[{\"reference_allele\":\"G\",\"variant_info\":[{\"tag\":\"dbSNP_150\",\"value\":true},{\"tag\":\"TSA\",\"value\":\"SNV\"},{\"tag\":\"E_Freq\",\"value\":true},{\"tag\":\"E_1000G\",\"value\":true},{\"tag\":\"MA\",\"value\":\"G\"},{\"tag\":\"MAF\",\"value\":\"0.151158\"},{\"tag\":\"MAC\",\"value\":\"757\"},{\"tag\":\"AA\",\"value\":\"C\"}],\"name\":\"rs544194668\",\"alternate_alleles\":[{\"bases\":\"T\",\"allele_info\":[{\"tag\":\"EAS_AF\",\"value\":\"0.3958\"},{\"tag\":\"EUR_AF\",\"value\":\"0.0765\"},{\"tag\":\"AMR_AF\",\"value\":\"0.0965\"},{\"tag\":\"SAS_AF\",\"value\":\"0.1871\"},{\"tag\":\"AFR_AF\",\"value\":\"0.0234\"}]}],\"description\":\"SNV C -> G\",\"location\":{\"strand\":1,\"fmin\":95439,\"fmax\":95440},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"SNV\"}}],\"track\":\"Group1.10\",\"operation\":\"add_variant\"}"
        String addVariant3String = "{ ${testCredentials} \"features\":[{\"reference_allele\":\"AAATT\",\"variant_info\":[{\"tag\":\"dbSNP_150\",\"value\":true},{\"tag\":\"TSA\",\"value\":\"deletion\"},{\"tag\":\"E_Freq\",\"value\":true},{\"tag\":\"E_1000G\",\"value\":true},{\"tag\":\"MA\",\"value\":\"-\"},{\"tag\":\"MAF\",\"value\":\"0.000599042\"},{\"tag\":\"MAC\",\"value\":\"3\"}],\"name\":\"rs533528979\",\"alternate_alleles\":[{\"bases\":\"A\",\"allele_info\":[{\"tag\":\"EAS_AF\",\"value\":\"0\"},{\"tag\":\"EUR_AF\",\"value\":\"0\"},{\"tag\":\"AMR_AF\",\"value\":\"0\"},{\"tag\":\"SAS_AF\",\"value\":\"0\"},{\"tag\":\"AFR_AF\",\"value\":\"0.0023\"}]}],\"description\":\"deletion CAAAG -> C\",\"location\":{\"strand\":1,\"fmin\":91868,\"fmax\":91873},\"type\":{\"cv\":{\"name\":\"sequence\"},\"name\":\"deletion\"}}],\"track\":\"Group1.10\",\"operation\":\"add_variant\"}"

        when: "we add all the variants"
        println "adding Organism ${Organism.count}"
        println "adding Sequence ${Sequence.count}"
        requestHandlingService.addVariant(JSON.parse(addVariant1String) as JSONObject)
        requestHandlingService.addVariant(JSON.parse(addVariant2String) as JSONObject)
        requestHandlingService.addVariant(JSON.parse(addVariant3String) as JSONObject)

        then: "we should see all the variants"
        assert SequenceAlteration.count == 3

        when: "we export these variants"
        File tempFile = File.createTempFile("output", ".vcf")
        tempFile.deleteOnExit()
        def variants = SequenceAlteration.all
        vcfHandlerService.writeVariantsToText(Organism.all.first(), variants, tempFile.path, "test")
        String tempFileText = tempFile.text
        print tempFileText

        then: "we should get a valid VCF"
        def lines = tempFile.readLines()
        assert lines.size() == 9
        assert lines[0] == "##fileformat=VCFv4.2"
        assert lines[2] == "##source=test"
        assert lines[4] == "##contig=<ID=Group1.10,length=1405242>"
        assert lines[5] == "#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO"
    }
}
