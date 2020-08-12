package org.bbop.apollo.attributes

class CannedValue {

    static constraints = {
        label nullable: false
        metadata nullable: true
    }

    String label
    String metadata

    static hasMany = [
        featureTypes: FeatureType
    ]
}
