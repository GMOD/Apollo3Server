# Apollo3.X
FROM ubuntu:18.04
MAINTAINER Nathan Dunn <nathandunn@lbl.gov>
ENV DEBIAN_FRONTEND noninteractive

# where bin directories are
ENV CATALINA_HOME /usr/share/tomcat9
# where webapps are deployed
ENV CATALINA_BASE /var/lib/tomcat9
ENV CONTEXT_PATH ROOT
ENV JAVA_HOME /usr/lib/jvm/java-11-openjdk-amd64

RUN apt-get -qq update --fix-missing && \
	apt-get --no-install-recommends -y install \
	git build-essential libpq-dev wget python3-pip net-tools less \
	lsb-release gnupg2 wget xmlstarlet netcat libpng-dev  \
	zlib1g-dev libexpat1-dev curl ssl-cert zip unzip openjdk-11-jdk-headless

RUN wget -O - https://debian.neo4j.com/neotechnology.gpg.key | apt-key add -
RUN echo 'deb https://debian.neo4j.com stable 3.5' | tee -a /etc/apt/sources.list.d/neo4j.list
RUN apt-get update
RUN apt list -a neo4j



RUN apt-get -qq update --fix-missing && \
	apt-get --no-install-recommends -y install \
	tomcat9 neo4j=1:3.5.21 && \
	apt-get autoremove -y && apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /apollo/

RUN curl -s "http://hgdownload.soe.ucsc.edu/admin/exe/linux.x86_64/blat/blat" -o /usr/local/bin/blat && \
 		chmod +x /usr/local/bin/blat && \
 		curl -s "http://hgdownload.soe.ucsc.edu/admin/exe/linux.x86_64/faToTwoBit" -o /usr/local/bin/faToTwoBit && \
 		chmod +x /usr/local/bin/faToTwoBit

RUN useradd -ms /bin/bash -d /apollo apollo
COPY gradlew /apollo
COPY gradle.properties /apollo
COPY gradle /apollo/gradle
COPY grails-app /apollo/grails-app
COPY src /apollo/src
COPY src/main/scripts /apollo/scripts
ADD grails* /apollo/
COPY build.gradle /apollo/build.gradle
ADD settings.gradle /apollo
RUN ls /apollo




# install grails and python libraries
USER apollo

ENV LC_CTYPE en_US.UTF-8
#ENV LC_ALL en_US.UTF-8
ENV LANG en_US.UTF-8
ENV LANGUAGE=en_US.UTF-8

RUN pip3 install setuptools
RUN pip3 install wheel
RUN pip3 install nose apollo==4.2.7




USER root
#COPY docker-files/build.sh /bin/build.sh
#RUN ["chmod", "+x", "/bin/build.sh"]
#ADD docker-files/docker-apollo-config.groovy /apollo/apollo-config.groovy
ADD docker-files/docker.apollo.yml /apollo/apollo.yml
RUN chown -R apollo:apollo /apollo
RUN mkdir -p /data/apollo_data
RUN chown -R apollo:apollo /data/apollo_data




USER apollo
WORKDIR /apollo
RUN ./grailsw clean && rm -rf build/* && ./grailsw war
RUN cp /apollo/build/libs/*.war /tmp/apollo.war && rm -rf /apollo/ || true
#RUN cp /apollo/build/libs/*.war /tmp/apollo.war
RUN mv /tmp/apollo.war /apollo/apollo.war



USER root
##RUN /bin/build.sh
## remove from webapps and copy it into a staging directory
RUN rm -rf ${CATALINA_BASE}/webapps/* && \
	cp /apollo/apollo*.war ${CATALINA_BASE}/apollo.war
#
#ADD docker-files/createenv.sh /createenv.sh
ADD docker-files/launch.sh /launch.sh

#USER apollo
#WORKDIR /apollo

#USER root
#RUN ${CATALINA_HOME}/bin/catalina.sh stop 5 -force
#RUN ${CATALINA_HOME}/bin/catalina.sh run

CMD "/launch.sh"
