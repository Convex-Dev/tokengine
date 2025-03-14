# Tokengine build Instructions

## Maven Build

Requires relatively Maven recent version e.g. 3.9.4

Run in CLI (bash / Powershell) in tokengine root directory.

```
mvn clean install
```

## Docker build

To build a Tokengine container using docker in the current directory from the provided `Dockerfile`

```bash
docker build -t convexlive/tokengine:latest .
```

This internally builds using Maven. Any dependencies required should be published in Maven Central.

## Docker Deployment

Deploy to docker hub:

```bash
docker login -u "convexlive" docker.io
docker push convexlive/tokengine
```