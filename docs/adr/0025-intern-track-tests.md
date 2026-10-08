# ADR-0025: Intern track tests and selecting candidates by background

- **Status**: accepted
- **Date**: 2026-10-08
- **Relates to**: docs/features/assessments.md (ASMT-39), ADR-0015 (technical banks), ADR-0010 (résumé readings)

## Context

CodeWalnut screens around 100 software engineering intern applicants at a time.
Their résumés point to one of three stacks (Java, Python or MERN), and the
interview question set used for interns covers the language, REST APIs and
HTTP, HTML/CSS, SQL scenario queries, Git/Docker/Kubernetes basics and simple
coding. Sending one generic test wastes the candidate's time and ours; picking
each candidate's stack by hand for 100 people is slow.

## Decision

- **Three role tests, not a new feature**: `se-intern-java`, `se-intern-python`
  and `se-intern-mern` in `bank/Roles`, fresher only, built from the existing
  bank: aptitude, 7 questions on the track's language, a new small
  **Web, APIs & tools** area (fundamentals only), SQL with a new *Scenario
  queries* topic, DSA and one coding problem. 75 minutes, pass mark 60%.
  A separate 30-question intern aptitude pattern for an aptitude-only round.
- **Background from the résumé reading**: `TechBackground` scores keywords in the
  AI's existing reading (skills count double; experience, projects and headline
  once). A track wins when it is at least twice the next; otherwise Mixed.
  JavaScript never counts as Java. No new AI call and no stored field.
- **Advisory only**: Send to candidates shows the background as a badge and
  offers "Select everyone with a … background"; staff can still tick anyone and
  nothing is sent until they click Send.

## Consequences

- Résumés not yet read by the AI have no background; the panel says how many
  and points to Analyze résumés.
- Keyword rules can mislabel unusual résumés (e.g. "Spring" as a season); the
  badge shows its evidence on hover so staff can check.
- The Web, APIs & tools area is exempt from the "every band, 10 topics" bank
  rule because it is meant for freshers only.
