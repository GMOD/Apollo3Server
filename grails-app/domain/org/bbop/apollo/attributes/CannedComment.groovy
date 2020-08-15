package org.bbop.apollo.attributes

class CannedComment {

    static constraints = {
        comment nullable: false
        metadata nullable: true
    }

    String comment
    String metadata

    static hasMany = [
            featureTypes: FeatureType
    ]
}
