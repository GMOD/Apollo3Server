package org.bbop.apollo

class WebServicesController {

    def index() {
        api()
    }

    def api() {
        String realPath = servletContext.getRealPath("/swagger/views/swagger-ui/index.html")
        render new File(realPath).text
    }

}
