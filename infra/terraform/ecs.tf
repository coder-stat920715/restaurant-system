locals {
  common_env = {
    KAFKA_BOOTSTRAP                           = aws_msk_cluster.main.bootstrap_brokers_tls
    SPRING_KAFKA_PROPERTIES_SECURITY_PROTOCOL = "SSL"
    OIDC_ISSUER_URI                           = var.oidc_issuer_uri
  }

  db_env = {
    for k, db in aws_db_instance.this : k => {
      DB_URL  = "jdbc:postgresql://${db.address}:5432/${k}"
      DB_USER = db.username
    }
  }

  db_secret = {
    for k, db in aws_db_instance.this : k => "${db.master_user_secret[0].secret_arn}:password::"
  }

  secret_arns = concat(
    [for db in aws_db_instance.this : db.master_user_secret[0].secret_arn],
    [aws_secretsmanager_secret.mongo_uri.arn]
  )

  # One entry per microservice. db = "" means the service has no PostgreSQL database.
  services = {
    "api-gateway" = {
      port    = 8080
      cpu     = 512
      memory  = 1024
      desired = var.gateway_desired_count
      db      = ""
      env = tomap({
        REDIS_HOST                    = aws_elasticache_replication_group.redis.primary_endpoint_address
        SPRING_DATA_REDIS_SSL_ENABLED = "true"
        ORDER_URL                     = "http://order-service.${var.name}.local:8081"
        QUERY_URL                     = "http://order-query-service.${var.name}.local:8082"
        KITCHEN_WORKFLOW_URL          = "http://kitchen-workflow-service.${var.name}.local:8087"
      })
      secrets = tomap({})
    }
    "order-service" = {
      port    = 8081
      cpu     = 512
      memory  = 1024
      desired = 2
      db      = "orders"
      env     = tomap({})
      secrets = tomap({})
    }
    "order-query-service" = {
      port    = 8082
      cpu     = 512
      memory  = 1024
      desired = 2
      db      = ""
      env     = tomap({})
      secrets = tomap({ MONGO_URI = aws_secretsmanager_secret.mongo_uri.arn })
    }
    "payment-service" = {
      port    = 8085
      cpu     = 512
      memory  = 1024
      desired = 2
      db      = "payments"
      env     = tomap({ PSP_URL = var.psp_url != "" ? var.psp_url : "http://localhost:8085/psp" })
      secrets = tomap({})
    }
    "kitchen-inventory-service" = {
      port    = 8086
      cpu     = 512
      memory  = 1024
      desired = 2
      db      = "kitchen"
      env     = tomap({})
      secrets = tomap({})
    }
    "kitchen-workflow-service" = {
      port    = 8087
      cpu     = 512
      memory  = 1024
      desired = 2
      db      = "workflow"
      env     = tomap({})
      secrets = tomap({})
    }
  }
}

resource "aws_ecr_repository" "svc" {
  for_each             = local.services
  name                 = "${var.name}/${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecs_cluster" "main" {
  name = "${var.name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "svc" {
  for_each          = local.services
  name              = "/${var.name}/${each.key}"
  retention_in_days = 30
}

# ---- AWS Cloud Map: east-west service discovery inside the private subnets
resource "aws_service_discovery_private_dns_namespace" "main" {
  name = "${var.name}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "svc" {
  for_each = local.services
  name     = each.key

  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.main.id

    dns_records {
      ttl  = 10
      type = "A"
    }

    routing_policy = "MULTIVALUE"
  }

  health_check_custom_config {
    failure_threshold = 1
  }
}

# ---- IAM
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  name = "read-secrets"
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = local.secret_arns
    }]
  })
}

resource "aws_iam_role" "task" {
  name               = "${var.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

# ---- task definitions + services
resource "aws_ecs_task_definition" "svc" {
  for_each                 = local.services
  family                   = "${var.name}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = each.key
    image     = "${aws_ecr_repository.svc[each.key].repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{
      containerPort = each.value.port
      protocol      = "tcp"
    }]

    environment = [
      for k, v in merge(local.common_env, try(local.db_env[each.value.db], {}), each.value.env) : {
        name  = k
        value = v
      }
    ]

    secrets = [
      for k, v in merge(try({ DB_PASSWORD = local.db_secret[each.value.db] }, {}), each.value.secrets) : {
        name      = k
        valueFrom = v
      }
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.svc[each.key].name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "svc" {
  for_each        = local.services
  name            = each.key
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.svc[each.key].arn
  desired_count   = each.value.desired
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.svc[each.key].arn
  }

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  dynamic "load_balancer" {
    for_each = each.key == "api-gateway" ? [1] : []

    content {
      target_group_arn = aws_lb_target_group.gateway.arn
      container_name   = "api-gateway"
      container_port   = 8080
    }
  }

  depends_on = [aws_lb_listener_rule.from_cloudfront]
}

resource "aws_appautoscaling_target" "gateway" {
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.svc["api-gateway"].name}"
  min_capacity       = var.gateway_desired_count
  max_capacity       = var.gateway_desired_count * 4
}

resource "aws_appautoscaling_policy" "gateway_cpu" {
  name               = "${var.name}-gateway-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.gateway.service_namespace
  scalable_dimension = aws_appautoscaling_target.gateway.scalable_dimension
  resource_id        = aws_appautoscaling_target.gateway.resource_id

  target_tracking_scaling_policy_configuration {
    target_value = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
