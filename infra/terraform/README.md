# AWS infrastructure (Terraform)

Maps the architecture in the design doc onto AWS:

| Design element | File | Resources |
|---|---|---|
| Route 53 + health checks | edge.tf | A alias records, HTTPS health check |
| Shield Advanced | edge.tf | `aws_shield_protection` (optional, needs a subscription) |
| WAF (OWASP-style rules) | edge.tf | managed rule groups + per-IP rate rule |
| CloudFront to public ALB | edge.tf, alb.tf | uncached dynamic API, secret origin header |
| Public ALB | alb.tf | HTTPS only, rejects requests without the CloudFront header |
| Spring Cloud Gateway | ecs.tf | Fargate service behind the ALB, autoscaled |
| Private east-west routing | ecs.tf | AWS Cloud Map namespace `<name>.local` |
| Services | ecs.tf | 5 Fargate services + ECR repos + log groups |
| PostgreSQL per service | data.tf | RDS with `rds.logical_replication=1` |
| Redis (rate limiter) | data.tf | ElastiCache, TLS |
| Read model | data.tf | DocumentDB (MongoDB API) |
| Kafka + Debezium CDC | data.tf | MSK, MSK Connect outbox connectors |
| Network | network.tf, security.tf | 3 tiers x 3 AZ, NAT, S3 endpoint, security groups |

## Steps
1. Build and push images (repo root has a generic `Dockerfile`):
   `docker build --build-arg MODULE=order-service -t <ecr-url>/restaurant/order-service:1.0.0 .` for each of the six modules.
2. Upload the Debezium PostgreSQL connector plugin ZIP to S3 and set `debezium_plugin_*`.
3. Create ACM certificates (ALB: `origin.<domain>` in your region, CloudFront: `<domain>` in us-east-1).
4. `cp terraform.tfvars.example terraform.tfvars`, edit, then `terraform init && terraform plan && terraform apply`.
   Create the ECR repositories first if you want to push before the services start: `terraform apply -target=aws_ecr_repository.svc`.
5. After the first apply, reboot the RDS instances once (parameter `rds.logical_replication` is applied on reboot).

## Known limits (read before production)
- Not applied or validated against a real AWS account. Run `terraform validate` and review `plan` carefully.
- MSK uses TLS without client authentication inside private subnets. Move to IAM or SCRAM for stricter setups.
- Debezium DB password is placed in the MSK Connect connector configuration; rotate or use a config provider.
- DocumentDB needs the Amazon CA bundle in the services' Java truststore.
- `psp_url` empty means the built-in fake PSP is used. Set a real provider before go-live.
- Cost: 4 RDS, 3 MSK brokers, 2 NAT/Redis/DocumentDB nodes. Start with `single_nat_gateway = true`.
