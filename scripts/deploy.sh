#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${STACK_NAME:-self-storage-app}"
AWS_REGION="${AWS_REGION:-$(aws configure get region)}"
if [[ -z "$AWS_REGION" || "$AWS_REGION" == "None" ]]; then
  echo "Set AWS_REGION or configure a default AWS CLI region." >&2
  exit 1
fi

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
DOMAIN_PREFIX="${COGNITO_DOMAIN_PREFIX:-self-storage-${ACCOUNT_ID}-${AWS_REGION}}"

sam build --template-file infrastructure/template.yaml
sam deploy \
  --template-file .aws-sam/build/template.yaml \
  --stack-name "$STACK_NAME" \
  --region "$AWS_REGION" \
  --capabilities CAPABILITY_IAM \
  --resolve-s3 \
  --no-confirm-changeset \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    "CognitoDomainPrefix=${DOMAIN_PREFIX}" \
    "NotificationEmail=${NOTIFICATION_EMAIL:-}"

output() {
  aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$AWS_REGION" \
    --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue | [0]" \
    --output text
}

API_URL="$(output ApiUrl)"
COGNITO_DOMAIN="$(output CognitoDomain)"
CLIENT_ID="$(output UserPoolClientId)"
BUCKET="$(output FrontendBucketName)"
DISTRIBUTION_ID="$(output CloudFrontDistributionId)"
FRONTEND_URL="$(output FrontendUrl)"
CONFIG_FILE="$(mktemp)"
trap 'rm -f "$CONFIG_FILE"' EXIT

API_URL="$API_URL" COGNITO_DOMAIN="$COGNITO_DOMAIN" CLIENT_ID="$CLIENT_ID" \
  python3 - "$CONFIG_FILE" <<'PY'
import json
import os
import sys

with open(sys.argv[1], "w", encoding="utf-8") as config:
    json.dump({
        "apiUrl": os.environ["API_URL"],
        "cognitoDomain": os.environ["COGNITO_DOMAIN"],
        "clientId": os.environ["CLIENT_ID"],
    }, config)
PY

aws s3 sync frontend/ "s3://${BUCKET}/" --delete --exclude "config.example.json" --region "$AWS_REGION"
aws s3 cp "$CONFIG_FILE" "s3://${BUCKET}/config.json" \
  --content-type application/json --cache-control no-store --region "$AWS_REGION"
aws cloudfront create-invalidation \
  --distribution-id "$DISTRIBUTION_ID" \
  --paths "/*" \
  --region "$AWS_REGION" >/dev/null

printf 'Deployment complete: %s\n' "$FRONTEND_URL"
printf 'API base URL: %s\n' "$API_URL"
printf 'Cognito user pool ID: %s\n' "$(output UserPoolId)"
if [[ -n "${NOTIFICATION_EMAIL:-}" ]]; then
  echo "Confirm the SNS subscription email sent to ${NOTIFICATION_EMAIL}."
fi
