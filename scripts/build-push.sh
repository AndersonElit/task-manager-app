#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../.env.local"

FLOCI_ENDPOINT="$AWS_ENDPOINT_URL"
SHA=$(git rev-parse --short HEAD)

# Obtener el host del registry desde la repositoryUri de Floci
REGISTRY_HOST=$(aws --endpoint-url="$FLOCI_ENDPOINT" \
  --region="$AWS_REGION" \
  --no-cli-pager \
  ecr describe-repositories \
  --query 'repositories[0].repositoryUri' \
  --output text | cut -d'/' -f1)

# Autenticar Docker con Floci ECR
aws ecr get-login-password \
  --endpoint-url="$FLOCI_ENDPOINT" \
  --region="$AWS_REGION" \
  --no-cli-pager \
  | docker login --username AWS --password-stdin "$REGISTRY_HOST"

build_and_push() {
  local context=$1
  local repo=$2
  local url="$REGISTRY_HOST/$repo"

  echo "==> Construyendo $repo..."
  docker build -t "$url:latest" -t "$url:$SHA" "$context"

  echo "==> Subiendo $repo..."
  docker push "$url:latest"
  docker push "$url:$SHA"
}

build_and_push "backend/tasks-creator"   "task-manager/tasks-creator"
build_and_push "backend/tasks-processor" "task-manager/tasks-processor"
build_and_push "frontend"                "task-manager/frontend"

echo ""
echo "Imágenes publicadas en Floci ECR ($REGISTRY_HOST):"
echo "  task-manager/tasks-creator:$SHA"
echo "  task-manager/tasks-processor:$SHA"
echo "  task-manager/frontend:$SHA"
