package org.bbop.apollo.user

/**
 * These are global roles.
 */
class Role {
    String name
    Integer rank

    static hasMany = [ users: User, permissions: String ]
    // I think this might inflict a cascade we don't wnat
//    static belongsTo = User

    static constraints = {
        name(nullable: false, blank: false, unique: true)
        rank(nullable: true, blank: false, unique: true)
    }
}
