terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    null = {
      source  = "hashicorp/null"
      version = "~> 3.0"
    }
  }
}

provider "aws" {
  region                      = var.aws_region
  access_key                  = "test"
  secret_key                  = "test"
  skip_credentials_validation = true
  skip_requesting_account_id  = true
  skip_metadata_api_check     = true

  endpoints {
    sqs                     = var.floci_endpoint
    rds                     = var.floci_endpoint
    cognitoidentityprovider = var.floci_endpoint
    apigatewayv2            = var.floci_endpoint
    ecr                     = var.floci_endpoint
  }
}
