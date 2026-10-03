# ADR-0014: Question bank with app-drawn pictures, and a test-paper builder

- **Status**: accepted
- **Date**: 2026-10-03
- **Builds on**: ADR-0011 (built-in tests)

## Context

CodeWalnut wants aptitude tests like the ones freshers face in campus drives
(TCS NQT, Infosys, Wipro NLTH, Cognizant GenC, Accenture): numerical ability
with data interpretation, logical reasoning including picture-based
"non-verbal" puzzles, and verbal ability, at a chosen mix of difficulty.
Later the same for Java, Python, React, Angular, SQL and coding, by role.

## Decision

- **A bank separate from tests** (`bank_question`): each question has an
  area (APTITUDE now; the other areas reuse the same table), a section, a
  topic, a difficulty (easy / medium / hard), points (1 / 2 / 3), and an
  optional picture and option pictures. Statuses: ACTIVE, REVIEW (AI drafts
  waiting for a person), ARCHIVED.
- **Papers copy questions** out of the bank into a draft test (section,
  topic, difficulty and pictures included), so bank edits never change a test
  already sent. The builder takes N easy / medium / hard per section, picks
  least-used questions first at random, and orders them easy → hard, section
  by section, or shuffled. Presets follow published patterns (TCS NQT
  Foundation, Wipro NLTH, Cognizant GenC, Infosys) plus a 20-question quick
  screen; ours use one timer for the whole paper.
- **Pictures are drawn by the app** as SVG from the same numbers the answer
  is computed from: bar, line and pie charts, tables, clocks, Venn diagrams,
  trains, figure series, mirror/water images, dot matrices and odd-one-out
  shapes (options can be pictures). AI drafts give chart data, not drawings;
  the app draws them. People can also upload PNG/JPEG pictures. Every
  picture is checked (no scripts, handlers or external links; PNG/JPEG by
  content, ≤ 1 MB) and shown through `<img>`.
- **Built-in starter bank** (173 questions): generated templates (two easy,
  two medium, two hard each) for percentages, profit and loss, time and work,
  trains, interest, ratio, averages, series, probability, four kinds of data
  interpretation, coding-decoding, directions, clocks, Venn diagrams and four
  non-verbal types, plus hand-written verbal (synonyms, antonyms, sentence
  completion, error spotting, reading comprehension) and reasoning (blood
  relations, syllogisms, arrangements). Loaded once at start-up by stable
  key; never overwrites rows.
- **Section-wise scores** in results (e.g. Numerical ability 14 / 20).

## Consequences

- A TCS-style 65-question paper takes one click; every picture question's
  answer is correct by construction.
- Verbal hard questions are fewer (6); add more by hand or with AI drafts.
- Per-section timers and negative marking are not modelled yet.
