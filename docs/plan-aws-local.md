# Plan: AWS Local con Floci

## Qué es Floci

[Floci](https://floci.io) es un emulador AWS gratuito, open-source (MIT) y ultra-liviano,
construido con Quarkus Native. Arranca en ~24 ms, consume ~13 MiB en idle y corre en un
único contenedor Docker en el puerto `4566` — compatible con el AWS SDK sin cambios de código.

---

## Servicios AWS que usaremos

| Servicio AWS       | Rol en el proyecto                                              |
|--------------------|-----------------------------------------------------------------|
| **API Gateway v2** | Punto de entrada único para el frontend; aplica JWT auth        |
| **Cognito**        | Proveedor de identidad para emitir y validar tokens JWT         |
| **SQS**            | Cola de mensajes (reemplaza RabbitMQ en task-creator/processor) |
| **RDS PostgreSQL** | Base de datos (reemplaza la conexión directa a PostgreSQL)      |
| **ECR**            | Registry de imágenes Docker para los tres microservicios        |
| **EKS**            | Cluster Kubernetes donde corren los microservicios              |

---

## Arquitectura local

```
Frontend SPA
    │
    │ HTTPS → JWT Bearer token
    ▼
┌─────────────────────────────────┐
│  Floci (Docker :4566)           │
│                                 │
│  ┌─────────────────────────┐    │
│  │  API Gateway v2 (HTTP)  │    │
│  │  JWT Authorizer         │    │
│  │  → Cognito User Pool    │    │
│  └────────┬────────────────┘    │
│           │ route /tasks*       │
│           ▼                     │
│  ┌─────────────────────────┐    │
│  │  SQS Queue              │    │
│  │  task-created-queue     │    │
│  └─────────────────────────┘    │
│                                 │
│  ┌─────────────────────────┐    │
│  │  RDS PostgreSQL         │    │
│  │  (Docker-backed)        │    │
│  └─────────────────────────┘    │
└─────────────────────────────────┘
    ▲                   ▲
    │ R2DBC             │ R2DBC
    │ SQS SDK           │ SQS SDK
┌───────────┐    ┌──────────────────┐
│  task-    │    │  task-processor  │
│  creator  │    │  (consumer SQS)  │
└───────────┘    └──────────────────┘
```

---

## Pasos para levantar el entorno local

### 1. Prerequisitos

Verificar que los siguientes comandos estén disponibles:

```bash
docker --version
terraform --version   # >= 1.6
aws --version
kubectl version --client   # >= 1.28
psql --version
python3 --version
```

### 2. Levantar Floci

```bash
./scripts/floci-start.sh
```

El script crea la red `floci-net`, arranca Floci y conecta `floci-ecr-registry` a esa
red. Ver `scripts/floci-start.sh` para el detalle de flags y puertos.

### 3. Aprovisionar la infraestructura con Terraform

Toda la infraestructura (SQS, RDS, Cognito, API Gateway) se gestiona como código en `terraform/`.

#### Estructura de archivos

```
terraform/
├── main.tf          # Provider AWS apuntando a Floci (:4566)
├── variables.tf     # Variables (región, contraseña RDS, etc.)
├── sqs.tf           # Cola task-created-queue
├── rds.tf           # Instancia RDS PostgreSQL
├── cognito.tf       # User Pool, App Client y usuario de prueba
├── api_gateway.tf   # API Gateway v2, JWT Authorizer y rutas /tasks
├── ecr.tf           # Repositorios de imágenes Docker (×3)
├── eks.tf           # Cluster EKS + node group
└── outputs.tf       # Exporta IDs/URLs para .env.local
```

#### Configuración del provider (`main.tf`)

El provider de AWS se redirige a Floci mediante `endpoints` y se deshabilitan las validaciones que requieren credenciales reales:

```hcl
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region                      = "us-east-1"
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true

  endpoints {
    sqs            = "http://localhost:4566"
    rds            = "http://localhost:4566"
    cognitoidentityprovider = "http://localhost:4566"
    apigatewayv2   = "http://localhost:4566"
  }
}
```

#### Inicializar y aplicar

```bash
cd terraform
terraform init
terraform apply -auto-approve
```

Al finalizar, `terraform output` muestra las URLs, IDs y credenciales. Para generar `.env.local`:

```bash
terraform output -json | python3 ../scripts/tf-to-env.py > ../.env.local
```

> `scripts/tf-to-env.py` lee los outputs de Terraform y los escribe como variables de entorno (`KEY=value`).

### 4. Publicar imágenes en ECR y desplegar en EKS

```bash
# Construir y subir las imágenes Docker a Floci ECR
./scripts/build-push.sh

# Configurar kubectl apuntando al cluster Floci EKS
source .env.local
aws eks update-kubeconfig \
  --endpoint-url http://localhost:4566 \
  --region us-east-1 \
  --name "$EKS_CLUSTER_NAME" \
  --no-cli-pager

# Desplegar todos los servicios en el cluster
./scripts/k8s-deploy.sh
```

`k8s-deploy.sh` conecta el contenedor k3s de Floci a `floci-net`, aplica los
manifiestos de `k8s/` y crea el secret de RDS apuntando a `floci-rds-taskdb:5432`
(nombre de contenedor resolvible dentro de la red compartida).

### 5. Verificar

```bash
# Cargar el JWT generado por el script
source .env.local

# Crear una tarea a través de API Gateway
curl -s -X POST \
  -H "Authorization: Bearer $ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Mi primera tarea","description":"Prueba local"}' \
  "$API_GATEWAY_URL/api/v1/tasks" | python3 -m json.tool

# Listar tareas
curl -s \
  -H "Authorization: Bearer $ID_TOKEN" \
  "$API_GATEWAY_URL/api/v1/tasks" | python3 -m json.tool
```

---

## Generar un nuevo token JWT

Terraform persiste los IDs de Cognito en `.env.local` pero no el token en sí (expira). Para obtener uno en cualquier momento:

```bash
# Cargar variables del entorno local
source .env.local

# Si es la primera vez, establecer contraseña permanente
# (Cognito crea el usuario en estado FORCE_CHANGE_PASSWORD)
aws --endpoint-url=http://localhost:4566 \
    --region=us-east-1 \
    --no-cli-pager \
    cognito-idp admin-set-user-password \
    --user-pool-id "$COGNITO_POOL_ID" \
    --username testuser \
    --password "Test1234!" \
    --permanent

# Solicitar token a Cognito (Floci)
ID_TOKEN=$(aws --endpoint-url=http://localhost:4566 \
               --region=us-east-1 \
               --no-cli-pager \
               cognito-idp initiate-auth \
               --auth-flow USER_PASSWORD_AUTH \
               --client-id "$COGNITO_CLIENT_ID" \
               --auth-parameters "USERNAME=testuser,PASSWORD=Test1234!" \
               --query 'AuthenticationResult.IdToken' \
               --output text)

echo "Token: $ID_TOKEN"
```

Con el token en la variable `$ID_TOKEN` puedes usar directamente los `curl` de la sección **Verificar**.

> **Credenciales del usuario de prueba**
> - Usuario: `testuser`
> - Contraseña: `Test1234!`
>
> Estas credenciales las crea Terraform en el recurso `cognito.tf`.

---

## Referencias

- [Floci — Sitio oficial](https://floci.io)
- [Quick Start - Floci](https://floci.io/floci/getting-started/quick-start/)
- [Migrate from LocalStack](https://floci.io/floci/getting-started/migrate-from-localstack/)
- [Services Overview](https://floci.io/floci/services/)
- [Terraform AWS Provider — custom endpoints](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/guides/custom-service-endpoints)
- [Terraform AWS Provider — getting started](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
