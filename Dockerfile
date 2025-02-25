# Docker for TokEngine

#
# Build stage
#
FROM maven:3.9.9-eclipse-temurin-23-alpine AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml clean install

# Run stage

FROM eclipse-temurin:23-jdk-alpine AS run
COPY --from=build /home/app/target/tokengine.jar /usr/local/lib/tokengine.jar

##### Expose ports. These can be mapped to host ports

# Convex binary protocol port
# EXPOSE 18888 

# HTTP port. Can be used for an HTTPS proxy
EXPOSE 8080  

# VOLUME ["/etc/ssl/certs"]
# VOLUME ["/etc/convex/keystore"]

ENTRYPOINT ["java", "-jar", "/usr/local/lib/tokengine.jar", "start"]

