import grails.converters.JSON
import org.bbop.apollo.attributes.FeatureType
import org.bbop.apollo.sequence.SequenceTranslationHandler


class BootStrap {

    def configWrapperService
    def grailsApplication
    def featureTypeService
    def domainMarshallerService
    def proxyService
    def userService
    def roleService
    def trackService
    def phoneHomeService


    def init = { servletContext ->
        println "Initializing..."
        def dataSource = grailsApplication.config.dataSource
        println "Datasource"
        println "Url: ${dataSource.url}"
        println "Driver: ${dataSource.driverClassName}"
        println "Dialect: ${dataSource.dialect}"

        System.getenv().each {
            log.debug it.key + "->" + it.value
        }

        domainMarshallerService.registerObjects()
        proxyService.initProxies()


        SequenceTranslationHandler.spliceDonorSites.addAll(configWrapperService.spliceDonorSites)
        SequenceTranslationHandler.spliceAcceptorSites.addAll(configWrapperService.spliceAcceptorSites)

        if(FeatureType.count==0){
            featureTypeService.stubDefaultFeatureTypes()
        }

        roleService.initRoles()

        def admin = grailsApplication.config?.apollo?.admin
        if(admin){
            userService.registerAdmin(admin.username,admin.password,admin.firstName,admin.lastName)
        }
        def adminUser = userService.registerAdmin("admin@local.host","password","Admin","User")
        println "adminuser ${adminUser as JSON}"


        def commandCommonDirectory = trackService.checkCommonDataDirectory()
        println commandCommonDirectory

        phoneHomeService.pingServerAsync(org.bbop.apollo.PhoneHomeEnum.START.value)

    }
    def destroy = {
        phoneHomeService.pingServer(org.bbop.apollo.PhoneHomeEnum.STOP.value)
    }
}
