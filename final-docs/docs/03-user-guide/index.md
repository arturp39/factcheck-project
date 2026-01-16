# 3. User Guide

This section provides instructions for end users on how to use the application.

## Contents

- [Features Walkthrough](features.md)
- [FAQ & Troubleshooting](faq.md)

## Getting Started

### System Requirements

| Requirement | Minimum | Recommended |
|-------------|---------|-------------|
| **Browser** | Chrome/Firefox/Safari modern | Latest stable |
| **Screen Resolution** | 1280x720 | 1920x1080 |
| **Internet** | Required (calls Vertex/Weaviate) | Stable connection |
| **Device** | Desktop/laptop | Desktop |

### Accessing the Application

1. Open your web browser.
2. Navigate to: **https://factcheck-backend-804697237544.us-central1.run.app/**.
3. You are redirected to **/login** if not authenticated.
4. Sign in or create an account at **/register**.

### First Launch

#### Step 1: Sign in or register

1. Enter a username and password (min 8 characters).
2. Click **Sign in** or **Register**.
3. You are redirected to the claim input page.

#### Step 2: Submit your first claim

1. Paste or type a single-sentence factual claim (max 400 characters).
2. Click **Verify**.
3. The app redirects to the result page showing verdict, explanation, and evidence.

#### Step 3: Inspect evidence and verdict

- Evidence snippets show title, source, publish date, and a short snippet.
- Use the bias section (once requested) to see MBFC context for sources.

#### Step 4: Continue the conversation

- Ask follow-up questions in the follow-up form below the verdict.
- Re-run bias analysis with the **Bias** button.

## Quick Start Guide

| Task | How To |
|------|--------|
| Sign in | Open /login and submit your credentials |
| Register | Open /register and create a new account |
| Verify a claim | Enter text on / and click **Verify** |
| Ask a follow-up | On the result page, type a question under "Follow-up" and submit |
| Run bias analysis | Click **Bias** on the result page to generate bias commentary |
| Review history | Use **History** on the result page or the recent claims list |

## User Roles

| Role | Permissions | Access Level |
|------|-------------|--------------|
| **USER** | Submit claims, view evidence, ask follow-ups, bias analysis | UI access |
| **ADMIN** | All USER permissions plus access to `/api/**` and admin endpoints | UI + API |
