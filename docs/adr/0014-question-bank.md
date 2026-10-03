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
- **Built-in bank, v2** (1,300 questions): 26 topics × 50 (17 easy, 17
  medium, 16 hard). Numerical: percentages, profit and loss, time and work,
  speed/trains, interest, ratio, averages, number series, probability, data
  interpretation (bar, line, pie, table). Logical: coding-decoding,
  directions, clocks, Venn diagrams, blood relations, syllogisms,
  arrangements, and four non-verbal types (figure series, mirror/water
  images, pattern matrix, odd one out). Verbal: synonyms, antonyms, sentence
  completion, error spotting, reading comprehension. Numbers and pictures
  are generated from seeded values; reasoning answers come from small
  solvers (blood relations read off a generated family tree, syllogisms
  checked against every Venn model, seating and height puzzles used only
  when the clues allow exactly one order); verbal items are curated lists.
  Generation is deterministic and each topic's 50 questions are unique by
  prompt and picture. Each topic carries a description and an example,
  shown as the **topic guide**. Loaded at start-up by stable key (never
  overwrites a row); built-in rows whose keys the current bank no longer
  makes (the first 173-question bank) are archived. Tests keep their copies.
- **Build by topics**: tick topics, questions per topic, a difficulty mix
  (balanced 40/40/20, mostly easy, challenging, or one level only) and an
  order (easy → hard, hard → easy, topic by topic, shuffled). The mix is
  applied across the paper and dealt out so every topic gets a spread of
  levels. Section counts and presets remain as the other way to build.
- **Section-wise scores** in results (e.g. Numerical ability 14 / 20).

## Consequences

- A TCS-style 65-question paper takes one click; every picture question's
  answer is correct by construction.
- Verbal items are a fixed list (17–19 per level per topic); papers built often will repeat them sooner than generated topics.
- Per-section timers and negative marking are not modelled yet.
