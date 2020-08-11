package org.bbop.apollo

import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

/**
 */
//@TestFor(NameService)
class NameServiceSpec extends Specification implements ServiceUnitTest<NameService> {

    void "letter padding strategy should work"() {

        when: "we have 1"
        LetterPaddingStrategy letterPaddingStrategy = new LetterPaddingStrategy()

        then: "assert a"
        assert "a" == letterPaddingStrategy.pad(0)
        assert "b" == letterPaddingStrategy.pad(1)
        assert "c" == letterPaddingStrategy.pad(2)
        assert "z" == letterPaddingStrategy.pad(25)
        assert "aa" == letterPaddingStrategy.pad(26)
        assert "ab" == letterPaddingStrategy.pad(27)
        assert "aaaz" == letterPaddingStrategy.pad(103)

    }
}
