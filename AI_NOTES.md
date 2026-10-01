# AI Collaboration & Engineering Reflections (`AI_NOTES.md`)

This document captures the engineering thought process, architectural trade-offs, and collaborative workflow with AI in designing and building the **Event-Driven GitHub Automation Bot**.

---

## 1. AI Tools and Collaboration Split

* **AI Model / Assistant Used:** Antigravity AI (powered by Gemini models).
* **Division of Work:**
  * **Architecture & Decision Making (Human / Senior Architect Lens):** Defined core resilience requirements, prioritized idempotency checks, selected the technology stack (Spring Boot 3 + MySQL), and structured the zero-cost deployment topology.
  * **Scaffolding & Code Generation (AI Assistant):** Generated idiomatic Spring Boot JPA entities, repository interfaces, REST endpoints, HMAC cryptographic service, and Tailwind CSS templates.
  * **Test Engineering & Verification (Human + AI):** Scripted PowerShell simulated webhook calls to validate edge cases (timing attacks, duplicate delivery replays, and unauthorized payloads).

---

## 2. Key Architecture Decisions Made

### Decision 1: Database-Backed Idempotency Table (`X-GitHub-Delivery`)
* **Rationale:** GitHub guarantees *at-least-once* delivery. In production, network hiccups or 504 timeouts frequently cause GitHub to resend the exact same event. Rather than relying on ephemeral in-memory sets (which wipe on restarts), we created a dedicated `webhook_deliveries` table with a `UNIQUE` index on the delivery GUID.
* **Result:** If a delivery is re-sent, the system intercepts it in `< 10ms` and returns a clean HTTP `200 OK` without duplicating issue comments or spamming Slack channels.

### Decision 2: Fast-ACK Pattern with Asynchronous Thread Pooling
* **Rationale:** GitHub requires webhook endpoints to respond in under 10 seconds. In our architecture, an incoming event might trigger a chain of external calls: an LLM triage via Gemini, a GitHub API write-back, and a Slack webhook dispatch. Any latency in the Gemini API or network congestion could cause a webhook timeout.
* **Result:** The `WebhookController` handles signature verification and database persistence synchronously, immediately returns HTTP `202 Accepted`, and dispatches the rule evaluation to an `@Async` background worker pool.

### Decision 3: Infinite Recursion & Bot Loop Protection
* **Rationale:** When a bot writes back to GitHub (e.g., posting a comment or adding a label), GitHub immediately emits a new webhook event (`issue_comment.created` or `issues.labeled`). Without protection, the bot will process its own action and post another comment, resulting in an infinite execution loop and rapid API rate-limit exhaustion.
* **Result:** Explicit guards inspect `sender.type == "Bot"` and `sender.login.endsWith("[bot]")`, short-circuiting the event before rule evaluation.

---

## 3. Hardest Bug / AI Friction Encountered & Resolution

### The Issue: Context Initialization Failure with Spring Boot 4.x / Jackson Dependency
* **What Happened:** During initial compilation, Spring Boot's dependency injection failed on startup with `UnsatisfiedDependencyException: No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper' available`. 
* **The Root Cause:** In standard Spring Web starters, `ObjectMapper` is auto-configured; however, with the custom starter dependency configuration in this project, Jackson databind was not automatically populated into the Spring application context as an autowireable bean candidate for custom services.
* **How It Was Fixed:** 
  1. Explicitly added `jackson-databind` to `pom.xml`.
  2. Created a dedicated `@Configuration` class (`AppConfig.java`) exposing a custom configured `ObjectMapper` bean with `FAIL_ON_UNKNOWN_PROPERTIES = false`. This hardened the JSON parser against unexpected GitHub payload attributes.

---

## 4. What I Would Improve or Add With More Time

1. **Full GitHub App Integration (JWT + RS256):** Migrate from personal access tokens to GitHub App private key authentication (`.pem` file), automatically generating short-lived installation tokens.
2. **Dead Letter Queue (DLQ) & Exponential Backoff:** If an external downstream service (Slack or GitHub) is temporarily 503 unavailable, store the failed event in a retry queue with exponential backoff rather than immediately marking it as permanently failed.
3. **Webhook Secret Auto-Rotation:** Provide a secret rotation interface directly on the dashboard.
