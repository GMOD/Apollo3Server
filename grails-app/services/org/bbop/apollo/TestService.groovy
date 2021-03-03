package org.bbop.apollo

import grails.gorm.transactions.Transactional
import org.bbop.apollo.feature.Feature

@Transactional
class TestService {


    Feature addUser(String username) {
        return new Feature(
                name: "bob",
                uniqueName: UUID.randomUUID().toString()
        ).save()
    }

    int getUserCount1(ArrayList<String> usernames) {
        Feature.countByNameInList(usernames)
    }

    int getUserCount2(ArrayList<String> usernames) {
        Feature.executeQuery(" MATCH (f:Feature) where f.name in ${usernames} return count(f) ").first() as Integer
    }

    void renameUser1(String uniqueName, String newName) {
        def feature = Feature.findByUniqueName(uniqueName)
        feature.name = newName
        feature.save()
    }

    void renameUser2(String oldName, String newName) {
        Feature.executeUpdate("MATCH (f:Feature) where f.name = '${oldName}' set f.name = '${newName}' return f")
    }


    void deleteUsers(ArrayList<String> usernames) {
        Feature.executeUpdate(" MATCH (f:Feature) where f.name in ${usernames} delete f")

    }
}
