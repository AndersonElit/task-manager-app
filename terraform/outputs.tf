output "aws_endpoint_url" {
  value = var.floci_endpoint
}

output "sqs_queue_url" {
  value = aws_sqs_queue.task_created.url
}

output "rds_host" {
  value = aws_db_instance.taskdb.address
}

output "rds_port" {
  value = aws_db_instance.taskdb.port
}

output "db_name" {
  value = var.rds_db_name
}

output "db_username" {
  value = var.rds_username
}

output "db_password" {
  value     = var.rds_password
  sensitive = true
}

output "cognito_pool_id" {
  value = aws_cognito_user_pool.pool.id
}

output "cognito_client_id" {
  value = aws_cognito_user_pool_client.client.id
}

output "api_gateway_url" {
  value = "http://localhost:4566/restapis/${aws_apigatewayv2_api.api.id}/${var.api_stage}/_user_request_"
}
