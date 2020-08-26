package org.bbop.apollo.attributes

class AvailableStatus {

    static constraints = {
//        label nullable: true
        value nullable: false
    }

//    String label
    String value

    static hasMany = [
            featureTypes: FeatureType
    ]

}
