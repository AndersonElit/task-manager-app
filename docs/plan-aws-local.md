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
aws --version
psql --version
python3 --version
```

### 2. Levantar Floci

RDS necesita acceso al socket de Docker para crear contenedores reales de PostgreSQL.

```bash
docker run -d \
  --name floci \
  -p 4566:4566 \
  -p 5000-9000:5000-9000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --add-host=host.docker.internal:host-gateway \
  floci/floci:latest
```

| Flag | Por qué |
|---|---|
| `-p 4566:4566` | Puerto único para todos los servicios AWS emulados |
| `-p 5000-9000` | Rango amplio para los puertos que Floci asigna dinámicamente a RDS |
| `-v /var/run/docker.sock` | Permite a Floci crear el contenedor PostgreSQL de RDS |
| `--add-host=host.docker.internal:host-gateway` | En Linux, `host.docker.internal` no se resuelve automáticamente; esto lo mapea al host |

### 3. Ejecutar el script de aprovisionamiento

El script `scripts/setup-local.sh` automatiza todo lo siguiente:
- Crear la cola SQS `task-created-queue`
- Crear la instancia RDS PostgreSQL y aplicar `docs/model.sql`
- Crear el Cognito User Pool, App Client y usuario de prueba
- Crear el API Gateway v2 con JWT Authorizer y rutas `/tasks`
- Generar el archivo `.env.local` con todas las variables listas

```bash
./scripts/setup-local.sh
```

Al finalizar el script imprime un resumen con las URLs, IDs y el JWT de prueba.

### 4. Levantar los microservicios

```bash
# Terminal 1 — task-creator (puerto 8080)
cd backend/tasks-creator
mvn install -DskipTests && mvn spring-boot:run -pl infrastructure/entry-points/app

# Terminal 2 — task-processor (puerto 8081)
cd backend/tasks-processor
 mvn install -DskipTests && mvn spring-boot:run -pl infrastructure/entry-points/app
```

Ambos servicios leen las variables de entorno desde `.env.local` generado por el script.

### 5. Verificar

```bash
# Cargar el JWT generado por el script
source .env.local

# Crear una tarea a través de API Gateway
curl -s -X POST \
  -H "Authorization: Bearer $ID_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Mi primera tarea","description":"Prueba local"}' \
  "$API_GATEWAY_URL/tasks" | python3 -m json.tool

# Listar tareas
curl -s \
  -H "Authorization: Bearer $ID_TOKEN" \
  "$API_GATEWAY_URL/tasks" | python3 -m json.tool
```

---

## Generar un nuevo token JWT

El script `setup-local.sh` genera el token durante la ejecución pero no lo persiste en `.env.local`. Para obtener un token en cualquier momento:

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
> Estas credenciales las crea el script en el paso de Cognito.

---

## Referencias

- [Floci — Sitio oficial](https://floci.io)
- [Quick Start - Floci](https://floci.io/floci/getting-started/quick-start/)
- [Migrate from LocalStack](https://floci.io/floci/getting-started/migrate-from-localstack/)
- [Services Overview](https://floci.io/floci/services/)
