#!/usr/bin/env bash
# =============================================================================
# setup-local.sh
# Aprovisiona todos los servicios AWS locales via Floci para el Task Manager.
# Requisito previo: Floci corriendo en Docker (ver docs/plan-aws-local.md).
# =============================================================================

set -euo pipefail

# -----------------------------------------------------------------------------
# Colores
# -----------------------------------------------------------------------------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

log_info()    { echo -e "${CYAN}[INFO]${NC}  $*"; }
log_success() { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }
log_section() { echo -e "\n${BOLD}==> $*${NC}"; }

# -----------------------------------------------------------------------------
# Configuración
# -----------------------------------------------------------------------------
FLOCI_ENDPOINT="http://localhost:4566"
AWS_REGION="us-east-1"
AWS_ACCESS_KEY_ID="test"
AWS_SECRET_ACCESS_KEY="test"

SQS_QUEUE_NAME="task-created-queue"

RDS_INSTANCE_ID="taskdb"
RDS_ENGINE="postgres"
RDS_DB_NAME="taskmanager"
RDS_USERNAME="admin"
RDS_PASSWORD="secret123"
RDS_PORT_RANGE_START=5400

COGNITO_POOL_NAME="task-manager-pool"
COGNITO_CLIENT_NAME="task-manager-client"
COGNITO_TEST_USER="testuser"
COGNITO_TEST_PASSWORD="Test1234!"

API_NAME="task-manager-api"
API_STAGE="local"
TASK_CREATOR_PORT=8080

MODEL_SQL="$(cd "$(dirname "$0")/.." && pwd)/docs/model.sql"
ENV_FILE="$(cd "$(dirname "$0")/.." && pwd)/.env.local"

# AWS CLI apuntando a Floci
AWSF="aws --endpoint-url=$FLOCI_ENDPOINT \
          --region=$AWS_REGION \
          --no-cli-pager"

export AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_DEFAULT_REGION=$AWS_REGION

# -----------------------------------------------------------------------------
# 0. Verificar dependencias y que Floci esté corriendo
# -----------------------------------------------------------------------------
log_section "Verificando requisitos"

for cmd in aws curl psql; do
  command -v "$cmd" &>/dev/null \
    || log_error "'$cmd' no encontrado. Instalalo antes de continuar."
done

log_info "Comprobando Floci en $FLOCI_ENDPOINT ..."
curl -sf "$FLOCI_ENDPOINT/_floci/health" >/dev/null \
  || log_error "Floci no responde. Ejecutá primero:
  docker run -d --name floci \\
    -p 4566:4566 -p 5000-9000:5000-9000 \\
    -v /var/run/docker.sock:/var/run/docker.sock \\
    floci/floci:latest"

log_success "Floci disponible."

# -----------------------------------------------------------------------------
# 1. SQS — cola de eventos task.created
# -----------------------------------------------------------------------------
log_section "SQS: $SQS_QUEUE_NAME"

if $AWSF sqs get-queue-url --queue-name "$SQS_QUEUE_NAME" &>/dev/null; then
  log_warn "La cola '$SQS_QUEUE_NAME' ya existe, se omite la creación."
else
  $AWSF sqs create-queue --queue-name "$SQS_QUEUE_NAME" >/dev/null
  log_success "Cola creada."
fi

SQS_QUEUE_URL=$($AWSF sqs get-queue-url \
  --queue-name "$SQS_QUEUE_NAME" \
  --query 'QueueUrl' --output text)

log_info "SQS URL: $SQS_QUEUE_URL"

# -----------------------------------------------------------------------------
# 2. RDS — instancia PostgreSQL
# -----------------------------------------------------------------------------
log_section "RDS: instancia $RDS_INSTANCE_ID"

# Floci devuelve DBInstances:[] (no un error) cuando la instancia no existe,
# por eso DBInstances[0].DBInstanceStatus es "None". Se trata igual que "not-found".
rds_status() {
  $AWSF rds describe-db-instances \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --query 'DBInstances[0].DBInstanceStatus' \
    --output text 2>/dev/null || echo "not-found"
}

RDS_STATUS=$(rds_status)

if [ "$RDS_STATUS" = "not-found" ] || [ "$RDS_STATUS" = "None" ] || [ -z "$RDS_STATUS" ]; then
  log_info "Creando instancia RDS PostgreSQL..."
  $AWSF rds create-db-instance \
    --db-instance-identifier "$RDS_INSTANCE_ID" \
    --db-instance-class      db.t3.micro \
    --engine                 "$RDS_ENGINE" \
    --db-name                "$RDS_DB_NAME" \
    --master-username        "$RDS_USERNAME" \
    --master-user-password   "$RDS_PASSWORD" \
    --allocated-storage      20 \
    --output text >/dev/null \
    || log_error "Falló la creación de la instancia RDS. Verificá que Floci tenga acceso al socket Docker: -v /var/run/docker.sock:/var/run/docker.sock"
  log_success "Instancia RDS creada, esperando que esté disponible..."
else
  log_warn "Instancia '$RDS_INSTANCE_ID' ya existe (estado: $RDS_STATUS)."
fi

log_info "Esperando que RDS esté disponible..."
for i in $(seq 1 40); do
  RDS_STATUS=$(rds_status)

  if [ "$RDS_STATUS" = "available" ]; then
    log_success "RDS disponible."
    break
  fi

  echo -n "  intento $i/40 (estado: $RDS_STATUS)..."
  sleep 5
  echo ""

  [ "$i" -eq 40 ] && log_error "Timeout esperando RDS.
  Revisá los logs de Floci con: docker logs floci
  Asegurate de que el contenedor fue iniciado con: -v /var/run/docker.sock:/var/run/docker.sock"
done

RDS_HOST=$($AWSF rds describe-db-instances \
  --db-instance-identifier "$RDS_INSTANCE_ID" \
  --query 'DBInstances[0].Endpoint.Address' --output text)

RDS_PORT=$($AWSF rds describe-db-instances \
  --db-instance-identifier "$RDS_INSTANCE_ID" \
  --query 'DBInstances[0].Endpoint.Port' --output text)

log_info "RDS endpoint: $RDS_HOST:$RDS_PORT"

# Aplicar modelo de base de datos
log_info "Aplicando model.sql..."
[ -f "$MODEL_SQL" ] || log_error "No se encontró $MODEL_SQL"

log_info "Esperando que PostgreSQL acepte conexiones en $RDS_HOST:$RDS_PORT..."
for i in $(seq 1 30); do
  if PGPASSWORD="$RDS_PASSWORD" psql \
       -h "$RDS_HOST" \
       -p "$RDS_PORT" \
       -U "$RDS_USERNAME" \
       -d "$RDS_DB_NAME" \
       -c "SELECT 1" >/dev/null 2>&1; then
    log_success "PostgreSQL listo."
    break
  fi
  echo -n "  intento $i/30..."
  sleep 3
  echo ""
  [ "$i" -eq 30 ] && log_error "Timeout esperando PostgreSQL en $RDS_HOST:$RDS_PORT."
done

PGPASSWORD="$RDS_PASSWORD" psql \
  -h "$RDS_HOST" \
  -p "$RDS_PORT" \
  -U "$RDS_USERNAME" \
  -d "$RDS_DB_NAME" \
  -f "$MODEL_SQL" \
  -v ON_ERROR_STOP=1 >/dev/null

log_success "Esquema aplicado correctamente."

# -----------------------------------------------------------------------------
# 3. Cognito — User Pool + App Client + usuario de prueba
# -----------------------------------------------------------------------------
log_section "Cognito: User Pool"

POOL_ID=$($AWSF cognito-idp list-user-pools --max-results 10 \
  --query "UserPools[?Name=='$COGNITO_POOL_NAME'].Id | [0]" \
  --output text 2>/dev/null || echo "None")

if [ "$POOL_ID" = "None" ] || [ -z "$POOL_ID" ]; then
  POOL_ID=$($AWSF cognito-idp create-user-pool \
    --pool-name "$COGNITO_POOL_NAME" \
    --query 'UserPool.Id' --output text)
  log_success "User Pool creado: $POOL_ID"
else
  log_warn "User Pool '$COGNITO_POOL_NAME' ya existe ($POOL_ID), se omite."
fi

CLIENT_ID=$($AWSF cognito-idp list-user-pool-clients \
  --user-pool-id "$POOL_ID" \
  --query "UserPoolClients[?ClientName=='$COGNITO_CLIENT_NAME'].ClientId | [0]" \
  --output text 2>/dev/null || echo "None")

if [ "$CLIENT_ID" = "None" ] || [ -z "$CLIENT_ID" ]; then
  CLIENT_ID=$($AWSF cognito-idp create-user-pool-client \
    --user-pool-id  "$POOL_ID" \
    --client-name   "$COGNITO_CLIENT_NAME" \
    --no-generate-secret \
    --explicit-auth-flows \
      ALLOW_USER_PASSWORD_AUTH \
      ALLOW_REFRESH_TOKEN_AUTH \
    --query 'UserPoolClient.ClientId' --output text)
  log_success "App Client creado: $CLIENT_ID"
else
  log_warn "App Client '$COGNITO_CLIENT_NAME' ya existe ($CLIENT_ID), se omite."
fi

USER_EXISTS=$($AWSF cognito-idp list-users \
  --user-pool-id "$POOL_ID" \
  --filter "username = \"$COGNITO_TEST_USER\"" \
  --query 'Users | length(@)' --output text 2>/dev/null || echo "0")

if [ "$USER_EXISTS" = "0" ]; then
  $AWSF cognito-idp admin-create-user \
    --user-pool-id     "$POOL_ID" \
    --username         "$COGNITO_TEST_USER" \
    --temporary-password "$COGNITO_TEST_PASSWORD" \
    --message-action   SUPPRESS >/dev/null
  log_success "Usuario de prueba '$COGNITO_TEST_USER' creado."
else
  log_warn "Usuario '$COGNITO_TEST_USER' ya existe, se omite."
fi

# Establecer contraseña permanente para salir del estado FORCE_CHANGE_PASSWORD
$AWSF cognito-idp admin-set-user-password \
  --user-pool-id "$POOL_ID" \
  --username     "$COGNITO_TEST_USER" \
  --password     "$COGNITO_TEST_PASSWORD" \
  --permanent >/dev/null
log_success "Contraseña marcada como permanente."

log_info "Obteniendo JWT de prueba..."
JWT_RESPONSE=$($AWSF cognito-idp initiate-auth \
  --auth-flow USER_PASSWORD_AUTH \
  --client-id "$CLIENT_ID" \
  --auth-parameters \
    "USERNAME=$COGNITO_TEST_USER,PASSWORD=$COGNITO_TEST_PASSWORD" 2>/dev/null || echo "{}")

ID_TOKEN=$(echo "$JWT_RESPONSE" | \
  python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('AuthenticationResult',{}).get('IdToken','<requiere-cambio-de-password>'))" 2>/dev/null || echo "<no-disponible>")

log_info "IdToken (JWT de prueba): ${ID_TOKEN:0:60}..."

# -----------------------------------------------------------------------------
# 4. API Gateway v2 — HTTP API + JWT Authorizer + rutas
# -----------------------------------------------------------------------------
log_section "API Gateway v2: $API_NAME"

API_ID=$($AWSF apigatewayv2 get-apis \
  --query "Items[?Name=='$API_NAME'].ApiId | [0]" \
  --output text 2>/dev/null || echo "None")

if [ "$API_ID" = "None" ] || [ -z "$API_ID" ]; then
  API_ID=$($AWSF apigatewayv2 create-api \
    --name          "$API_NAME" \
    --protocol-type HTTP \
    --query 'ApiId' --output text)
  log_success "HTTP API creada: $API_ID"
else
  log_warn "API '$API_NAME' ya existe ($API_ID), se omite la creación."
fi

AUTHORIZER_ID=$($AWSF apigatewayv2 get-authorizers \
  --api-id "$API_ID" \
  --query "Items[?Name=='cognito-jwt-authorizer'].AuthorizerId | [0]" \
  --output text 2>/dev/null || echo "None")

if [ "$AUTHORIZER_ID" = "None" ] || [ -z "$AUTHORIZER_ID" ]; then
  AUTHORIZER_ID=$($AWSF apigatewayv2 create-authorizer \
    --api-id          "$API_ID" \
    --authorizer-type JWT \
    --identity-source '$request.header.Authorization' \
    --name            cognito-jwt-authorizer \
    --jwt-configuration \
      "Audience=$CLIENT_ID,Issuer=$FLOCI_ENDPOINT/$POOL_ID" \
    --query 'AuthorizerId' --output text)
  log_success "JWT Authorizer creado: $AUTHORIZER_ID"
else
  log_warn "JWT Authorizer ya existe ($AUTHORIZER_ID), se omite."
fi

INTEGRATION_ID=$($AWSF apigatewayv2 get-integrations \
  --api-id "$API_ID" \
  --query "Items[0].IntegrationId" \
  --output text 2>/dev/null || echo "None")

if [ "$INTEGRATION_ID" = "None" ] || [ -z "$INTEGRATION_ID" ]; then
  INTEGRATION_ID=$($AWSF apigatewayv2 create-integration \
    --api-id                  "$API_ID" \
    --integration-type        HTTP_PROXY \
    --integration-uri         "http://host.docker.internal:$TASK_CREATOR_PORT" \
    --integration-method      ANY \
    --payload-format-version  1.0 \
    --query 'IntegrationId' --output text)
  log_success "Integración creada hacia task-creator :$TASK_CREATOR_PORT"
else
  log_warn "Integración ya existe ($INTEGRATION_ID), se omite."
fi

for ROUTE_KEY in "ANY /api/v1/tasks" "ANY /api/v1/tasks/{proxy+}"; do
  ROUTE_EXISTS=$($AWSF apigatewayv2 get-routes \
    --api-id "$API_ID" \
    --query "Items[?RouteKey=='$ROUTE_KEY'] | length(@)" \
    --output text 2>/dev/null || echo "0")

  if [ "$ROUTE_EXISTS" = "0" ]; then
    $AWSF apigatewayv2 create-route \
      --api-id            "$API_ID" \
      --route-key         "$ROUTE_KEY" \
      --authorization-type JWT \
      --authorizer-id     "$AUTHORIZER_ID" \
      --target            "integrations/$INTEGRATION_ID" >/dev/null
    log_success "Ruta creada: $ROUTE_KEY"
  else
    log_warn "Ruta '$ROUTE_KEY' ya existe, se omite."
  fi
done

STAGE_EXISTS=$($AWSF apigatewayv2 get-stages \
  --api-id "$API_ID" \
  --query "Items[?StageName=='$API_STAGE'] | length(@)" \
  --output text 2>/dev/null || echo "0")

if [ "$STAGE_EXISTS" = "0" ]; then
  $AWSF apigatewayv2 create-stage \
    --api-id    "$API_ID" \
    --stage-name "$API_STAGE" \
    --auto-deploy >/dev/null
  log_success "Stage '$API_STAGE' creado."
else
  log_warn "Stage '$API_STAGE' ya existe, se omite."
fi

API_GW_URL="$FLOCI_ENDPOINT/restapis/$API_ID/$API_STAGE/_user_request_"

# -----------------------------------------------------------------------------
# 5. Generar .env.local para los microservicios
# -----------------------------------------------------------------------------
log_section "Generando $ENV_FILE"

cat > "$ENV_FILE" <<EOF
# Generado por scripts/setup-local.sh — $(date -u +"%Y-%m-%dT%H:%M:%SZ")
# No commitear este archivo.

# AWS / Floci
export AWS_ENDPOINT_URL=$FLOCI_ENDPOINT
export AWS_REGION=$AWS_REGION
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test

# SQS
export SQS_QUEUE_URL=$SQS_QUEUE_URL

# RDS
export R2DBC_URL=r2dbc:postgresql://$RDS_HOST:$RDS_PORT/$RDS_DB_NAME
export DB_USERNAME=$RDS_USERNAME
export DB_PASSWORD=$RDS_PASSWORD

# Cognito
export COGNITO_POOL_ID=$POOL_ID
export COGNITO_CLIENT_ID=$CLIENT_ID

# API Gateway
export API_GATEWAY_URL=$API_GW_URL
EOF

log_success "Archivo generado: $ENV_FILE"

# -----------------------------------------------------------------------------
# Resumen final
# -----------------------------------------------------------------------------
log_section "Entorno local listo"

echo -e "
  ${BOLD}SQS Queue URL:${NC}      $SQS_QUEUE_URL
  ${BOLD}RDS endpoint:${NC}       $RDS_HOST:$RDS_PORT / db: $RDS_DB_NAME
  ${BOLD}Cognito Pool ID:${NC}    $POOL_ID
  ${BOLD}Cognito Client ID:${NC}  $CLIENT_ID
  ${BOLD}API Gateway URL:${NC}    $API_GW_URL
  ${BOLD}JWT de prueba:${NC}      ${ID_TOKEN:0:60}...

  ${CYAN}Próximos pasos:${NC}
    1. Levantá task-creator  →  cd backend/task-creator  && ./mvnw spring-boot:run
    2. Levantá task-processor →  cd backend/task-processor && ./mvnw spring-boot:run
    3. Probá la API con el JWT:
       curl -H \"Authorization: Bearer \$ID_TOKEN\" $API_GW_URL/api/v1/tasks
"
