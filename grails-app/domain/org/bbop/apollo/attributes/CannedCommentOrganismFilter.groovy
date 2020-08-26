package org.bbop.apollo.attributes

class CannedCommentOrganismFilter extends OrganismFilter {

    CannedComment cannedComment

    static constraints = {
        cannedComment nullable: false
    }
}
