package org.bbop.apollo

import org.bbop.apollo.organism.Organism
import org.bbop.apollo.user.User


class PhoneHomeJob {

    def phoneHomeService
    def configWrapperService

    static triggers = {
//      simple repeatInterval: 5000l // execute job once a day
        simple repeatInterval: 24 * 60 * 60 * 1000l // execute job once a day
    }

    def execute() {
        // execute job
        def map = ["num_users": User.count.toString(), "num_organisms": Organism.count.toString()]
        phoneHomeService.pingServer(PhoneHomeEnum.RUNNING.value,map)
    }
}
