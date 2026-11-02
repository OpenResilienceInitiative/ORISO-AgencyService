FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
VOLUME ["/tmp","/log"]
EXPOSE 8084
ARG JAR_FILE
ENV JAVA_UPPER_VERSION=eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
# The Ubuntu-based Temurin image ships Canonical's pebble service manager,
# which is unused here and carries fixable Go CVEs; drop it from the image.
RUN rm -f /usr/bin/pebble
# This base digest already ships the Ubuntu fixes the image CVE gate needs
# (OpenSSL 3.5.5-1ubuntu3.7, curl/libcurl4t64 8.18.0-1ubuntu2.8,
# libbrotli1 1.2.0-3ubuntu0.1 for CVE-2026-111497), so no package upgrades here.
COPY ./target/AgencyService.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dtomcat.util.http.parser.HttpParser.requestTargetAllow=|{}","-XX:MaxRAMPercentage=75","-jar","/app.jar"]
