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

## 1. Levantar Floci en un contenedor Docker

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

Verificar que está corriendo:

```bash
curl http://localhost:4566/_floci/health
```

---

## 2. Configurar AWS CLI para apuntar a Floci

```bash
aws configure set aws_access_key_id     test
aws configure set aws_secret_access_key test
aws configure set default.region        us-east-1
aws configure set default.output        json

# Alias para no repetir --endpoint-url
alias awsf='aws --endpoint-url=http://localhost:4566'
```

---

## 3. Aprovisionar SQS

```bash
# Cola estándar para eventos task.created
awsf sqs create-queue --queue-name task-created-queue

# Verificar
awsf sqs list-queues
```

URL de la cola resultante:
`http://localhost:4566/000000000000/task-created-queue`

---

## 4. Aprovisionar RDS (PostgreSQL)

```bash
awsf rds create-db-instance \
  --db-instance-identifier taskdb \
  --db-instance-class      db.t3.micro \
  --engine                 postgres \
  --master-username        admin \
  --master-user-password   secret123 \
  --allocated-storage      20

# Esperar a que el estado sea "available"
awsf rds describe-db-instances \
  --db-instance-identifier taskdb \
  --query 'DBInstances[0].DBInstanceStatus'
```

Floci crea un contenedor Docker real de PostgreSQL y proxy el puerto en el rango `5400-5420`.
Obtener el endpoint:

```bash
awsf rds describe-db-instances \
  --db-instance-identifier taskdb \
  --query 'DBInstances[0].Endpoint'
```

Aplicar el modelo de base de datos:

```bash
psql -h localhost -p <puerto-rds> -U admin -d postgres \
  -f docs/model.sql
```

---

## 5. Aprovisionar Cognito (emisor JWT)

```bash
# Crear User Pool
awsf cognito-idp create-user-pool \
  --pool-name task-manager-pool \
  --query 'UserPool.Id' --output text

# Crear App Client (sin secreto para SPA)
awsf cognito-idp create-user-pool-client \
  --user-pool-id <POOL_ID> \
  --client-name task-manager-client \
  --no-generate-secret \
  --explicit-auth-flows ALLOW_USER_PASSWORD_AUTH ALLOW_REFRESH_TOKEN_AUTH \
  --query 'UserPoolClient.ClientId' --output text

# Crear usuario de prueba
awsf cognito-idp admin-create-user \
  --user-pool-id  <POOL_ID> \
  --username      testuser \
  --temporary-password Test1234!

# Login → obtener tokens JWT
awsf cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id <CLIENT_ID> \
  --auth-parameters USERNAME=testuser,PASSWORD=Test1234!
```

Guardá el `IdToken` resultante: es el Bearer token que usarás en el frontend y en las pruebas.

---

## 6. Aprovisionar API Gateway v2 con JWT Authorizer

```bash
# 1. Crear HTTP API
API_ID=$(awsf apigatewayv2 create-api \
  --name task-manager-api \
  --protocol-type HTTP \
  --query 'ApiId' --output text)

# 2. Crear JWT Authorizer apuntando al User Pool de Cognito
AUTHORIZER_ID=$(awsf apigatewayv2 create-authorizer \
  --api-id $API_ID \
  --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --name cognito-jwt-authorizer \
  --jwt-configuration \
    Audience=<CLIENT_ID>,Issuer=http://localhost:4566/<POOL_ID> \
  --query 'AuthorizerId' --output text)

# 3. Integración hacia task-creator (corre en :8080)
INTEGRATION_ID=$(awsf apigatewayv2 create-integration \
  --api-id $API_ID \
  --integration-type HTTP_PROXY \
  --integration-uri http://host.docker.internal:8080 \
  --integration-method ANY \
  --payload-format-version 1.0 \
  --query 'IntegrationId' --output text)

# 4. Ruta protegida: cualquier método sobre /tasks
awsf apigatewayv2 create-route \
  --api-id $API_ID \
  --route-key 'ANY /tasks' \
  --authorization-type JWT \
  --authorizer-id $AUTHORIZER_ID \
  --target integrations/$INTEGRATION_ID

awsf apigatewayv2 create-route \
  --api-id $API_ID \
  --route-key 'ANY /tasks/{proxy+}' \
  --authorization-type JWT \
  --authorizer-id $AUTHORIZER_ID \
  --target integrations/$INTEGRATION_ID

# 5. Deploy
awsf apigatewayv2 create-stage \
  --api-id $API_ID \
  --stage-name local \
  --auto-deploy

# Endpoint final
echo "API Gateway URL: http://localhost:4566/restapis/$API_ID/local/_user_request_"
```

Todas las peticiones al API Gateway sin Bearer token válido recibirán `401 Unauthorized`.

---

## 7. Configurar los microservicios para usar Floci

### Variables de entorno comunes (`.env`)

```dotenv
# Floci / AWS
AWS_ENDPOINT_URL=http://localhost:4566
AWS_REGION=us-east-1
AWS_ACCESS_KEY_ID=test
AWS_SECRET_ACCESS_KEY=test

# SQS
SQS_QUEUE_URL=http://localhost:4566/000000000000/task-created-queue

# RDS
R2DBC_URL=r2dbc:postgresql://localhost:<puerto-rds>/postgres
DB_USERNAME=admin
DB_PASSWORD=secret123
```

### task-creator — publicar a SQS

```java
// En lugar de RabbitTemplate, usar SqsAsyncClient del SDK v2
SqsAsyncClient.builder()
    .endpointOverride(URI.create(System.getenv("AWS_ENDPOINT_URL")))
    .region(Region.US_EAST_1)
    .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
    .build();
```

### task-processor — consumir de SQS

```java
// Spring Cloud AWS SQS Listener apuntando a Floci
@SqsListener("task-created-queue")
public void handleTaskCreated(TaskCreatedEvent event) {
    // actualizar tarea a completada
}
```

Configurar el endpoint en `application.yml`:

```yaml
spring:
  cloud:
    aws:
      sqs:
        endpoint: ${AWS_ENDPOINT_URL}
      credentials:
        access-key: test
        secret-key: test
      region:
        static: us-east-1
```

---

## 8. Flujo de desarrollo local completo

```
1. docker run  →  levanta Floci (:4566)
2. awsf sqs    →  crea task-created-queue
3. awsf rds    →  crea instancia PostgreSQL (Docker-backed)
4. psql        →  aplica model.sql
5. awsf cognito →  crea User Pool + usuario de prueba
6. awsf apigatewayv2 → crea API + JWT Authorizer + rutas
7. ./mvnw -pl infrastructure/entry-points/app spring-boot:run  (task-creator :8080)
8. ./mvnw -pl infrastructure/entry-points/app spring-boot:run  (task-processor :8081)
9. npm run dev  →  frontend apunta a API Gateway URL
```

---

## Scripts de automatización (próximo paso)

Todos los comandos de aprovisionamiento (pasos 2-6) se pueden consolidar en un script
`scripts/setup-local.sh` para ejecutar con un solo comando después de levantar Floci.

---

## Referencias

- [Floci — Sitio oficial](https://floci.io)
- [Quick Start - Floci](https://floci.io/floci/getting-started/quick-start/)
- [Migrate from LocalStack](https://floci.io/floci/getting-started/migrate-from-localstack/)
- [Services Overview](https://floci.io/floci/services/)
