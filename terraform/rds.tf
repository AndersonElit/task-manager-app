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

# Aplica model.sql al crear o cuando el esquema o la instancia RDS cambian.
# Se re-ejecuta si cambia el hash de model.sql, el endpoint de RDS, o el id de DB.
resource "null_resource" "init_db" {
  depends_on = [aws_db_instance.taskdb]

  triggers = {
    model_hash   = filemd5("${path.module}/../docs/model.sql")
    rds_address  = aws_db_instance.taskdb.address
    rds_port     = aws_db_instance.taskdb.port
    db_identifier = aws_db_instance.taskdb.identifier
  }

  provisioner "local-exec" {
    interpreter = ["/bin/bash", "-c"]
    command     = <<-SHELL
      set -euo pipefail

      if ! command -v psql &>/dev/null; then
        echo "ERROR: psql no está instalado. Instálalo con: sudo apt-get install postgresql-client"
        exit 1
      fi

      echo "Esperando que PostgreSQL (${aws_db_instance.taskdb.address}:${aws_db_instance.taskdb.port}) acepte conexiones..."
      READY=0
      for i in $(seq 1 30); do
        if PGPASSWORD='${var.rds_password}' psql \
            -h '${aws_db_instance.taskdb.address}' \
            -p '${aws_db_instance.taskdb.port}' \
            -U '${var.rds_username}' \
            -d '${var.rds_db_name}' \
            -c "SELECT 1" >/dev/null 2>&1; then
          READY=1
          break
        fi
        echo "  intento $i/30..."
        sleep 3
      done

      if [ "$READY" -eq 0 ]; then
        echo "ERROR: PostgreSQL no aceptó conexiones después de 30 intentos."
        exit 1
      fi

      echo "PostgreSQL listo. Aplicando model.sql..."
      PGPASSWORD='${var.rds_password}' psql \
        -h '${aws_db_instance.taskdb.address}' \
        -p '${aws_db_instance.taskdb.port}' \
        -U '${var.rds_username}' \
        -d '${var.rds_db_name}' \
        -f '${path.module}/../docs/model.sql' \
        -v ON_ERROR_STOP=1

      echo "Modelo de base de datos aplicado exitosamente."
    SHELL
  }
}
