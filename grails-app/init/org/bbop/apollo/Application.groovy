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
                title = "Apollo",
                version = "3.0.0",
                description = "Web Apollo Server",
                license = @License(name = "Modified BSD License", url = "https://raw.githubusercontent.com/GMOD/Apollo3Server/master/LICENSE.md"),
                contact = @Contact(url = "https://github.com/GMOD/Apollo3Server/issues", name = "Raise issue", email = "ndunnme@gmail.com")        )
)
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
}