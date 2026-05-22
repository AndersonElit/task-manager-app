# Plan: Cluster Kubernetes en AWS EKS (vía Floci)

## Cómo funciona EKS en Floci

Floci emula EKS levantando un contenedor k3s real por cada cluster que se crea.
El API server de Kubernetes queda expuesto en un puerto del host (típicamente 6443).
El control plane (crear cluster, obtener kubeconfig) se expone en `localhost:4566`
igual que el resto de servicios.

Al igual que con ECR, el contenedor k3s que crea Floci necesita estar en la red
`floci-net` para que las aplicaciones puedan alcanzar los servicios de Floci
(SQS, RDS, ECR) desde dentro del cluster. Esto requiere el mismo tratamiento:
excluir el puerto 6443 del rango de `floci` y conectar el contenedor k3s a la red
después de crearlo.

---

## Prerrequisitos

```bash
kubectl version --client   # >= 1.28
```

---

## Infraestructura EKS — terraform/eks.tf

```hcl
resource "aws_eks_cluster" "main" {
  name     = "task-manager"
  role_arn = "arn:aws:iam::000000000000:role/eks-cluster-role"

  vpc_config {
    subnet_ids = ["subnet-00000000"]
  }
}

resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "task-manager-nodes"
  node_role_arn   = "arn:aws:iam::000000000000:role/eks-node-role"
  subnet_ids      = ["subnet-00000000"]

  scaling_config {
    desired_size = 2
    min_size     = 1
    max_size     = 3
  }

  instance_types = ["t3.medium"]
}

output "eks_cluster_name" {
  value = aws_eks_cluster.main.name
}

output "eks_cluster_endpoint" {
  value = aws_eks_cluster.main.endpoint
}
```

> Floci no valida los ARNs de IAM ni las subnets — los valores son placeholders
> para mantener la estructura idéntica a producción.

Aplicar:

```bash
cd terraform && terraform apply -auto-approve
```

---

## Configurar kubectl

Después de `terraform apply`, obtener el kubeconfig desde Floci:

```bash
aws eks update-kubeconfig \
  --endpoint-url http://localhost:4566 \
  --region us-east-1 \
  --name task-manager \
  --no-cli-pager
```

Verificar:

```bash
kubectl get nodes
```

---

## Conectar el cluster k3s a floci-net

Floci crea el cluster k3s como un contenedor Docker. Al igual que `floci-ecr-registry`,
ese contenedor necesita estar en `floci-net` para que los pods puedan alcanzar
los servicios de Floci.

```bash
# El nombre del contenedor k3s lo asigna Floci (patrón: floci-eks-<cluster>)
K3S_CONTAINER=$(docker ps --format '{{.Names}}' | grep floci-eks)

docker network connect floci-net "$K3S_CONTAINER"
```

> Si falla con "port already allocated" en el 6443, actualizar `scripts/floci-start.sh`
> agregando 6443 a los puertos excluidos del rango (ver sección al final del plan).

---

## Manifiestos Kubernetes

Estructura de archivos:

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── tasks-creator/
│   ├── deployment.yaml
│   └── service.yaml
├── tasks-processor/
│   ├── deployment.yaml
│   └── service.yaml
└── frontend/
    ├── deployment.yaml
    └── service.yaml
```

### namespace.yaml

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: task-manager
```

### configmap.yaml

Centraliza los endpoints de Floci para todos los servicios. Dentro del cluster k3s,
Floci es alcanzable por el nombre del contenedor en `floci-net`.

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: floci-config
  namespace: task-manager
data:
  AWS_ENDPOINT_URL: "http://floci:4566"
  AWS_REGION: "us-east-1"
  SQS_QUEUE_URL: "http://floci:4566/000000000000/task-created-queue"
```

> `R2DBC_URL` se obtiene de `terraform output` y varía según el puerto que Floci
> asigne dinámicamente al contenedor PostgreSQL. Agregarlo como Secret (ver abajo).

### Secret con credenciales de RDS

```bash
# Leer puerto RDS del .env.local generado por terraform output
source .env.local

kubectl create secret generic rds-credentials \
  --namespace task-manager \
  --from-literal=R2DBC_URL="$R2DBC_URL" \
  --from-literal=DB_USERNAME=admin \
  --from-literal=DB_PASSWORD=secret123
```

### tasks-creator/deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tasks-creator
  namespace: task-manager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: tasks-creator
  template:
    metadata:
      labels:
        app: tasks-creator
    spec:
      containers:
        - name: tasks-creator
          image: 000000000000.dkr.ecr.us-east-1.localhost:5000/task-manager/tasks-creator:latest
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: floci-config
            - secretRef:
                name: rds-credentials
```

### tasks-creator/service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tasks-creator
  namespace: task-manager
spec:
  selector:
    app: tasks-creator
  ports:
    - port: 8080
      targetPort: 8080
```

### tasks-processor/deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tasks-processor
  namespace: task-manager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: tasks-processor
  template:
    metadata:
      labels:
        app: tasks-processor
    spec:
      containers:
        - name: tasks-processor
          image: 000000000000.dkr.ecr.us-east-1.localhost:5000/task-manager/tasks-processor:latest
          ports:
            - containerPort: 8081
          envFrom:
            - configMapRef:
                name: floci-config
            - secretRef:
                name: rds-credentials
```

### tasks-processor/service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: tasks-processor
  namespace: task-manager
spec:
  selector:
    app: tasks-processor
  ports:
    - port: 8081
      targetPort: 8081
```

### frontend/deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: task-manager
spec:
  replicas: 1
  selector:
    matchLabels:
      app: frontend
  template:
    metadata:
      labels:
        app: frontend
    spec:
      containers:
        - name: frontend
          image: 000000000000.dkr.ecr.us-east-1.localhost:5000/task-manager/frontend:latest
          ports:
            - containerPort: 80
```

### frontend/service.yaml

```yaml
apiVersion: v1
kind: Service
metadata:
  name: frontend
  namespace: task-manager
spec:
  type: NodePort
  selector:
    app: frontend
  ports:
    - port: 80
      targetPort: 80
      nodePort: 30080
```

---

## Script de despliegue — scripts/k8s-deploy.sh

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K8S_DIR="$SCRIPT_DIR/../k8s"

source "$SCRIPT_DIR/../.env.local"

# Conectar el contenedor k3s a floci-net si no está conectado
K3S_CONTAINER=$(docker ps --format '{{.Names}}' | grep floci-eks || true)
if [[ -n "$K3S_CONTAINER" ]]; then
  docker network connect floci-net "$K3S_CONTAINER" 2>/dev/null || true
fi

kubectl apply -f "$K8S_DIR/namespace.yaml"
kubectl apply -f "$K8S_DIR/configmap.yaml"

kubectl create secret generic rds-credentials \
  --namespace task-manager \
  --from-literal=R2DBC_URL="$R2DBC_URL" \
  --from-literal=DB_USERNAME=admin \
  --from-literal=DB_PASSWORD=secret123 \
  --save-config --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "$K8S_DIR/tasks-creator/"
kubectl apply -f "$K8S_DIR/tasks-processor/"
kubectl apply -f "$K8S_DIR/frontend/"

echo "Despliegue completado."
kubectl get pods -n task-manager
```

---

## Conflicto de puerto 6443 en floci-start.sh

Si al ejecutar `docker network connect floci-net <k3s-container>` aparece el error
`Bind for :::6443 failed: port is already allocated`, actualizar el rango en
`scripts/floci-start.sh` para excluir también el 6443:

```bash
-p 5000-5099:5000-5099 \
-p 5101-6442:5101-6442 \
-p 6444-8000:6444-8000 \
```

---

## Pasos en orden

1. `./scripts/floci-start.sh` (Floci con la red correcta)
2. `cd terraform && terraform apply -auto-approve`
3. `terraform output -json | python3 ../scripts/tf-to-env.py > ../.env.local`
4. `./scripts/build-push.sh` (imágenes en Floci ECR)
5. Configurar kubectl: `aws eks update-kubeconfig --endpoint-url http://localhost:4566 --region us-east-1 --name task-manager --no-cli-pager`
6. `./scripts/k8s-deploy.sh`
7. Verificar: `kubectl get pods -n task-manager`

---

## Referencias

- [Floci — EKS](https://floci.io/floci/services/)
- [Terraform aws_eks_cluster](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/eks_cluster)
- [Terraform aws_eks_node_group](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/eks_node_group)
