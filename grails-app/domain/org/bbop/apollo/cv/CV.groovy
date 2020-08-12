package org.bbop.apollo.cv
class CV {

    static constraints = {
    }

     String name;
     String definition;

     static hasMany = [
             cvterms: CVTerm
     ]
}
