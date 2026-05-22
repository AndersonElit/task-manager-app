resource "aws_apigatewayv2_api" "api" {
  name          = var.api_name
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id           = aws_apigatewayv2_api.api.id
  authorizer_type  = "JWT"
  identity_sources = ["$request.header.Authorization"]
  name             = "cognito-jwt-authorizer"

  jwt_configuration {
    audience = [aws_cognito_user_pool_client.client.id]
    issuer   = "${var.floci_endpoint}/${aws_cognito_user_pool.pool.id}"
  }
}

resource "aws_apigatewayv2_integration" "tasks" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_uri        = "http://host.docker.internal:${var.task_creator_port}/api/v1/tasks"
  integration_method     = "ANY"
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_integration" "tasks_proxy" {
  api_id                 = aws_apigatewayv2_api.api.id
  integration_type       = "HTTP_PROXY"
  integration_uri        = "http://host.docker.internal:${var.task_creator_port}/api/v1/tasks/{proxy}"
  integration_method     = "ANY"
  payload_format_version = "1.0"
}

resource "aws_apigatewayv2_route" "tasks" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "ANY /api/v1/tasks"

  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.tasks.id}"
}

resource "aws_apigatewayv2_route" "tasks_proxy" {
  api_id    = aws_apigatewayv2_api.api.id
  route_key = "ANY /api/v1/tasks/{proxy+}"

  authorization_type = "JWT"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.tasks_proxy.id}"
}

resource "aws_apigatewayv2_stage" "local" {
  api_id      = aws_apigatewayv2_api.api.id
  name        = var.api_stage
  auto_deploy = true
}
