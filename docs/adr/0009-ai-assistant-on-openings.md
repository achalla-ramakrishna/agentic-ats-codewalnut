# ADR-0009: AI assistant on the opening page — proposes, a person applies

- **Status**: accepted
- **Date**: 2026-10-02
- **Builds on**: AGENTS.md "AI is advisory", docs/features/ai-assistance.md (AI-05…AI-09)

## Context

Recruiters want to type what happened — "sagar, sucheth and amogh are
shortlisted" — instead of changing each candidate's stage by hand, with
names typed loosely (first names, typos). The house rule is that no code
path may reject or advance a candidate from model output alone.

## Decision

- **Claude via the official Anthropic Java SDK** (`com.anthropic:anthropic-java`),
  model `claude-opus-5-5` (overridable with `ats.assistant.model`), with
  **structured output**: the reply is a JSON object matching the
  `AssistantPlan` records (summary, actions, unresolved names), so there is
  nothing to parse by hand. Server-side refusal **fallbacks** are on
  (`fallbacks: "default"`, beta `server-side-fallback-2026-07-01`); a refusal
  that still comes back is shown as "declined, please rephrase".
- **Proposals only.** The server checks every proposed action against the
  opening: the application must be in this opening, the stage must exist,
  duplicates and no-op moves are dropped, unknown actions are ignored. Names
  that match several people (or nobody) come back as questions. Nothing is
  changed by the assistant endpoint.
- **A person applies.** The UI shows the plan labelled "Suggested by AI",
  with a checkbox per change, a choice for ambiguous names and a required
  reason for rejections; "Apply" calls the same stage/note endpoints as
  doing it by hand, so history, permissions and rules are unchanged.
- **Minimum data to the provider**: opening title, stage names, and each
  candidate's application id, name and current stage — no emails, phones,
  documents or notes. Candidate names are framed as data, not instructions;
  even if one tried to steer the model, the result is only a proposal that
  is validated and shown to a person.
- **Logged**: each suggestion is in the audit log (counts, not the text).
  Rate-limited to 60 per person per hour.
- **Configuration**: `ANTHROPIC_API_KEY`. Without it the assistant is off and
  the button doesn't show. Dev and demo use a small rule-based stand-in so no
  data leaves the app.

## Consequences

- Recruiters can update many candidates in one sentence, with the same
  checks as by hand.
- Cost per instruction is small (one request with the candidate list).
- Only stage moves and notes for now; emails, interviews and searches by
  description are possible next steps on the same pattern.
