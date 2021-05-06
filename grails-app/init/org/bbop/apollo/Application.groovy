package org.bbop.apollo

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import groovy.transform.CompileStatic
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License

@CompileStatic
@OpenAPIDefinition(
        info = @Info(
                title = "TODO",
                version = "1.0",
                description = "TODO",
                license = @License(name = "TODO", url = "TODO"),
                contact = @Contact(url = "TODO", name = "TODO", email = "TODO")
        )
)
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}