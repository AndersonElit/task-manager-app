# Plan: Dockerización y Amazon ECR (vía Floci)

## Qué haremos

Crear imágenes Docker optimizadas (multi-stage) para los tres servicios y almacenarlas
en ECR emulado por Floci. Los repositorios ECR se agregan al mismo `terraform/`
existente. Floci soporta ECR de forma nativa usando un contenedor `registry:2` interno,
pero requiere que Docker esté configurado con `insecure-registries` y que Floci arranque
en una red Docker compartida (`floci-net`) para que el proxy hacia el registry funcione.

---

## Cómo funciona ECR en Floci

Floci levanta un contenedor `registry:2` (`floci-ecr-registry`) para servir el
protocolo Docker Registry v2. El control plane (crear repos, obtener credenciales)
se expone en `localhost:4566` igual que el resto de servicios. La URI que devuelve
`aws ecr describe-repositories` apunta al registry interno de Floci en el puerto 5000.

Para que el proxy de Floci hacia `floci-ecr-registry` funcione, ambos contenedores
deben estar en la misma red Docker. Esto se logra arrancando Floci con
`--network floci-net -e DOCKER_NETWORK=floci-net` (ver `plan-aws-local.md`, paso 2).

Además, Docker debe tener el registry configurado como insecure (usa HTTP, no HTTPS).

### Prerequisito: daemon.json

Agregar a `/etc/docker/daemon.json` (crear el archivo si no existe):

```json
{
  "insecure-registries": ["000000000000.dkr.ecr.us-east-1.localhost:5000"]
}
```

Luego reiniciar el daemon de Docker:

```bash
sudo systemctl restart docker
```

> Si ya hay contenido en `daemon.json`, agregar solo la clave `insecure-registries`
> al objeto existente, sin sobrescribir el resto.

---

## Imágenes a construir

| Imagen                          | Base runtime                  | Artefacto                                            |
|---------------------------------|-------------------------------|------------------------------------------------------|
| `task-manager/tasks-creator`    | eclipse-temurin:21-jre-alpine | `infrastructure/entry-points/app/target/*.jar`       |
| `task-manager/tasks-processor`  | eclipse-temurin:21-jre-alpine | `infrastructure/entry-points/app/target/*.jar`       |
| `task-manager/frontend`         | nginx:alpine                  | `dist/` generado por Vite                            |

---

## Estructura de archivos a crear

```
backend/tasks-creator/
└── Dockerfile

backend/tasks-processor/
└── Dockerfile

frontend/
├── Dockerfile
└── nginx.conf

terraform/
└── ecr.tf           # aws_ecr_repository × 3 (se agrega al terraform/ existente)

scripts/
└── build-push.sh    # Build + tag + push de las 3 imágenes a Floci ECR
```

---

## Dockerfiles

### tasks-creator y tasks-processor

Build multi-stage: Maven compila el fat JAR en el stage 1; el stage 2 copia
sólo el JAR a una imagen JRE mínima (~200 MB vs ~500 MB con JDK completo).

```dockerfile
# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn package -DskipTests --no-transfer-progress

# Stage 2 — runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/infrastructure/entry-points/app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### frontend

Build multi-stage: Node construye el bundle estático con Vite; Nginx lo sirve.
El `nginx.conf` redirige cualquier ruta desconocida a `index.html` (requerido
por React Router).

```dockerfile
# Stage 1 — build
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2 — serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

**nginx.conf**
```nginx
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

---

## Repositorios ECR — terraform/ecr.tf

Se agrega al `terraform/` existente. El provider ya está configurado con el
endpoint de Floci en `main.tf`, por lo que los recursos ECR también se crean
en Floci sin ningún cambio extra.

```hcl
locals {
  ecr_repos = [
    "task-manager/tasks-creator",
    "task-manager/tasks-processor",
    "task-manager/frontend",
  ]
}

resource "aws_ecr_repository" "repos" {
  for_each = toset(local.ecr_repos)

  name                 = each.key
  image_tag_mutability = "MUTABLE"
}

output "ecr_repository_urls" {
  value = {
    for name, repo in aws_ecr_repository.repos : name => repo.repository_url
  }
}
```

> `image_scanning_configuration` se omite porque Floci no implementa el escaneo
> de imágenes (es una feature de AWS real). En producción se puede agregar.

**Aplicar:**
```bash
cd terraform
terraform apply -auto-approve
```

---

## Script build-push.sh

Lee los outputs de Terraform para obtener las URLs de los repositorios Floci,
autentica el cliente Docker, construye las imágenes y las sube con tag `latest`
y SHA del commit.

```bash
#!/usr/bin/env bash
set -euo pipefail

FLOCI_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
SHA=$(git rev-parse --short HEAD)

# Obtener el host del registry (viene en la repositoryUri de Floci)
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
  local context=$1   # directorio fuente
  local repo=$2      # nombre del repositorio

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
```

**Uso:**
```bash
chmod +x scripts/build-push.sh
./scripts/build-push.sh
```

---

## Variables de entorno en contenedores

Cuando los contenedores Spring Boot corren **dentro de Docker** y necesitan
comunicarse con Floci, deben usar `host.docker.internal:4566` en lugar de
`localhost:4566` (dentro de un contenedor, `localhost` apunta al propio
contenedor, no al host).

| Variable          | tasks-creator | tasks-processor | Valor local (Docker)                          |
|-------------------|:---:|:---:|-----------------------------------------------|
| `R2DBC_URL`       | ✓   | ✓   | `r2dbc:postgresql://host.docker.internal:<puerto-rds>/<db>` |
| `DB_USERNAME`     | ✓   | ✓   | `admin`                                       |
| `DB_PASSWORD`     | ✓   | ✓   | `secret123`                                   |
| `SQS_QUEUE_URL`   | ✓   | ✓   | `http://host.docker.internal:4566/000000000000/task-created-queue` |
| `AWS_ENDPOINT_URL`| ✓   | ✓   | `http://host.docker.internal:4566`            |
| `AWS_REGION`      | ✓   | ✓   | `us-east-1`                                   |
| `SERVER_PORT`     | ✓   | ✓   | `8080` / `8081`                               |

> En Linux `host.docker.internal` no se resuelve automáticamente. Al hacer
> `docker run` hay que agregar `--add-host=host.docker.internal:host-gateway`
> (el mismo flag que ya usa el contenedor de Floci).

---

## Pasos en orden

1. Configurar `/etc/docker/daemon.json` con `insecure-registries` y reiniciar Docker
2. Levantar Floci con `--network floci-net -e DOCKER_NETWORK=floci-net` (ver `plan-aws-local.md`, paso 2)
3. Agregar `terraform/ecr.tf` y aplicar: `terraform apply -auto-approve`
4. Crear `backend/tasks-creator/Dockerfile`
5. Crear `backend/tasks-processor/Dockerfile`
6. Crear `frontend/Dockerfile` y `frontend/nginx.conf`
7. Crear `scripts/build-push.sh`
8. Ejecutar: `./scripts/build-push.sh`

---

## Referencias

- [Floci — Services](https://floci.io/floci/services/)
- [Terraform aws_ecr_repository](https://registry.terraform.io/providers/hashicorp/aws/latest/docs/resources/ecr_repository)
- [Spring Boot Docker best practices](https://spring.io/guides/topicals/spring-boot-docker)
- [Vite — Building for production](https://vitejs.dev/guide/build)
