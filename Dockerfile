# Apollo3.X
FROM ubuntu:18.04
MAINTAINER Nathan Dunn <nathandunn@lbl.gov>
ENV DEBIAN_FRONTEND noninteractive

# where bin directories are
ENV CATALINA_HOME /usr/share/tomcat9
# where webapps are deployed
ENV CATALINA_BASE /var/lib/tomcat9
ENV CONTEXT_PATH ROOT
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

RUN apt-get -qq update --fix-missing && \
	apt-get --no-install-recommends -y install \
	git build-essential libpq-dev wget python3-pip \
	lsb-release gnupg2 wget xmlstarlet netcat libpng-dev postgresql-common \
	zlib1g-dev libexpat1-dev curl ssl-cert zip unzip openjdk-8-jdk-headless

RUN apt-get -qq update --fix-missing && \
	apt-get --no-install-recommends -y install \
	tomcat9 && \
	apt-get autoremove -y && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /apollo/

RUN curl -s "http://hgdownload.soe.ucsc.edu/admin/exe/linux.x86_64/blat/blat" -o /usr/local/bin/blat && \
 		chmod +x /usr/local/bin/blat && \
 		curl -s "http://hgdownload.soe.ucsc.edu/admin/exe/linux.x86_64/faToTwoBit" -o /usr/local/bin/faToTwoBit && \
 		chmod +x /usr/local/bin/faToTwoBit

#NOTE, we had problems with the build the archive-file coming in from github so using a clone instead
RUN useradd -ms /bin/bash -d /apollo apollo
COPY gradlew /apollo
COPY gradle.properties /apollo
COPY gradle /apollo/gradle
COPY grails-app /apollo/grails-app
#COPY lib /apollo/lib
COPY src /apollo/src
COPY src/main/scripts /apollo/scripts
ADD grails* /apollo/
#COPY apollo /apollo/apollo
COPY build.gradle /apollo/build.gradle
ADD settings.gradle /apollo
RUN ls /apollo

#COPY docker-files/build.sh /bin/build.sh
#RUN ["chmod", "+x", "/bin/build.sh"]
#ADD docker-files/docker-apollo-config.groovy /apollo/apollo-config.groovy
ADD docker-files/docker.apollo.yml /apollo/apollo.yml
RUN chown -R apollo:apollo /apollo

# install grails and python libraries
USER apollo

#RUN pip3 install setuptools
#RUN pip3 install nose "apollo==4.2"

WORKDIR /apollo
RUN ./grailsw clean
RUN rm -rf build/*
RUN ./grailsw war
RUN cp /apollo/build/libs/*.war /tmp/apollo.war && rm -rf /apollo/ || true
RUN mv /tmp/apollo.war /apollo/apollo.war

USER root
#RUN /bin/build.sh
# remove from webapps and copy it into a staging directory
RUN rm -rf ${CATALINA_BASE}/webapps/* && \
	cp /apollo/apollo*.war ${CATALINA_BASE}/apollo.war

ADD docker-files/createenv.sh /createenv.sh
ADD docker-files/launch.sh /launch.sh
CMD "/launch.sh"
