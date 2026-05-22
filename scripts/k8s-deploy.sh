#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/../k8s"

source "$SCRIPT_DIR/../.env.local"

# Conectar el contenedor k3s a floci-net para que los pods alcancen los servicios de Floci
K3S_CONTAINER=$(docker ps --format '{{.Names}}' | grep floci-eks || true)
if [[ -n "$K3S_CONTAINER" ]]; then
  docker network connect floci-net "$K3S_CONTAINER" 2>/dev/null || true
  echo "k3s conectado a floci-net: $K3S_CONTAINER"
fi

kubectl apply -f "$K8S_DIR/namespace.yaml"
kubectl apply -f "$K8S_DIR/configmap.yaml"

# RDS accesible por nombre de contenedor en floci-net desde dentro del cluster
kubectl create secret generic rds-credentials \
  --namespace task-manager \
  --from-literal=R2DBC_URL="r2dbc:postgresql://floci-rds-taskdb:5432/${DB_NAME:-taskmanager}" \
  --from-literal=DB_USERNAME="$DB_USERNAME" \
  --from-literal=DB_PASSWORD="$DB_PASSWORD" \
  --save-config --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$K8S_DIR/tasks-creator/"
kubectl apply -f "$K8S_DIR/tasks-processor/"
kubectl apply -f "$K8S_DIR/frontend/"

echo ""
echo "Despliegue completado."
kubectl get pods -n task-manager
