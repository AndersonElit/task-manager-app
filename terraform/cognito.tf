resource "aws_cognito_user_pool" "pool" {
  name = var.cognito_pool_name

  password_policy {
    minimum_length    = 8
    require_lowercase = false
    require_numbers   = false
    require_symbols   = false
    require_uppercase = false
  }
}

resource "aws_cognito_user_pool_client" "client" {
  name         = var.cognito_client_name
  user_pool_id = aws_cognito_user_pool.pool.id

  generate_secret = false

  explicit_auth_flows = [
    "ALLOW_USER_PASSWORD_AUTH",
    "ALLOW_REFRESH_TOKEN_AUTH",
  ]
}

resource "aws_cognito_user" "testuser" {
  user_pool_id = aws_cognito_user_pool.pool.id
  username     = var.cognito_test_user
  password     = var.cognito_test_password
}
