#!/usr/bin/env bash
set -euo pipefail

docker network create floci-net 2>/dev/null || true

docker rm -f floci floci-ecr-registry 2>/dev/null || true

docker run -d \
  --name floci \
  --network floci-net \
  -p 4566:4566 \
  -p 5000-5099:5000-5099 \
  -p 5101-6442:5101-6442 \
  -p 6444-8000:6444-8000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  -e DOCKER_NETWORK=floci-net \
  floci/floci:latest

echo "Esperando que Floci cree floci-ecr-registry..."
until docker inspect floci-ecr-registry >/dev/null 2>&1; do sleep 1; done

docker network connect floci-net floci-ecr-registry
echo "Floci listo. floci-ecr-registry conectado a floci-net."
