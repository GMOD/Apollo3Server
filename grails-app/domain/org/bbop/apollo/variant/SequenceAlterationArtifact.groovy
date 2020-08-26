package org.bbop.apollo.variant

import org.bbop.apollo.feature.Feature


class SequenceAlterationArtifact extends Feature {

    static constraints = {
        alterationResidue nullable: true
    }

    String alterationResidue

    static String cvTerm = "sequence_alteration_artifact"
    static String ontologyId = "SO:0002172"
    static String alternateCvTerm = "SequenceAlterationArtifact"

    public int getOffset() {
        return 0
    }

}
