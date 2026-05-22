resource "aws_eks_cluster" "main" {
  name     = "task-manager"
  role_arn = "arn:aws:iam::000000000000:role/eks-cluster-role"

  vpc_config {
    subnet_ids = ["subnet-00000000"]
  }
}
