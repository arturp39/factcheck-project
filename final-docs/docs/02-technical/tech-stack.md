# Technology Stack

## Stack Overview

| Layer | Technology | Version | Justification |
|-------|------------|---------|---------------|
| **Frontend** | Thymeleaf + HTML/CSS | Spring Boot 3 templates | Lightweight server-rendered UI |
| **Backend** | Java | 21 | Spring Boot baseline, type safety |
| **Framework** | Spring Boot | 3.x | REST + MVC + JPA + Actuator |
| **Database** | PostgreSQL | 15 (Cloud SQL/local) | Reliable relational storage |
| **ORM** | Spring Data JPA | - | Quick CRUD + migrations |
| **Vector DB** | Weaviate | 1.26+ | Vector search for RAG evidence |
| **NLP** | FastAPI + Vertex AI | - | Embeddings + preprocessing |
| **Deployment** | Docker + Cloud Run | - | Container-first deploys |
| **CI/CD** | Cloud Build | - | Image builds per service |

## External Services & APIs

| Service | Purpose | Pricing Model |
|---------|---------|---------------|
| Vertex AI (Gemini + embeddings) | Verdicts, follow-ups, bias analysis, embeddings | Pay-as-you-go |
| Weaviate | Vector storage/search for evidence | Self-hosted (VM) |
| NewsAPI | Publisher/source catalog | API key |
| MBFC via RapidAPI | Bias/factuality metadata | API key |
