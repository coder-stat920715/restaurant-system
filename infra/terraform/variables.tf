variable "name" {
  type    = string
  default = "restaurant"
}

variable "region" {
  type    = string
  default = "eu-west-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "az_count" {
  type    = number
  default = 3
}

variable "single_nat_gateway" {
  description = "One NAT gateway (cheap) or one per AZ (resilient)."
  type        = bool
  default     = true
}

variable "domain_name" {
  description = "Public API host name, e.g. api.example.com"
  type        = string
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone that contains domain_name"
  type        = string
}

variable "alb_certificate_arn" {
  description = "ACM certificate (same region as the ALB) covering origin.<domain_name>"
  type        = string
}

variable "cloudfront_certificate_arn" {
  description = "ACM certificate in us-east-1 covering domain_name"
  type        = string
}

variable "origin_verify_secret" {
  description = "Shared secret CloudFront adds as X-Origin-Verify; the ALB rejects requests without it."
  type        = string
  sensitive   = true
}

variable "image_tag" {
  type    = string
  default = "latest"
}

variable "oidc_issuer_uri" {
  description = "Keycloak realm or Cognito user pool issuer URL"
  type        = string
}

variable "psp_url" {
  description = "Base URL of the real payment provider. Empty keeps the built-in fake PSP (not for production)."
  type        = string
  default     = ""
}

variable "gateway_desired_count" {
  type    = number
  default = 2
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "db_multi_az" {
  type    = bool
  default = false
}

variable "protect_data" {
  description = "Deletion protection and final snapshots for databases. Set true in production."
  type        = bool
  default     = false
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.small"
}

variable "docdb_instance_class" {
  type    = string
  default = "db.t4g.medium"
}

variable "docdb_master_username" {
  type    = string
  default = "restaurant_admin"
}

variable "docdb_master_password" {
  type      = string
  sensitive = true
}

variable "msk_instance_type" {
  type    = string
  default = "kafka.t3.small"
}

variable "debezium_plugin_bucket" {
  description = "S3 bucket holding the Debezium PostgreSQL connector ZIP for MSK Connect"
  type        = string
}

variable "debezium_plugin_key" {
  description = "S3 key of the Debezium connector ZIP (debezium-connector-postgres plugin archive)"
  type        = string
}

variable "enable_shield_advanced" {
  description = "Attach Shield Advanced protections (requires an active Shield Advanced subscription)."
  type        = bool
  default     = false
}
