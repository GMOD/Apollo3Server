package org.bbop.apollo

import org.bbop.apollo.permission.UserPermission

class UserTrackPermission extends UserPermission{

    String trackVisibilities // JSON representation (name:'',visible:t/f)

    static constraints = {
    }

    static mapping = {
    }

}
