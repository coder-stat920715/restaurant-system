output "api_url" {
  value = "https://${var.domain_name}"
}

output "cloudfront_domain" {
  value = aws_cloudfront_distribution.edge.domain_name
}

output "ecr_repositories" {
  value = { for k, r in aws_ecr_repository.svc : k => r.repository_url }
}

output "kafka_bootstrap_tls" {
  value = aws_msk_cluster.main.bootstrap_brokers_tls
}

output "postgres_endpoints" {
  value = { for k, db in aws_db_instance.this : k => db.address }
}

output "documentdb_endpoint" {
  value = aws_docdb_cluster.main.endpoint
}
