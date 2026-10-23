terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.name
      ManagedBy = "terraform"
    }
  }
}

# CloudFront-scoped WAF and Shield Advanced protections are global and live in us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
