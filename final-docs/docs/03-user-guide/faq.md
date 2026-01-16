# FAQ & Troubleshooting

## Frequently Asked Questions

### General

**Q: What does the platform do?**

A: It verifies textual claims using a RAG pipeline (Weaviate + Vertex Gemini), returns a verdict/explanation, and keeps conversation history.

---

**Q: Where does the evidence come from?**

A: From the news-collector service, which ingests RSS/API sources, chunks the text, embeds it via the NLP service, and stores vectors in Weaviate.

---

**Q: Why do some obvious facts return "unclear"?**

A: The system focuses on **recent news coverage**. If the indexed corpus does not contain fresh articles about your claim (e.g., static facts like landmark locations), the model will answer "unclear" because it has no current evidence to ground the response.

---

**Q: Why is bias sometimes empty?**

A: Bias metadata depends on MBFC sync. Run /admin/mbfc/sync (collector) with a valid RapidAPI key to enrich sources.

---

### Access & Configuration

**Q: Do I need an account?**

A: Yes. Use `/register` (UI) or `/api/auth/register` (API) to create a USER account, then sign in to access the claim pages.

---

**Q: Which credentials are required?**

A: App credentials for login (username/password), plus service credentials for Vertex, NewsAPI, MBFC/RapidAPI, Postgres, and Weaviate. All service credentials are configured via environment variables.

---

### Features

**Q: How many evidence snippets are returned?**

A: Controlled by APP_SEARCH_TOP_K (default 5). The backend trims each snippet to 400 characters.

---

**Q: Can I change the model?**

A: Yes. Set VERTEX_MODEL_NAME in the backend and NLP_VERTEX_MODEL in the NLP service.

---

## Troubleshooting

### Common Issues

| Problem | Possible Cause | Solution |
|---------|---------------|----------|
| Verify returns 500 | Missing Vertex credentials or Weaviate unreachable | Check env vars, service logs, and correlation ID in response |
| Follow-up returns error | Empty question or claim ID not found | Provide non-empty text; ensure claim exists |
| Bias text empty | MBFC data missing | Run MBFC sync and re-run bias analysis |
| Ingestion stuck | Task lease held or source blocked | Check ingestion_logs status and block reason; rerun /ingestion/run |
| No evidence found | Corpus empty | Trigger /ingestion/run and wait for indexing |

### Error Messages

| Error Code/Message | Meaning | How to Fix |
|-------------------|---------|------------|
| "Claim must not be empty" | Validation failed on /verify | Shorten/clean the claim |
| 409 on /ingestion/run | Run already in progress | Wait for completion or abort via admin API |
| 503 "Embedding generation failed" | Vertex embed error | Check NLP logs and credentials; use fake embeddings for dev |

### Browser-Specific Issues

| Browser | Known Issue | Workaround |
|---------|-------------|------------|
| Chrome/Firefox | None observed | - |
| Safari | May cache aggressively | Hard refresh (Cmd+Shift+R) |

## Getting Help

### Self-Service Resources

- [Documentation](../index.md)
- Inspect service logs with correlation IDs returned in responses
- NLP metrics at /metrics

### Contact Support

| Channel | Response Time | Best For |
|---------|--------------|----------|
| Email/Chat (internal) | Within team hours | Environment config, credential issues |

### Reporting Bugs

When reporting a bug, please include:

1. **Correlation ID** (from response header/body)
2. **Steps to reproduce**
3. **Expected vs actual behavior**
4. **Logs or screenshots**
5. **Service versions** (Git commit/date)

Submit bug reports through your team channel or issue tracker.
