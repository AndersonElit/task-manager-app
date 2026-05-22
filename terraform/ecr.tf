locals {
  ecr_repos = [
    "task-manager/tasks-creator",
    "task-manager/tasks-processor",
    "task-manager/frontend",
  ]
}

resource "aws_ecr_repository" "repos" {
  for_each = toset(local.ecr_repos)

  name                 = each.key
  image_tag_mutability = "MUTABLE"
}

output "ecr_repository_urls" {
  value = {
    for name, repo in aws_ecr_repository.repos : name => repo.repository_url
  }
}
