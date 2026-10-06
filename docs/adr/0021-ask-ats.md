# ADR-0021: Ask ATS — a chat over the whole ATS with read-only tools

- **Status**: accepted
- **Date**: 2026-10-06
- **Relates to**: docs/features/ai-assistance.md (ASK-01…ASK-05), ADR-0009 (assistant on an opening)

## Context

Recruiters and managers want to ask the ATS questions in plain words ("who is
shortlisted for the Java openings?", "whose feedback is pending?", "how do I
share a candidate with a client?") instead of clicking through openings,
candidates and interviews. The assistant on an opening (ADR-0009) only knows that
one opening and is built for moving candidates.

## Decision

- **A chat page, "Ask ATS"**, for all staff, with each person's saved chats.
- **Claude with tools, not a data dump**: the model gets a small set of
  read-only lookups and decides which to call (several per question if
  needed, at most 10 rounds). This scales to many openings and keeps each
  answer grounded in live data. The tools return compact JSON; the answer is
  Markdown with links into the app.
- **Permissions are the screens' own**: every tool calls the existing services
  as the asking user. A lookup they can't do returns "not allowed" to the model
  (not an audited denial), and the answer says they don't have access.
- **Read-only** (superseded by ADR-0023: actions are now proposed as cards and done only when a person confirms): there are no tools that change data. Moving stages, messaging
  and scheduling stay on the screens (the opening assistant can still propose
  stage moves for review).
- **Data minimisation**: email and phone are never sent to the model, as for the
  opening assistant (AI-29). Notes and feedback summaries are, because questions
  are about them.
- **"How do I…"** is answered from a short hand-written guide
  (`resources/assistant/ats-guide.md`) in the cached system prompt.
- **Chats stored per user** (questions and answers only), private to their
  owner. Tool results aren't stored; a follow-up re-runs the lookups, so answers
  reflect current data.
- **Offline**: production without an AI key shows Ask ATS as switched off; dev
  and demo use a keyword-based stand-in so the page can be tried.

## Consequences

- Answers cost one to a few Claude calls per question; the system prompt and
  tool list are cached.
- The model can still misread or summarise wrongly; the page says to check
  anything important, and every candidate is a link to the real record.
- New screens need a matching tool (or a guide entry) before Ask ATS can
  answer about them.
