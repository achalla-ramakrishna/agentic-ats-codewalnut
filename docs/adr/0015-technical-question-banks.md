# ADR-0015: Technical question banks by experience level

- **Status**: accepted
- **Date**: 2026-10-03
- **Builds on**: ADR-0014 (question bank and paper builder)

## Context

After the aptitude bank, CodeWalnut wants tests for the stacks it hires for
(Java, Python, JavaScript, React, Angular, SQL) and wants to pitch them at
the candidate's experience: freshers, people with 1–3 years, and people with
3+ years. Unlike aptitude, technical questions can't be generated from
numbers: they need hand-written questions, code snippets and careful
distractors.

## Decision

- **Areas**: `Assessment.Category` gains REACT and ANGULAR (JAVA, PYTHON,
  JAVASCRIPT and SQL already existed). Every bank question has an area.
- **Experience bands instead of sections**: technical areas use the
  sections FUNDAMENTALS ("Fundamentals", for freshers), PRACTICAL ("Applied",
  1–3 years) and ADVANCED ("Advanced", 3+ years). Aptitude keeps numerical /
  logical / verbal. The server rejects a section that doesn't belong to the
  area. Results show scores per band, the same way aptitude shows sections.
- **Topics**: 10–12 topics per area, at least three per band, each with a
  "covers" line, shown in the topic guide (with the band's audience).
- **Questions are hand-written text files** (`resources/bank/tech/<area>.txt`)
  in a small line format: `@topic`, `Q E|M|H`, `|` code lines, one `+`
  answer, three `-` distractors and an `=` explanation. A parser fails the
  build's tests with the file and line number on any mistake.
- **792 starter questions**: 12 per topic (4 easy, 4 medium, 4 hard).
  Code-output answers were checked by running every snippet (javac/java,
  python3, node, MySQL 8).
- **Distractor quality is tested**: hand-written multiple choice tends to
  make the right answer the longest option. A test fails if more than 10% of
  questions have an answer clearly longer (≥ 25%) than every distractor.
- **Stable keys from content**: a question's key is a hash of its prompt and
  code, so editing a question in the file adds the new version and archives
  the old one on start-up; tests that copied the old one are unchanged.
- **Presets per area**: freshers (20 fundamentals questions, 30 minutes),
  1–3 years and 3+ years (25 questions, 40 minutes, weighted to the higher
  bands). Build by topics works for every area.
- **AI drafts** can add questions to any area and band; they wait for review
  as before, with the band's audience in the prompt.

## Consequences

- Recruiters build a "Java — 1 to 3 years" paper in one click, or pick
  topics and a difficulty mix.
- 12 questions per topic repeat sooner than the generated aptitude topics;
  grow the bank with AI drafts (reviewed) or by editing the files.
- Practical coding (writing and running code) is not covered; it needs a
  sandboxed runner and is a separate decision.
