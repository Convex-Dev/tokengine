## Docker build

To build a Tokengine container using docker in the current directory from the provided `Dockerfile`

```bash
docker build -t convexlive/tokengine:latest .
```

Deploy to docker hub:

```bash
docker login -u "convexlive" docker.io
docker push convexlive/tokengine
```