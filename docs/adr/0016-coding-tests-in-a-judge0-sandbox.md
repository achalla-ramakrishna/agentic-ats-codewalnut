# ADR-0016: Coding tests, run in a self-hosted Judge0 sandbox

- **Status**: accepted
- **Date**: 2026-10-04
- **Builds on**: ADR-0011 (built-in tests), ADR-0014/0015 (question bank)

## Context

Multiple-choice tests show what a candidate knows, not whether they can write
working code. Recruiters want candidates to write real programs in the test,
graded automatically. Running a stranger's code is dangerous: it must never run
on the app server.

## Decision

- **A new question kind, CODING.** The candidate writes a program in Java,
  Python, JavaScript (Node) or C++ that reads the input from stdin and prints the
  answer. The public spec (`coding_json`: languages, starter code, sample tests,
  time and memory limits, input/output format) is shown to candidates; hidden
  test cases live in `answer_json`, which never reaches a candidate response.
- **Sandbox: self-hosted Judge0 CE** (chosen by the business over a paid hosted
  API and over browser-only running, which candidates could tamper with). The app
  talks to it only through `client/CodeRunner` (`Judge0CodeRunner`), sending
  source and inputs, never expected outputs; the app compares output itself
  (trailing spaces and blank lines ignored). Judge0 needs privileged containers,
  which Railway doesn't allow, so it runs on its own small VM
  (`docs/deploy-judge0.md`). `ATS_CODE_RUNNER_URL` unset → coding questions can be
  written but not run (`DisabledCodeRunner`); tests use `FakeCodeRunner` (dev
  profile only).
- **Run on samples while taking the test**: at most 30 runs per question and 150
  per test, only while the test is open. Staff can run a solution against every
  test ("Try a solution") to check a question before sending it.
- **Grading after submit, in the background.** Submitting marks the test
  `grading = PENDING`; after the commit a worker runs each coding answer against
  samples and hidden tests and records per-case results. Points are partial:
  points × tests passed ÷ tests. The final score, history entry and team-chat note
  appear when grading is DONE. If Judge0 is down, grading is retried every
  minute (and after a restart) and marked FAILED after 30 attempts with a team
  note; staff can click **Grade again**.
- **Built-in problems**: 60 problems in `resources/bank/coding/*.yml` across 12
  topics (basics to dynamic programming and graphs), area CODING, banded like
  other technical areas (fundamentals / applied / advanced). Expected outputs
  were produced by reference solutions (kept in `src/test/resources`, not
  shipped) and cross-checked against brute force; a test re-runs them when
  python3 is available. Points 5 / 10 / 15 by difficulty. Developer role tests
  add one coding problem per level (+15 / 25 / 35 minutes).
- **Integrity signals**: the browser reports tab switches and pastes (counts
  only); staff see them next to the result as prompts to ask about, never as an
  automatic penalty.
- **Editor**: a small dependency-free code editor (line numbers, Tab indent,
  auto-indent; Esc then Tab leaves it) rather than a large editor package.

## Consequences

- One more piece of infrastructure to run and patch (the Judge0 VM); without it
  coding questions don't work, and the editor says so.
- Judge0 CE 1.13 ships older runtimes (Java 13, Python 3.8, Node 12, GCC 9);
  starter code avoids newer language features. Language ids are configurable
  (`ats.coding.language-ids.*`) for a newer Judge0 image.
- Hidden tests can't be seen by candidates, but a determined candidate could
  still share a problem after taking it; rotate problems by building papers from
  the bank (random picks per topic and level).
