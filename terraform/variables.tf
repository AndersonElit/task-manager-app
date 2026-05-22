variable "floci_endpoint" {
  description = "URL del contenedor Floci"
  default     = "http://localhost:4566"
}

variable "aws_region" {
  default = "us-east-1"
}

variable "sqs_queue_name" {
  default = "task-created-queue"
}

variable "rds_instance_id" {
  default = "taskdb"
}

variable "rds_db_name" {
  default = "taskmanager"
}

variable "rds_username" {
  default = "admin"
}

variable "rds_password" {
  default   = "secret123"
  sensitive = true
}

variable "cognito_pool_name" {
  default = "task-manager-pool"
}

variable "cognito_client_name" {
  default = "task-manager-client"
}

variable "cognito_test_user" {
  default = "testuser"
}

variable "cognito_test_password" {
  default   = "Test1234!"
  sensitive = true
}

variable "api_name" {
  default = "task-manager-api"
}

variable "api_stage" {
  default = "local"
}

variable "task_creator_port" {
  default = 8080
}
