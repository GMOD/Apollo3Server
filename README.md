# Apollo
[![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.3555454.svg)](https://doi.org/10.5281/zenodo.3555454)
[![Build](https://travis-ci.org/GMOD/ApolloServer.svg?branch=develop)](https://travis-ci.org/GMOD/ApolloServer?branch=develop)
[![License](https://img.shields.io/badge/License-BSD%202--Clause-orange.svg)](https://opensource.org/licenses/BSD-2-Clause)
[![Contributor Covenant](https://img.shields.io/badge/Contributor%20Covenant-v1.4%20adopted-ff69b4.svg?style=for-the-badge&logo=data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTQiIGhlaWdodD0iMTQiIHZpZXdCb3g9IjAgMCAyNTYgMjU2IiB2ZXJzaW9uPSIxLjEiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyIgeG1sbnM6eGxpbms9Imh0dHA6Ly93d3cudzMub3JnLzE5OTkveGxpbmsiPjx0aXRsZT5Db250cmlidXRvciBDb3ZlbmFudCBMb2dvPC90aXRsZT48ZyBpZD0iQ2FudmFzIj48ZyBpZD0iR3JvdXAiPjxnIGlkPSJTdWJ0cmFjdCI+PHVzZSB4bGluazpocmVmPSIjcGF0aDBfZmlsbCIgZmlsbD0iIzVFMEQ3MyIvPjwvZz48ZyBpZD0iU3VidHJhY3QiPjx1c2UgeGxpbms6aHJlZj0iI3BhdGgxX2ZpbGwiIHRyYW5zZm9ybT0idHJhbnNsYXRlKDU4IDI0KSIgZmlsbD0iIzVFMEQ3MyIvPjwvZz48L2c+PC9nPjxkZWZzPjxwYXRoIGlkPSJwYXRoMF9maWxsIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0gMTgyLjc4NyAxMi4yODQ2QyAxNzMuMDA1IDkuNDk0MDggMTYyLjY3NyA4IDE1MiA4QyA5MC4xNDQxIDggNDAgNTguMTQ0MSA0MCAxMjBDIDQwIDE4MS44NTYgOTAuMTQ0MSAyMzIgMTUyIDIzMkMgMTg4LjQ2NCAyMzIgMjIwLjg1NyAyMTQuNTc1IDI0MS4zMDggMTg3LjU5OEMgMjE5Ljg3IDIyOC4yNzIgMTc3LjE3MyAyNTYgMTI4IDI1NkMgNTcuMzA3NSAyNTYgMCAxOTguNjkyIDAgMTI4QyAwIDU3LjMwNzUgNTcuMzA3NSAwIDEyOCAwQyAxNDcuNjA0IDAgMTY2LjE3OSA0LjQwNzA5IDE4Mi43ODcgMTIuMjg0NloiLz48cGF0aCBpZD0icGF0aDFfZmlsbCIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNIDEzNy4wOSA5LjIxMzQyQyAxMjkuNzU0IDcuMTIwNTYgMTIyLjAwOCA2IDExNCA2QyA2Ny42MDgxIDYgMzAgNDMuNjA4MSAzMCA5MEMgMzAgMTM2LjM5MiA2Ny42MDgxIDE3NCAxMTQgMTc0QyAxNDEuMzQ4IDE3NCAxNjUuNjQzIDE2MC45MzEgMTgwLjk4MSAxNDAuNjk4QyAxNjQuOTAzIDE3MS4yMDQgMTMyLjg4IDE5MiA5NiAxOTJDIDQyLjk4MDcgMTkyIDAgMTQ5LjAxOSAwIDk2QyAwIDQyLjk4MDcgNDIuOTgwNyAwIDk2IDBDIDExMC43MDMgMCAxMjQuNjM0IDMuMzA1MzEgMTM3LjA5IDkuMjEzNDJaIi8+PC9kZWZzPjwvc3ZnPg==)](docs/CODE_OF_CONDUCT.md)


### [![](https://github.com/GMOD/Apollo/blob/master/docs/images/download_small.png)&nbsp;Download the latest release](https://github.com/GMOD/Apollo/releases/latest) ![](docs/images/ApolloLogo_100x36.png)

A collaborative, real-time, genome annotation editor.  The stack is a Java web application / database backend and a
Javascript client that runs in a web browser as a JBrowse plugin.  

Cite Apollo using `Dunn NA, Unni DR, Diesh C, Munoz-Torres M, Harris NL, Yao E, et al. (2019) Apollo: Democratizing genome annotation. PLoS Comput Biol 15(2): e1006790.` <https://doi.org/10.1371/journal.pcbi.1006790>


Questions / Comments / Community contact can be sent to our [Apollo user mailing list](mailto:apollo@lbl.gov) or posted directory to our [google group](https://groups.google.com/a/lbl.gov/forum/#!forum/apollo). Old questions are [archived on Nabble](http://gmod.827538.n3.nabble.com/Apollo-f815553.html).

Complete Apollo installation and configuration instructions are available from the [Apollo documentation pages](http://genomearchitect.readthedocs.io/en/latest/)

The Apollo client is implemented as a plugin for [JBrowse](http://jbrowse.org).  Additional JBrowse plugins may be found in the [JBrowse registry](https://gmod.github.io/jbrowse-registry/) and configured in ```apollo-config.groovy```.

We provide a [Demonstration Apollo](docs/Demo.md) site and an integrated service is provided by [UseGalaxy Europe](https://usegalaxy.eu/).

The [User's Guide](docs/UsersGuide.md) provides guidance on how to use it.  Please feel free to update this documentation.


### Launch for development (server only)

    ./grailsw run-app 
    
OR 
    
    ./gradlew bootRun 
    
### Run tests 

    ./grailsw test-app
    
OR 
    
    ./gradlew test
    
### Run with Docker

     docker build . 
     docker run -it --publish=8888:8080 --publish=7777:7474 <built image>
     
 For pre-built:
 
     docker run -it --publish=8888:8080 --publish=7777:7474 gmod/apollo3server:latest

Connect at http://localhost:8888/ . . . eg. http://localhost:8888/user/loadUsers

You'll need to kill the docker instance here, for the moment. 

### Thanks to

[![YourKit](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/) 

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

## Note

Under development . . more details to come.

