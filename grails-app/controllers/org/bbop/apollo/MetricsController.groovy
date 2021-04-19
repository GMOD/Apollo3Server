package org.bbop.apollo

import grails.converters.JSON
import groovy.json.JsonOutput
import groovy.json.StreamingJsonBuilder
import org.grails.web.json.JSONObject

class MetricsController {

    def grailsResourceLocator

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
        def r1 = grailsResourceLocator.findResourceForURI('classpath:apollo-3.0.0.yml')
        render r1.inputStream.text
    }
}
