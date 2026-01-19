# Deployment & DevOps

## Infrastructure

### Deployment Architecture

**Production (GCP)**
`
Cloud Run: backend, news-collector, nlp-service
Cloud SQL: Postgres (backend DB), Postgres (collector DB)
GCE VM: Weaviate in Docker
Vertex AI: Gemini + text-embedding-004
External APIs: NewsAPI, MBFC (RapidAPI)
Cloud Scheduler: triggers /ingestion/run on a schedule (no in-app scheduler in Cloud Run)
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
| VERTEX_PROJECT_ID | GCP project | my-project |
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
