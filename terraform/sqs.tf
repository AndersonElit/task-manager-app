resource "aws_sqs_queue" "task_created" {
  name = var.sqs_queue_name
}
