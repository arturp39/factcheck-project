# 1. Project Overview

This section covers the business context, goals, and requirements for the project.

## Contents

- [Problem Statement & Goals](problem-and-goals.md)
- [Stakeholders & Users](stakeholders.md)
- [Scope](scope.md)
- [Features](features.md)

## Executive Summary

This project is an internal RAG platform that quickly verifies textual claims. User submit a claim, the backend retrieves evidence from a continuously updated Weaviate database, and Vertex Gemini LLM returns a verdict with explanations. Follow-up Q&A and bias analysis help editors defend the result. A dedicated ingestion service updates the database by syncing sources, respecting robots.txt, chunking, embedding with the NLP service, and enriching outlets with MBFC bias metadata. Because the system centers on **recent news**, claims without current coverage may return an "unclear" verdict.

## Key Highlights

| Aspect | Description |
|--------|-------------|
| **Problem** | Fact-checking is slow and hard to audit |
| **Solution** | Retrieval-augmented verification with stored conversations |
| **Target Users** | Regular users, editors, students |
| **Key Features** | Claim verification, evidence browsing, follow-ups, bias analysis, ingestion ops |
| **Tech Stack** | Spring Boot, FastAPI, Vertex AI, Weaviate, PostgreSQL, Docker Compose |