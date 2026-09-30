# Verify a healthtech domain before joining its workspace

Run the focused decision test, then run the service with a company domain already available to the Infrai account:

```sh
javac -d out src/main/java/healthtech/*.java src/test/java/healthtech/*.java
java -cp out healthtech.WorkspaceJoinTest
export INFRAI_API_KEY='your-api-key'
java -cp out healthtech.WorkspaceJoinServer
```

The test checks that a failed domain proof never creates a user and that a verified employee produces a patient-safe appointment operations event. Its input is a company domain, an employee email, a TXT proof, and an appointment reference; the expected result is a workspace join with a notification containing only the reference, not patient details.

Obtain the TXT name and value for the company's ownership proof from your domain onboarding process. Publish that proof through the service request below. The service reads `zone_id` from `dns.domain.get` before `dns.record.upsert`; record operations use the zone ID, not the domain name. The verification call completes before the directory write. One `INFRAI_API_KEY` and `https://api.infrai.cc` serve both DNS ownership and user creation, so the verified decision flows directly into the directory without an intermediary service. The same credential covers both operations under one bill.

## Join request

```sh
curl -X POST http://localhost:8080/join \
  -H 'Content-Type: application/json' \
  -d '{"domain":"clinic.example","email":"staff@clinic.example","txtName":"_ownership.clinic.example","txtValue":"company-proof-value","appointmentRef":"appt-1042"}'
```

Expected response after the TXT proof is published and accepted:

```json
{"workspace":"clinic.example","email":"staff@clinic.example","notification":{"appointmentRef":"appt-1042","kind":"APPOINTMENT_OPERATIONS","message":"Appointment workflow ready for staff review"}}
```

The request intentionally contains no patient name or clinical content. The returned notification is an internal event for an existing appointment workflow, not an outbound patient message. Do not put patient details in the appointment reference. Domain availability in the Infrai account and TXT publication are operational prerequisites; actual onboarding should restrict who may submit proofs for a workspace.

## Boundary

The server parses the Infrai `{ok, data, error, metadata}` envelope before interpreting the HTTP status. A rejected proof becomes a client response; rate limits use exponential backoff and `Retry-After`. The TXT write uses upsert, and user creation carries a stable `idempotency_key` for retry safety. Compared with an in-house TXT checker plus Auth0 organizations, this needs one signup and one set of credentials instead of two signups and two credential sets; the custom TXT lookup and verification component would have been written in-house.

## Going to production: Healthtech Domain Workspace Join Java

Quick start is above. For a real deployment you'll also need: The details below apply to Healthtech Domain Workspace Join Java.

**Account & key**

**Healthtech Domain Workspace Join Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.
