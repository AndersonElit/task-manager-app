resource "aws_db_instance" "taskdb" {
  identifier          = var.rds_instance_id
  engine              = "postgres"
  instance_class      = "db.t3.micro"
  allocated_storage   = 20
  db_name             = var.rds_db_name
  username            = var.rds_username
  password            = var.rds_password
  skip_final_snapshot = true
}

# Aplica model.sql al crear o cuando el esquema cambia.
# Se re-ejecuta sólo si cambia el hash de docs/model.sql.
resource "null_resource" "init_db" {
  depends_on = [aws_db_instance.taskdb]

  triggers = {
    model_hash = filemd5("${path.module}/../docs/model.sql")
  }

  provisioner "local-exec" {
    command = <<-SHELL
      set -e
      echo "Esperando que PostgreSQL acepte conexiones..."
      for i in $(seq 1 30); do
        PGPASSWORD='${var.rds_password}' psql \
          -h '${aws_db_instance.taskdb.address}' \
          -p '${aws_db_instance.taskdb.port}' \
          -U '${var.rds_username}' \
          -d '${var.rds_db_name}' \
          -c "SELECT 1" >/dev/null 2>&1 && break
        echo "  intento $i/30..."
        sleep 3
      done
      echo "Aplicando model.sql..."
      PGPASSWORD='${var.rds_password}' psql \
        -h '${aws_db_instance.taskdb.address}' \
        -p '${aws_db_instance.taskdb.port}' \
        -U '${var.rds_username}' \
        -d '${var.rds_db_name}' \
        -f '${path.module}/../docs/model.sql' \
        -v ON_ERROR_STOP=1
    SHELL
  }
}
