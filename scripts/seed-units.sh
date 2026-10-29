#!/usr/bin/env bash
set -euo pipefail

STACK_NAME="${STACK_NAME:-self-storage-app}"
AWS_REGION="${AWS_REGION:-$(aws configure get region)}"
if [[ -z "$AWS_REGION" || "$AWS_REGION" == "None" ]]; then
  echo "Set AWS_REGION or configure a default AWS CLI region." >&2
  exit 1
fi
if [[ -z "${TABLE_NAME:-}" ]]; then
  TABLE_NAME="$(aws cloudformation describe-stacks \
    --stack-name "$STACK_NAME" \
    --region "$AWS_REGION" \
    --query "Stacks[0].Resources[?LogicalResourceId=='StorageTable'].PhysicalResourceId | [0]" \
    --output text)"
fi

if [[ -z "$TABLE_NAME" || "$TABLE_NAME" == "None" ]]; then
  echo "Could not find the deployed storage table. Set TABLE_NAME explicitly." >&2
  exit 1
fi

for unit in "A101:Small:500" "A102:Medium:700" "A103:Large:1000"; do
  IFS=: read -r unit_id size price <<< "$unit"
  item="$(printf '{"PK":{"S":"UNIT#%s"},"SK":{"S":"UNIT"},"entity":{"S":"UNIT"},"unitId":{"S":"%s"},"size":{"S":"%s"},"monthlyPrice":{"N":"%s"},"available":{"BOOL":true}}' \
    "$unit_id" "$unit_id" "$size" "$price")"
  if aws dynamodb put-item \
    --table-name "$TABLE_NAME" \
    --item "$item" \
    --condition-expression 'attribute_not_exists(PK)' \
    --region "$AWS_REGION" >/dev/null 2>&1; then
    echo "Added storage unit $unit_id."
  else
    echo "Storage unit $unit_id already exists or could not be added." >&2
    exit 1
  fi
done
