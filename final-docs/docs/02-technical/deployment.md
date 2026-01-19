# Deployment & DevOps

## Infrastructure

### Deployment Architecture

**Production (GCP)**
`
Cloud Run: backend, news-collector, nlp-service
Cloud SQL: Postgres (backend DB), Postgres (collector DB)
GCE VM: Weaviate in Docker
Vertex AI: Gemini + gemini-embedding-001
External APIs: NewsAPI, MBFC (RapidAPI)
Cloud Scheduler: triggers /ingestion/run daily at 00:00 (Cloud Run is request-driven)
Cloud Tasks: fan-out ingestion tasks to /ingestion/task in the collector (gcp profile)
`

**Local/dev**
`
Docker Compose (infra/docker-compose.yml)
  +- backend (8080) + Postgres (5433)
  +- news-collector (8081) + Postgres (5432)
  +- nlp-service (8000)
  +- weaviate (8082)
`

### Environments

| Environment | URL | Branch |
|-------------|-----|--------|
| **Development** | Local docker-compose stack | feature/* |
| **Staging** | Not configured (optional) | - |
| **Production** | https://factcheck-backend-804697237544.us-central1.run.app | main |

## CI/CD Summary

Cloud Build YAMLs build container images per service and deploy to Cloud Run.

## Environment Variables (core)

| Variable | Description | Example |
|----------|-------------|---------|
| SPRING_DATASOURCE_URL/USERNAME/PASSWORD | Backend DB | Cloud SQL JDBC URL |
| COLLECTOR_DATASOURCE_URL/USERNAME/PASSWORD | Collector DB | Cloud SQL JDBC URL |
| APP_ADMIN_NAME / APP_ADMIN_PASSWORD | Bootstrap admin | admin / change_me |
| APP_JWT_SECRET | JWT signing secret | 32+ chars |
| WEAVIATE_BASE_URL | Weaviate endpoint | http://weaviate:8082 |
| NLP_SERVICE_URL | NLP service URL | http://nlp-service:8000 |
| NLP_SERVICE_AUTH_ENABLED | Enable Cloud Run IAM auth for NLP | false |
| NLP_SERVICE_AUTH_AUDIENCE | NLP Cloud Run URL (ID token audience) | https://factcheck-nlp-service-... |
| NLP_SERVICE_RETRY_MAX_ATTEMPTS | NLP retry attempts for 429/503 | 3 |
| NLP_SERVICE_RETRY_INITIAL_BACKOFF_MS | NLP retry backoff start | 500 |
| NLP_SERVICE_RETRY_MAX_BACKOFF_MS | NLP retry backoff cap | 5000 |
| CHUNKING_SEMANTIC_MAX_SENTENCES_PER_REQUEST | Collector semantic embedding batch size | 100 |
| VERTEX_PROJECT_ID | GCP project | my-project |
| CLOUD_TASKS_PROJECT_ID | GCP project for Cloud Tasks | my-project |
| CLOUD_TASKS_LOCATION | Cloud Tasks region | us-central1 |
| CLOUD_TASKS_QUEUE | Cloud Tasks queue name | ingestion-queue |
| CLOUD_TASKS_TARGET_URL | Collector /ingestion/task URL | https://factcheck-news-collector.../ingestion/task |
| CLOUD_TASKS_SERVICE_ACCOUNT_EMAIL | OIDC service account for tasks | service-account@project.iam.gserviceaccount.com |
| NEWSAPI_API_KEY / RAPIDAPI_KEY | External APIs | *** |

**Secrets Management:** Use .env for local dev; in GCP store secrets in Secret Manager or Cloud Run service variables.

## How to Run Locally

`
cd infra
cp .env.example .env
# fill Vertex/NewsAPI/MBFC keys or use fake embeddings

docker-compose up -d --build
`

### Verify Installation

1. Open https://factcheck-backend-804697237544.us-central1.run.app and register/sign in
2. Submit a sample claim
3. Check API: curl https://factcheck-backend-804697237544.us-central1.run.app/api/claims?page=0&size=5 with a Bearer token
4. NLP health: curl https://factcheck-nlp-service-804697237544.us-central1.run.app/health
5. Collector health: curl https://factcheck-news-collector-804697237544.us-central1.run.app/actuator/health
