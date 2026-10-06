# ADR-0023: Actions in Ask ATS are proposed by the AI and done by people

- **Status**: accepted
- **Date**: 2026-10-06
- **Builds on**: ADR-0021 (Ask ATS), ADR-0009 (assistant on an opening: propose, review, apply)

## Context

Recruiters asked Ask ATS to do things, not only answer: move candidates, add
notes, remind about tests, message candidates, share with clients. The ATS rule
so far has been that AI is advisory: it suggests and people decide.

## Decision

- **Propose, then confirm.** The model gets one extra tool, `propose_actions`.
  It never changes data; it validates each proposal and returns it as a card.
  The person clicks **Do it** (or **Do all**) to run it, or **Skip**.
- **Checked at proposal, enforced at execution.** `AskActionService.check`
  refuses impossible or unpermitted proposals up front (so cards are real
  options); execution then goes through the existing service methods
  (`TrackerService.moveStage`, `MessageService.post`, `AssessmentInviteService.remind`,
  `ClientShareService.share`, `WorkflowService.logContact`) as the clicking
  user, with their permission checks, history events and audit entries.
- **Six kinds only**: stage moves, notes, logged contacts, test reminders,
  candidate messages and client shares. Interviews (calendar invites) and offers
  stay on their screens, where the details are entered.
- **Messages are editable** on the card; the edited text is sent. WhatsApp
  without the Business API opens click-to-chat in a tab the click itself opens,
  so it needs one click per message (no batch).
- **Stored with the chat**: proposals and their outcome (status, result, who,
  when) live in the conversation JSON, each runs at most once (409 after), and
  follow-up questions see what was done.
- **Safety in the prompt and the code**: no rejection unless the person asked
  with a reason (also enforced: rejecting needs a note); messages signed with
  the asker's name; no invented dates, salaries or promises.

## Consequences

- Batch work ("remind everyone who…") becomes a few clicks while every change
  is still a person's decision, visible in history and the audit log.
- A proposal can go stale (the candidate moved meanwhile); execution re-runs the
  service checks, and a failure is shown on the card.
- New kinds of action need a validator and an executor in `AskActionService`.
