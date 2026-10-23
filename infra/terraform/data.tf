locals {
  databases = toset(["orders", "payments", "kitchen", "workflow"])
  outbox_dbs = toset(["orders", "payments", "kitchen"])
}

# ---------------- PostgreSQL (one instance per service, logical replication on for Debezium)
resource "aws_db_subnet_group" "main" {
  name       = "${var.name}-db"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_db_parameter_group" "postgres" {
  name   = "${var.name}-postgres16"
  family = "postgres16"

  parameter {
    name         = "rds.logical_replication"
    value        = "1"
    apply_method = "pending-reboot"
  }
}

resource "aws_db_instance" "this" {
  for_each = local.databases

  identifier                  = "${var.name}-${each.key}"
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = var.db_instance_class
  allocated_storage           = 20
  max_allocated_storage       = 100
  storage_encrypted           = true
  db_name                     = each.key
  username                    = "restaurant_admin"
  manage_master_user_password = true # password lives in Secrets Manager, never in Terraform state
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.rds.id]
  parameter_group_name        = aws_db_parameter_group.postgres.name
  multi_az                    = var.db_multi_az
  backup_retention_period     = 7
  publicly_accessible         = false
  deletion_protection         = var.protect_data
  skip_final_snapshot         = !var.protect_data
  final_snapshot_identifier   = var.protect_data ? "${var.name}-${each.key}-final" : null
}

# ---------------- Redis (gateway rate limiter)
resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.name}-redis"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "${var.name}-redis"
  description                = "Gateway rate limiter"
  engine                     = "redis"
  engine_version             = "7.1"
  node_type                  = var.redis_node_type
  num_cache_clusters         = 2
  automatic_failover_enabled = true
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

# ---------------- DocumentDB (MongoDB-compatible API) for the CQRS read model
resource "aws_docdb_subnet_group" "main" {
  name       = "${var.name}-docdb"
  subnet_ids = aws_subnet.data[*].id
}

resource "aws_docdb_cluster" "main" {
  cluster_identifier        = "${var.name}-docdb"
  engine                    = "docdb"
  master_username           = var.docdb_master_username
  master_password           = var.docdb_master_password
  db_subnet_group_name      = aws_docdb_subnet_group.main.name
  vpc_security_group_ids    = [aws_security_group.docdb.id]
  storage_encrypted         = true
  deletion_protection       = var.protect_data
  skip_final_snapshot       = !var.protect_data
  final_snapshot_identifier = var.protect_data ? "${var.name}-docdb-final" : null
}

resource "aws_docdb_cluster_instance" "main" {
  count              = 2
  identifier         = "${var.name}-docdb-${count.index}"
  cluster_identifier = aws_docdb_cluster.main.id
  instance_class     = var.docdb_instance_class
}

resource "aws_secretsmanager_secret" "mongo_uri" {
  name_prefix = "${var.name}/mongo-uri-"
}

resource "aws_secretsmanager_secret_version" "mongo_uri" {
  secret_id     = aws_secretsmanager_secret.mongo_uri.id
  secret_string = "mongodb://${var.docdb_master_username}:${urlencode(var.docdb_master_password)}@${aws_docdb_cluster.main.endpoint}:27017/orders_read?tls=true&replicaSet=rs0&readPreference=secondaryPreferred&retryWrites=false"
}

# ---------------- Kafka (MSK)
resource "aws_msk_configuration" "main" {
  name           = "${var.name}-kafka"
  kafka_versions = ["3.6.0"]

  server_properties = <<-EOT
    auto.create.topics.enable=true
    default.replication.factor=3
    min.insync.replicas=2
    num.partitions=3
  EOT
}

resource "aws_msk_cluster" "main" {
  cluster_name           = "${var.name}-kafka"
  kafka_version          = "3.6.0"
  number_of_broker_nodes = var.az_count

  broker_node_group_info {
    instance_type   = var.msk_instance_type
    client_subnets  = aws_subnet.data[*].id
    security_groups = [aws_security_group.msk.id]

    storage_info {
      ebs_storage_info {
        volume_size = 100
      }
    }
  }

  configuration_info {
    arn      = aws_msk_configuration.main.arn
    revision = aws_msk_configuration.main.latest_revision
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  client_authentication {
    unauthenticated = true # TLS-only inside private subnets; switch to IAM/SCRAM for stricter setups
  }
}

# ---------------- Debezium on MSK Connect (one outbox connector per service database)
resource "aws_cloudwatch_log_group" "connect" {
  name              = "/${var.name}/msk-connect"
  retention_in_days = 30
}

data "aws_iam_policy_document" "connect_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["kafkaconnect.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "connect" {
  name               = "${var.name}-msk-connect"
  assume_role_policy = data.aws_iam_policy_document.connect_assume.json
}

resource "aws_iam_role_policy" "connect" {
  name = "logs"
  role = aws_iam_role.connect.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogStream", "logs:PutLogEvents", "logs:DescribeLogStreams"]
      Resource = "${aws_cloudwatch_log_group.connect.arn}:*"
    }]
  })
}

resource "aws_mskconnect_custom_plugin" "debezium" {
  name         = "${var.name}-debezium-postgres"
  content_type = "ZIP"

  location {
    s3 {
      bucket_arn = "arn:aws:s3:::${var.debezium_plugin_bucket}"
      file_key   = var.debezium_plugin_key
    }
  }
}

data "aws_secretsmanager_secret_version" "db_password" {
  for_each  = local.outbox_dbs
  secret_id = aws_db_instance.this[each.key].master_user_secret[0].secret_arn
}

resource "aws_mskconnect_connector" "outbox" {
  for_each = local.outbox_dbs

  name                 = "${var.name}-${each.key}-outbox"
  kafkaconnect_version = "2.7.1"

  capacity {
    provisioned_capacity {
      mcu_count    = 1
      worker_count = 1
    }
  }

  # NOTE: the DB password ends up in the connector configuration. Prefer an MSK Connect config provider
  # for Secrets Manager once you harden this, and remember RDS rotates the managed password.
  connector_configuration = {
    "connector.class"                                     = "io.debezium.connector.postgresql.PostgresConnector"
    "tasks.max"                                           = "1"
    "plugin.name"                                         = "pgoutput"
    "database.hostname"                                   = aws_db_instance.this[each.key].address
    "database.port"                                       = "5432"
    "database.user"                                       = aws_db_instance.this[each.key].username
    "database.password"                                   = jsondecode(data.aws_secretsmanager_secret_version.db_password[each.key].secret_string)["password"]
    "database.dbname"                                     = each.key
    "topic.prefix"                                        = each.key
    "table.include.list"                                  = "public.outbox_event"
    "slot.name"                                           = "${each.key}_outbox_slot"
    "publication.name"                                    = "${each.key}_outbox_slot_pub"
    "publication.autocreate.mode"                         = "filtered"
    "tombstones.on.delete"                                = "false"
    "transforms"                                          = "outbox"
    "transforms.outbox.type"                              = "io.debezium.transforms.outbox.EventRouter"
    "transforms.outbox.table.field.event.id"              = "id"
    "transforms.outbox.table.field.event.key"             = "aggregate_id"
    "transforms.outbox.table.field.event.type"            = "event_type"
    "transforms.outbox.table.field.event.payload"         = "payload"
    "transforms.outbox.route.by.field"                    = "aggregate_type"
    "transforms.outbox.table.expand.json.payload"         = "true"
    "transforms.outbox.route.topic.replacement"           = "restaurant.events.$${routedByValue}"
    "transforms.outbox.table.fields.additional.placement" = "event_type:header:eventType,id:header:eventId"
    "key.converter"                                       = "org.apache.kafka.connect.storage.StringConverter"
    "value.converter"                                     = "org.apache.kafka.connect.json.JsonConverter"
    "value.converter.schemas.enable"                      = "false"
  }

  kafka_cluster {
    apache_kafka_cluster {
      bootstrap_servers = aws_msk_cluster.main.bootstrap_brokers_tls

      vpc {
        security_groups = [aws_security_group.connect.id]
        subnets         = aws_subnet.private[*].id
      }
    }
  }

  kafka_cluster_client_authentication {
    authentication_type = "NONE"
  }

  kafka_cluster_encryption_in_transit {
    encryption_type = "TLS"
  }

  plugin {
    custom_plugin {
      arn      = aws_mskconnect_custom_plugin.debezium.arn
      revision = aws_mskconnect_custom_plugin.debezium.latest_revision
    }
  }

  log_delivery {
    worker_log_delivery {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.connect.name
      }
    }
  }

  service_execution_role_arn = aws_iam_role.connect.arn
}
