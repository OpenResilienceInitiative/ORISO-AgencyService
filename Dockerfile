FROM eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3
VOLUME ["/tmp","/log"]
EXPOSE 8084
ARG JAR_FILE
ENV JAVA_UPPER_VERSION=eclipse-temurin:21-jre@sha256:273396ed5998598ed1091e8d72711c2d36980a0e65103859c55a4e977a41ffd3
# The Ubuntu-based Temurin image ships Canonical's pebble service manager,
# which is unused here and carries fixable Go CVEs; drop it from the image.
RUN rm -f /usr/bin/pebble
# The pinned Temurin base still ships OpenSSL 3.5.5-1ubuntu3.2; Canonical's 3.5.5-1ubuntu3.6
# fixes CVE-2026-84782, which fails the image CVE gate. curl/libcurl4t64 need the
# Ubuntu fix for CVE-2026-92392 for the same reason. Upgrade only these packages.
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade libssl3t64 openssl openssl-provider-legacy curl libcurl4t64 \
    && rm -rf /var/lib/apt/lists/*
COPY ./target/AgencyService.jar app.jar
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-Dtomcat.util.http.parser.HttpParser.requestTargetAllow=|{}","-XX:MaxRAMPercentage=75","-jar","/app.jar"]
