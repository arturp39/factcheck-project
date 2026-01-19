# Tutorial: Verify a Claim and Ask Follow-ups

Goal: walk through verifying a claim, inspecting evidence, and asking a follow-up question.

Prerequisites
- Stack running (`docker compose up --build`).
- Optional: set `X-Correlation-Id` to trace logs.

Steps
1) Login to get a JWT token
   ```bash
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"change_me"}'
   ```
   Use the returned `token` as `Authorization: Bearer <token>` in the next calls.

2) Verify a claim
   ```bash
   curl -s -X POST http://localhost:8080/api/claims/verify \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -H "X-Correlation-Id: demo-123" \
     -d '{"claim":"The Moon has no gravity."}'
   ```
   Response includes `claimId`, `verdict`, `explanation`, and `evidence`.

3) Fetch stored claim
   ```bash
   curl -s http://localhost:8080/api/claims/{CLAIM_ID} \
     -H "Authorization: Bearer $TOKEN"
   ```

4) Re-run evidence search (optional)
   ```bash
   curl -s http://localhost:8080/api/claims/{CLAIM_ID}/evidence \
     -H "Authorization: Bearer $TOKEN"
   ```

5) Ask a follow-up
   ```bash
   curl -s -X POST http://localhost:8080/api/claims/{CLAIM_ID}/followup \
     -H "Content-Type: application/json" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"question":"What sources support this verdict?"}'
   ```

6) Run bias analysis
   ```bash
   curl -s -X POST http://localhost:8080/api/claims/{CLAIM_ID}/bias \
     -H "Authorization: Bearer $TOKEN"
   ```

Edge cases
- Empty claim -> 400.
- Claim longer than `APP_CLAIM_MAX_LENGTH` -> 400.
- Follow-up question empty -> 400.
- Missing/invalid JWT -> 401/403.

What to expect
- Latency includes embedding + Weaviate + Vertex LLM; evidence size is capped by `APP_SEARCH_TOP_K`.
- Errors return `ErrorResponse` with `correlationId` for log tracing.
