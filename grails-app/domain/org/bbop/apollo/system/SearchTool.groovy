package org.bbop.apollo.system

class SearchTool {

    static constraints = {
    }

    String key
    String implementationClass
    String binaryPath
    String tmpDir
    String databasePath
    String options
    boolean removeTempDirectory
    static mapping = {
        key column: "search_key"
    }

}
