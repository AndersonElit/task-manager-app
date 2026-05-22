#!/usr/bin/env python3
"""
Lee `terraform output -json` desde stdin y escribe .env.local a stdout.
Uso: cd terraform && terraform output -json | python3 ../scripts/tf-to-env.py > ../.env.local
"""
import json
import sys
from datetime import datetime, timezone


def get(data, key):
    return data.get(key, {}).get("value", "")


data = json.load(sys.stdin)

rds_host = get(data, "rds_host")
rds_port = get(data, "rds_port")
db_name  = get(data, "db_name")
timestamp = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

print("\n".join([
    f"# Generado por terraform output — {timestamp}",
    "# No commitear este archivo.",
    "",
    "# AWS / Floci",
    f"export AWS_ENDPOINT_URL={get(data, 'aws_endpoint_url')}",
    f"export AWS_REGION=us-east-1",
    "export AWS_ACCESS_KEY_ID=test",
    "export AWS_SECRET_ACCESS_KEY=test",
    "",
    "# SQS",
    f"export SQS_QUEUE_URL={get(data, 'sqs_queue_url')}",
    "",
    "# RDS",
    f"export R2DBC_URL=r2dbc:postgresql://{rds_host}:{rds_port}/{db_name}",
    f"export DB_USERNAME={get(data, 'db_username')}",
    f"export DB_PASSWORD={get(data, 'db_password')}",
    "",
    "# Cognito",
    f"export COGNITO_POOL_ID={get(data, 'cognito_pool_id')}",
    f"export COGNITO_CLIENT_ID={get(data, 'cognito_client_id')}",
    "",
    "# API Gateway",
    f"export API_GATEWAY_URL={get(data, 'api_gateway_url')}",
    "",
    "# EKS",
    f"export EKS_CLUSTER_NAME={get(data, 'eks_cluster_name')}",
]))

# Generar frontend/.env.local con los valores actuales de Cognito y API Gateway
import os, pathlib

script_dir = pathlib.Path(__file__).parent
frontend_env = script_dir.parent / "frontend" / ".env.local"

api_gateway_url = get(data, "api_gateway_url")
cognito_client_id = get(data, "cognito_client_id")

frontend_env.write_text("\n".join([
    f"VITE_COGNITO_ENDPOINT=http://localhost:4566",
    f"VITE_COGNITO_CLIENT_ID={cognito_client_id}",
    f"VITE_API_URL={api_gateway_url}",
    "",
]))
