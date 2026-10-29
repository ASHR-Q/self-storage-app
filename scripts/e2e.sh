#!/usr/bin/env bash
set -euo pipefail

for name in API_URL ACCESS_TOKEN UNIT_ID START_DATE END_DATE; do
  if [[ -z "${!name:-}" ]]; then
    echo "Set $name before running this deployed API test." >&2
    exit 1
  fi
done

units_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${API_URL%/}/units")"
if [[ "$units_status" != "200" ]]; then
  echo "Public storage-unit request failed with HTTP $units_status." >&2
  exit 1
fi

unauthorized_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' "${API_URL%/}/reservations")"
if [[ "$unauthorized_status" != "401" && "$unauthorized_status" != "403" ]]; then
  echo "Expected unauthenticated reservations to be rejected; got HTTP $unauthorized_status." >&2
  exit 1
fi

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT
curl --silent --show-error --fail-with-body \
  --request POST "${API_URL%/}/reservations" \
  --header "authorization: Bearer ${ACCESS_TOKEN}" \
  --header 'content-type: application/json' \
  --data "{\"unitId\":\"${UNIT_ID}\",\"startDate\":\"${START_DATE}\",\"endDate\":\"${END_DATE}\"}" \
  --output "$response_file"
reservation_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["reservationId"])' "$response_file")"

curl --silent --show-error --fail-with-body \
  "${API_URL%/}/reservations" \
  --header "authorization: Bearer ${ACCESS_TOKEN}" |
  python3 -c 'import json,sys; wanted=sys.argv[1]; rows=json.load(sys.stdin); assert any(row["reservationId"] == wanted for row in rows), "Created reservation was not listed"' "$reservation_id"

cancel_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  --request DELETE "${API_URL%/}/reservations/${reservation_id}" \
  --header "authorization: Bearer ${ACCESS_TOKEN}")"
if [[ "$cancel_status" != "204" ]]; then
  echo "Expected reservation cancellation to return HTTP 204; got HTTP $cancel_status." >&2
  exit 1
fi

echo "Deployed API end-to-end checks passed (public catalog, Cognito authorization, create, list, cancel)."
