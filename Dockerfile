FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3
VOLUME ["/tmp","/log"]
EXPOSE 8084
ARG JAR_FILE
ENV JAVA_UPPER_VERSION=eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3
COPY ./target/AgencyService.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dtomcat.util.http.parser.HttpParser.requestTargetAllow=|{}","-XX:MaxRAMPercentage=75","-jar","/app.jar"]
