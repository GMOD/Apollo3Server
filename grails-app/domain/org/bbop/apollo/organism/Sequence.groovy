package org.bbop.apollo.organism

import org.bbop.apollo.location.FeatureLocation

class Sequence {

    static constraints = {
        name nullable: false
        start nullable: false
        end nullable: false
        organism nullable: true
        organismId nullable: true
        seqChunkSize nullable: true
    }


    // feature locations instead of features
    static hasMany = [
        featureLocations: FeatureLocation,
//        sequenceChunks: SequenceChunk
    ]

    static mapping = {
        end column: 'sequence_end'
        start column: 'sequence_start'
        featureLocations cascade: 'all-delete-orphan'
        sequenceChunks cascade: 'all-delete-orphan'
    }

    static belongsTo = [Organism]


    String name
    Organism organism
    Long organismId
    Integer length
    Integer seqChunkSize
    Integer start
    Integer end
}
