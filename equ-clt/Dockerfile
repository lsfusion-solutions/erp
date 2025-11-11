FROM amazoncorretto:21-alpine-jdk

ARG PLATFORM_VERSION

WORKDIR /equ

ADD http://jenkins.lsfusion.luxsoft.by/download/equ-clt/$PLATFORM_VERSION/equ-clt-server-1.0.0-SNAPSHOT.jar equ-clt-server.jar

COPY conf conf
RUN sed -i 's/localhost/${APP_SERVER}/g' conf/settings.xml

COPY entrypoint.sh /
RUN chmod +x /entrypoint.sh

WORKDIR /lsfusion

ENTRYPOINT ["/entrypoint.sh"]