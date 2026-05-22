#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/../k8s"

source "$SCRIPT_DIR/../.env.local"

K3S_CONTAINER=$(docker ps --format '{{.Names}}' | grep floci-eks || true)
if [[ -z "$K3S_CONTAINER" ]]; then
  echo "Error: no se encontró el contenedor k3s. Ejecutar terraform apply primero."
  exit 1
fi

# Conectar k3s a floci-net para que los pods alcancen los servicios de Floci
docker network connect floci-net "$K3S_CONTAINER" 2>/dev/null || true
echo "k3s en floci-net: $K3S_CONTAINER"

# Configurar registry mirror: redirige pulls de la URL ECR a floci-ecr-registry:5000
# (desde dentro del cluster, localhost:5000 apunta al k3s mismo, no al host)
REGISTRY_YAML="/etc/rancher/k3s/registries.yaml"
if ! docker exec "$K3S_CONTAINER" grep -q "floci-ecr-registry" "$REGISTRY_YAML" 2>/dev/null; then
  echo "Configurando registry mirror en k3s..."
  docker exec "$K3S_CONTAINER" mkdir -p /etc/rancher/k3s
  docker exec -i "$K3S_CONTAINER" sh -c "cat > $REGISTRY_YAML" << 'YAML'
mirrors:
  "000000000000.dkr.ecr.us-east-1.localhost:5000":
    endpoint:
      - "http://floci-ecr-registry:5000"
configs:
  "floci-ecr-registry:5000":
    tls:
      insecure_skip_verify: true
YAML
  echo "Reiniciando k3s para aplicar el mirror..."
  docker restart "$K3S_CONTAINER"
  sleep 10
  docker network connect floci-net "$K3S_CONTAINER" 2>/dev/null || true
  echo "Esperando que el API server esté listo..."
  until kubectl --kubeconfig="$HOME/.kube/config-floci-eks" get nodes >/dev/null 2>&1; do sleep 2; done
fi

# Extraer kubeconfig real del k3s (el de aws eks update-kubeconfig usa token AWS inválido)
K3S_KUBECONFIG="$HOME/.kube/config-floci-eks"
docker exec "$K3S_CONTAINER" cat /etc/rancher/k3s/k3s.yaml \
  | sed 's|https://127.0.0.1:6443|https://localhost:6500|g' \
  > "$K3S_KUBECONFIG"
chmod 600 "$K3S_KUBECONFIG"
export KUBECONFIG="$K3S_KUBECONFIG"

kubectl apply -f "$K8S_DIR/namespace.yaml"
kubectl apply -f "$K8S_DIR/configmap.yaml"

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
