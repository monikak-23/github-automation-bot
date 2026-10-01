# Event-Driven GitHub Automation Bot

An enterprise-grade, unattended event automation engine and bot built with **Java 17, Spring Boot 3.3+ (4.1 compatible), and MySQL**.

This service reacts to GitHub repository activity (issues, pull requests), securely evaluates customizable automation rules, writes back to the GitHub API, dispatches rich Slack notifications, and leverages Google Gemini AI for issue triage and summarization.

---

## Architecture Overview

```
                      +-----------------------------+
                      |     GitHub Webhook Event    |
                      |  (Issues, Pull Requests)    |
                      +--------------+--------------+
                                     | POST (HMAC-SHA256 signature)
                                     v
+-------------------------------------------------------------------------------+
|                       SPRING BOOT AUTOMATION ENGINE                           |
|                                                                               |
|  1. Gateway & Ingestion Layer (`WebhookController`)                           |
|     - Validates cryptographic HMAC-SHA256 signature (`X-Hub-Signature-256`)   |
|     - Verifies `X-GitHub-Delivery` GUID against `webhook_deliveries`          |
|     - Replay Protection / Idempotency: Duplicate GUID returns fast 200 OK      |
|     - Fast-ACK: Returns 202 Accepted in < 100ms to prevent webhook timeouts   |
|                                                                               |
|  2. Asynchronous Processing Worker (`WebhookProcessingService`)               |
|     - Infinite Loop Guard: Filters out events triggered by bot accounts      |
|     - Evaluates active database rules (keyword matching on title/body/author) |
|     - (Optional AI Triage): Invokes Gemini 1.5 Flash for priority triage      |
|     - Dispatches Actions:                                                     |
|         * GitHub API: Adds labels / posts comments via REST                   |
|         * Slack: Posts interactive Block Kit alert                            |
|     - Logs full execution telemetry into `event_audit_logs`                   |
|                                                                               |
|  3. Management Control Plane (`DashboardController`)                          |
|     - Authenticated GitHub OAuth2 Sign-In                                     |
|     - Interactive Rule Builder (Create, toggle, delete rules)                 |
|     - Live Observability: Audit trail with execution latency & statuses       |
+-------------------------------------------------------------------------------+
                                     |
                                     v
                           +-------------------+
                           |  MySQL Database   |
                           +-------------------+
```

---

## Key Reliability & Security Features

1. **Idempotency & Replay Protection**:
   GitHub delivers events on an *at-least-once* basis. The engine enforces a database-backed unique index on `delivery_guid`. Duplicate deliveries are caught and acknowledged immediately with HTTP `200 OK` without triggering duplicate downstream actions.
2. **Timing-Attack Resilient Authentication**:
   Webhook signatures (`X-Hub-Signature-256`) are verified using constant-time byte comparison (`MessageDigest.isEqual()`). Forged payloads are rejected with HTTP `401 Unauthorized`.
3. **Decoupled Fast-ACK Pattern**:
   GitHub webhooks timeout after 10 seconds. The ingestion controller finishes persistence in `< 100ms`, sends HTTP `202 Accepted`, and passes execution to a background `@Async` worker thread pool.
4. **Infinite Recursion Guard**:
   Events originating from `sender.type == "Bot"` or `sender.login.endsWith("[bot]")` are suppressed to prevent infinite comment-trigger loops.

---

## Tech Stack

- **Language:** Java 17
- **Framework:** Spring Boot 3.x
- **Persistence:** Spring Data JPA / Hibernate, MySQL 8.x
- **Security:** Spring Security & Spring OAuth2 Client
- **Templates & UI:** Thymeleaf, Tailwind CSS
- **AI Integration:** Google Gemini 1.5 Flash (via Google AI Studio free tier)
- **External APIs:** GitHub REST API v3, Slack Incoming Webhooks

---

## Local Setup & Quickstart

### Prerequisites
- Java 17+
- MySQL 8.0 running locally (or free Aiven/TiDB cloud instance)
- Maven Wrapper (included: `./mvnw` or `mvnw.cmd`)

### 1. Clone & Configure Environment

Create an `.env` file or export the following environment variables:

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=github_bot
DB_USERNAME=root
DB_PASSWORD=your_mysql_password

# Webhook Secret (Set the same secret in GitHub repo webhook settings)
GITHUB_WEBHOOK_SECRET=your_webhook_secret_key

# GitHub Automation (For issue labels and comment write-backs)
GITHUB_BOT_TOKEN=ghp_your_github_personal_access_token_or_app_token

# GitHub OAuth2 (For Dashboard Sign-in)
GITHUB_CLIENT_ID=your_github_oauth_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_client_secret

# Slack Notifications
SLACK_WEBHOOK_URL=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK

# Gemini AI (Optional stretch goal)
GEMINI_API_KEY=your_gemini_api_key
```

### 2. Run the Application

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080/`.

---

## Testing Webhooks Locally

You can forward live webhooks to your local machine using **Smee.io** or **ngrok**:

1. Go to [smee.io](https://smee.io) and click **Start a new channel**.
2. Copy your Smee channel URL (e.g. `https://smee.io/abc123xyz`).
3. In your GitHub Repository:
   - Go to **Settings** > **Webhooks** > **Add webhook**.
   - **Payload URL:** `https://smee.io/abc123xyz`
   - **Content type:** `application/json`
   - **Secret:** Enter your configured `GITHUB_WEBHOOK_SECRET`.
   - **Events:** Select *Issues* and *Pull requests*.
4. Run the Smee client locally:
   ```bash
   npx smee-client -u https://smee.io/abc123xyz -t http://localhost:8080/api/webhooks/github
   ```

---

## Testing with Simulated Requests (PowerShell)

You can verify HMAC validation and idempotency directly using this script:

```powershell
$secret = "development_secret_token"
$payload = '{"action":"opened","issue":{"number":101,"title":"[bug] Critical login crash","body":"Steps to reproduce...","html_url":"https://github.com/my-org/repo/issues/101"},"repository":{"full_name":"my-org/repo"},"sender":{"login":"test-user","type":"User"}}'

$hmac = New-Object System.Security.Cryptography.HMACSHA256
$hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
$sigBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($payload))
$signature = "sha256=" + [System.BitConverter]::ToString($sigBytes).Replace("-","").ToLower()

$headers = @{
    "Content-Type" = "application/json"
    "X-GitHub-Event" = "issues"
    "X-GitHub-Delivery" = [System.Guid]::NewGuid().ToString()
    "X-Hub-Signature-256" = $signature
}

Invoke-RestMethod -Uri "http://localhost:8080/api/webhooks/github" -Method Post -Body $payload -Headers $headers
```

---

## Deployment (Zero-Cost Hosting)

1. **Database:** Deploy a free MySQL instance on [Aiven](https://aiven.io/) (free MySQL tier, no credit card required).
2. **Backend:** Deploy on [Render](https://render.com/) as a Web Service:
   - Build Command: `./mvnw clean package -DskipTests`
   - Start Command: `java -jar target/*.jar`
   - Set the environment variables in Render's dashboard.
