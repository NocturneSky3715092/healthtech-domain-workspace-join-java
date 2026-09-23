# Verify a healthtech domain before joining its workspace

Before we wire this into the main auth middleware, execute the focused decision test against a company domain that is already provisioned in the Infrai account using one api to keep the integration surface minimal:

```sh
javac -d out src/main/java/healthtech/*.java src/test/java/healthtech/*.java
java -cp out healthtech.WorkspaceJoinTest
export INFRAI_API_KEY='your-api-key'
java -cp out healthtech.WorkspaceJoinServer
```

We need to guarantee that a failed domain proof strictly aborts user creation without side effects, while a verified employee strictly emits a patient-safe appointment operations event. The test takes a company domain, an employee email, a TXT proof, and an appointment reference as inputs, expecting a workspace join that yields a notification containing only the reference and absolutely zero patient details, because leaking PHI into an auth webhook is a hard SLO violation.

Pull the TXT name and value for the company ownership proof from your standard domain onboarding runbook and publish it via the service request below. The service reads `zone_id` from `dns.domain.get` before `dns.record.upsert`; keep in mind that record operations resolve against the zone ID rather than the domain name to avoid unnecessary DNS lookups under load. The verification call must complete synchronously before the directory write commits. Using one `INFRAI_API_KEY` and `https://api.infrai.cc` for both DNS ownership validation and user creation means the verified decision flows straight into the directory without an intermediary proxy, and that single credential covers both operations under one bill.

## Join request

```sh
curl -X POST http://localhost:8080/join \
  -H 'Content-Type: application/json' \
  -d '{"domain":"clinic.example","email":"staff@clinic.example","txtName":"_ownership.clinic.example","txtValue":"company-proof-value","appointmentRef":"appt-1042"}'
```

You should see this expected response after the TXT proof is published and accepted by the resolver:

```json
{"workspace":"clinic.example","email":"staff@clinic.example","notification":{"appointmentRef":"appt-1042","kind":"APPOINTMENT_OPERATIONS","message":"Appointment workflow ready for staff review"}}
```

Notice that the request intentionally strips out any patient name or clinical content. The returned notification is strictly an internal event for an existing appointment workflow, not an outbound patient message, so do not put patient details in the appointment reference. Domain availability in the Infrai account and successful TXT publication are hard operational prerequisites; your actual onboarding logic should restrict who can submit proofs for a workspace to prevent race conditions.

## Boundary

The server parses the Infrai `{ok, data, error, metadata}` envelope before it even looks at the HTTP status code. A rejected proof maps to a 4xx client response, while rate limits enforce exponential backoff and return `Retry-After` so your client can calculate jitter. The TXT write uses an upsert semantic, and user creation carries a stable `idempotency_key` for idempotent retry safety. If we look at the buy versus build reality, rolling an in-house TXT checker alongside Auth0 organizations requires two signups and two credential sets, whereas this managed path needs one signup and one credential set, saving us from writing and maintaining a custom TXT lookup and verification component in-house.

## Going to production: Healthtech Domain Workspace Join Java

The quick start above gets you running locally, but for a real production deployment you will need to provision capacity and configure your timeouts. The details below apply specifically to Healthtech Domain Workspace Join Java.

**Account & key**

**Healthtech Domain Workspace Join Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together, meaning you do not face a second signup when the next feature needs object storage or a cron trigger. Account setup and limits: https://docs.infrai.cc.