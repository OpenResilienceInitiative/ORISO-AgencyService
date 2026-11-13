FROM eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
VOLUME ["/tmp","/log"]
EXPOSE 8084
ARG JAR_FILE
ENV JAVA_UPPER_VERSION=eclipse-temurin:21-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
# The Ubuntu-based Temurin image ships Canonical's pebble service manager,
# which is unused here and carries fixable Go CVEs; drop it from the image.
RUN rm -f /usr/bin/pebble
# This base digest already ships the Ubuntu fixes for OpenSSL 3.5.5-1ubuntu3.7,
# curl/libcurl4t64 8.18.0-1ubuntu2.8 and libbrotli1 1.2.0-3ubuntu0.1
# (CVE-2026-111497). apt/libapt-pkg7.0 still need the fix for CVE-2026-109518
# (Ubuntu USN-8996-1). Disable APT retries during the upgrade because the old
# APT is vulnerable when it retries downloads.
RUN apt-get -o Acquire::Retries=0 update \
    && apt-get -o Acquire::Retries=0 install -y --no-install-recommends --only-upgrade apt libapt-pkg7.0 \
    && rm -rf /var/lib/apt/lists/*
COPY ./target/AgencyService.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dtomcat.util.http.parser.HttpParser.requestTargetAllow=|{}","-XX:MaxRAMPercentage=75","-jar","/app.jar"]
