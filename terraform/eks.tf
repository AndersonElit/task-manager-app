resource "aws_eks_cluster" "main" {
  name     = "task-manager"
  role_arn = "arn:aws:iam::000000000000:role/eks-cluster-role"

  vpc_config {
    subnet_ids = ["subnet-00000000"]
  }
}

resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "task-manager-nodes"
  node_role_arn   = "arn:aws:iam::000000000000:role/eks-node-role"
  subnet_ids      = ["subnet-00000000"]

  scaling_config {
    desired_size = 2
    min_size     = 1
    max_size     = 3
  }

  instance_types = ["t3.medium"]
}
