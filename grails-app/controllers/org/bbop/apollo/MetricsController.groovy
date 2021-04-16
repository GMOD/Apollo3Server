package org.bbop.apollo

import grails.converters.JSON
import groovy.json.JsonOutput
import groovy.json.StreamingJsonBuilder
import org.grails.web.json.JSONObject

class MetricsController {

    //{
    //  "version" : "3.0.0",
    //  "gauges" : { },
    //  "counters" : { },
    //  "histograms" : { },
    //  "meters" : { },
    //  "timers" : {
//    ...
    // }
    // ]

    def metrics() {

        StringWriter writer = new StringWriter()
        StreamingJsonBuilder builder = new StreamingJsonBuilder(writer)
        builder{
              "version"   "3.0.0"
              "gauges"  { }
              "counters"  { }
              "histograms"  { }
              "meters" { }
              "timers" {}
        }
        render new JSONObject(writer.toString()) as  JSON
    }

    def api(){
//        println servletContext.getRealPath("classes")
//        println servletContext.getRealPath("/classes")
//        println servletContext.getRealPath("/META-INF")
//        println servletContext.getRealPath("/swagger")
//        println servletContext.getRealPath("META-INF")
        println "A"
        String realPath = servletContext.getRealPath("/apollo-3.0.0.yml")
        println "B.5 ${realPath}"
//        println "context path: ${servletContext.getContextPath()}"
        def resource = servletContext.getContext("classpath:META-INF/swagger/apollo-3.0.0.yml")
//        def resource = servletContext.getRealPath("../groovy/main/META-INF/swagger/apollo-3.0.0.yml")
//                ./build/classes/groovy/main/META-INF/swagger/apollo-3.0.0.yml
        println "C.3"
        println resource
        println "D.5"
        def ymlFile = resource.
        println "E.5"
        println ymlFile
        println "F"
        File file = new File(realPath)
        println "file exists ${file.exists()}"
        String textFile = file.text

//        render 'swagger/apollo-3.0.0.yml'
        render textFile
    }
}
