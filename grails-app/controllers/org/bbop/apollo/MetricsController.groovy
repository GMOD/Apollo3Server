package org.bbop.apollo

import grails.converters.JSON
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
    }}
