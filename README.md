# Fieldstone Self Storage

A serverless self-storage application: Java 21 REST API, DynamoDB persistence, Cognito customer sign-in, a static customer frontend on private S3 behind CloudFront, and asynchronous reservation notifications.

## What is included

- `GET /units` lists available storage units.
- Cognito-protected `GET /reservations`, `POST /reservations`, and `DELETE /reservations/{reservationId}` let signed-in customers manage their own reservations.
- DynamoDB stores storage units, customer reservations, and unit booking records. Strongly consistent reads plus a per-unit version guard in DynamoDB transactions prevent overlapping reservations from racing each other.
- Cognito Hosted UI uses OAuth authorization-code flow with PKCE. API Gateway validates access tokens before invoking customer routes.
- DynamoDB Streams invokes a notification Lambda asynchronously when a reservation is created. The Lambda publishes to SNS; an optional email subscriber receives new-reservation messages.
- The frontend is served from a private S3 bucket through a CloudFront origin access control.
- AWS SAM defines and builds all cloud resources.

## Prerequisites

- Java 21 and Maven
- AWS CLI configured with deployment permissions
- AWS SAM CLI
- Python 3 (used by the deployment helper)

## Deploy

From the repository root:

```bash
NOTIFICATION_EMAIL=you@example.com ./scripts/deploy.sh
```

`NOTIFICATION_EMAIL` is optional. If set, confirm the SNS subscription email after deployment. The deploy script creates/updates the SAM stack, builds the frontend configuration from stack outputs, uploads the static site, and invalidates CloudFront. Set `AWS_REGION` if your AWS CLI does not have a default region. Set `STACK_NAME` to use a different stack name. Cognito hosted UI domain prefixes are globally unique; override the account-and-region-derived default with `COGNITO_DOMAIN_PREFIX` if it is already taken.

Seed example storage spaces once after deploying:

```bash
./scripts/seed-units.sh
```

The sample units are `A101` (Small, ZAR 500/month), `A102` (Medium, ZAR 700/month), and `A103` (Large, ZAR 1,000/month). The seed command does not overwrite existing units.

## API contract

All dates use `YYYY-MM-DD`; reservation end dates are exclusive. New reservations have `PENDING` status.

| Method | Path | Access | Purpose |
|---|---|---|---|
| `GET` | `/units` | Public | List storage units |
| `GET` | `/reservations` | Cognito access token | List the signed-in customer's reservations |
| `POST` | `/reservations` | Cognito access token | Create a reservation with `unitId`, `startDate`, and `endDate` |
| `DELETE` | `/reservations/{reservationId}` | Cognito access token | Cancel one of the signed-in customer's reservations |

## Build and tests

Run the Java unit tests and package the Lambda artifact:

```bash
mvn -f backend/pom.xml test package
```

After deployment and signing in through the frontend, copy the Cognito **access token** from the browser session and run an end-to-end API check against an available, seeded unit:

```bash
API_URL=https://your-api.execute-api.your-region.amazonaws.com \
ACCESS_TOKEN=your-cognito-access-token \
UNIT_ID=A101 START_DATE=2026-11-01 END_DATE=2026-12-01 \
./scripts/e2e.sh
```

The deployed smoke test checks public catalog access, rejection of unauthenticated customer requests, then reservation creation, listing, and cancellation.

## Project layout

- `backend/` — Java domain model, Lambda handlers, and Maven build
- `frontend/` — static customer application
- `infrastructure/template.yaml` — AWS SAM infrastructure
- `scripts/` — deployment, sample data, and deployed end-to-end checks
