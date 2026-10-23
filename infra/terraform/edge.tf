locals {
  origin_domain = "origin.${var.domain_name}"

  waf_managed_rules = {
    common     = { name = "AWSManagedRulesCommonRuleSet", priority = 10 }
    bad_inputs = { name = "AWSManagedRulesKnownBadInputsRuleSet", priority = 20 }
    sqli       = { name = "AWSManagedRulesSQLiRuleSet", priority = 30 }
    reputation = { name = "AWSManagedRulesAmazonIpReputationList", priority = 40 }
  }
}

# ---- AWS WAF in front of CloudFront: OWASP Top 10 style managed rules + per-IP rate limit
resource "aws_wafv2_web_acl" "edge" {
  provider = aws.us_east_1
  name     = "${var.name}-edge"
  scope    = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "rate-limit-per-ip"
    priority = 5

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = 2000
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  dynamic "rule" {
    for_each = local.waf_managed_rules

    content {
      name     = rule.key
      priority = rule.value.priority

      override_action {
        none {}
      }

      statement {
        managed_rule_group_statement {
          name        = rule.value.name
          vendor_name = "AWS"
        }
      }

      visibility_config {
        cloudwatch_metrics_enabled = true
        metric_name                = "${var.name}-${rule.key}"
        sampled_requests_enabled   = true
      }
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-edge"
    sampled_requests_enabled   = true
  }
}

# ---- CloudFront: dynamic API calls are never cached, everything is forwarded to the public ALB
resource "aws_cloudfront_distribution" "edge" {
  enabled         = true
  is_ipv6_enabled = true
  comment         = "${var.name} API edge"
  aliases         = [var.domain_name]
  web_acl_id      = aws_wafv2_web_acl.edge.arn
  http_version    = "http2and3"
  price_class     = "PriceClass_100"

  origin {
    domain_name = local.origin_domain
    origin_id   = "public-alb"

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "https-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }

    custom_header {
      name  = "X-Origin-Verify"
      value = var.origin_verify_secret
    }
  }

  default_cache_behavior {
    target_origin_id         = "public-alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    compress                 = true
    cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # Managed-CachingDisabled
    origin_request_policy_id = "216adef6-5c7f-47e4-b989-5492eafa07d3" # Managed-AllViewer
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = var.cloudfront_certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

# ---- Route 53
resource "aws_route53_record" "api" {
  zone_id = var.hosted_zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.edge.domain_name
    zone_id                = aws_cloudfront_distribution.edge.hosted_zone_id
    evaluate_target_health = false
  }
}

resource "aws_route53_record" "origin" {
  zone_id = var.hosted_zone_id
  name    = local.origin_domain
  type    = "A"

  alias {
    name                   = aws_lb.public.dns_name
    zone_id                = aws_lb.public.zone_id
    evaluate_target_health = true
  }
}

resource "aws_route53_health_check" "api" {
  fqdn              = var.domain_name
  type              = "HTTPS"
  port              = 443
  resource_path     = "/actuator/health"
  request_interval  = 30
  failure_threshold = 3
  measure_latency   = true

  tags = {
    Name = "${var.name}-api-health"
  }
}

# ---- Shield Advanced (needs an active subscription)
resource "aws_shield_protection" "cloudfront" {
  provider     = aws.us_east_1
  count        = var.enable_shield_advanced ? 1 : 0
  name         = "${var.name}-cloudfront"
  resource_arn = aws_cloudfront_distribution.edge.arn
}

resource "aws_shield_protection" "alb" {
  count        = var.enable_shield_advanced ? 1 : 0
  name         = "${var.name}-alb"
  resource_arn = aws_lb.public.arn
}
