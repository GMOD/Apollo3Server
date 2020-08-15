package org.bbop.apollo.attributes

class CannedKey {

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
