package org.bbop.apollo.feature

class NonCanonicalThreePrimeSpliceSite extends SpliceSite{

    static mapping = {
        labels "NonCanonicalThreePrimeSpliceSite", "SpliceSite", "TranscriptRegion", "Feature"
    }

    static constraints = {
    }

    static String cvTerm = "non_canonical_three_prime_splice_site"
    static String ontologyId = "SO:0000678"
    static String alternateCvTerm  = "NonCanonicalThreePrimeSpliceSite"

}
