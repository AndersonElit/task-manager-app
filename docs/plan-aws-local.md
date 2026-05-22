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
  -p 5400-5420:5400-5420 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  floci/floci:latest
```

| Flag | Por qué |
|---|---|
| `-p 4566:4566` | Puerto único para todos los servicios AWS emulados |
| `-p 5400-5420` | Rango de puertos que Floci expone para instancias RDS |
| `-v /var/run/docker.sock` | Permite a Floci crear el contenedor PostgreSQL de RDS |

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
./mvnw spring-boot:run -pl infrastructure/entry-points/app

# Terminal 2 — task-processor (puerto 8081)
cd backend/tasks-processor
./mvnw spring-boot:run -pl infrastructure/entry-points/app
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

## Referencias

- [Floci — Sitio oficial](https://floci.io)
- [Quick Start - Floci](https://floci.io/floci/getting-started/quick-start/)
- [Migrate from LocalStack](https://floci.io/floci/getting-started/migrate-from-localstack/)
- [Services Overview](https://floci.io/floci/services/)
