# ADR-0012: CodeWalnut-branded résumés — AI drafts, a person edits, the app renders

- **Status**: accepted
- **Date**: 2026-10-03
- **Relates to**: docs/features/client-submissions.md (SUB-01, SUB-02), ADR-0007 (sharing), ADR-0011 (tests)

## Context

CodeWalnut sends clients its own version of each candidate's résumé: the
candidate's mobile number removed (clients go through CodeWalnut), the
content tidied, and a footer "Presented by CodeWalnut". Recruiters make
these by hand in Word today.

## Decision

- **AI draft from the original résumé**, tailored to the opening: Claude
  (structured output, `BrandedResume`: name, title line, city, email,
  summary, skill groups, sections of entries with bullets). The prompt keeps
  every fact, improves wording, never invents skills, numbers or roles, and
  leaves out phone, address, date of birth, personal details and links.
  Dev and demo copy the text section by section instead (no AI).
- **The app removes phone numbers and links itself**, on every draft and
  every edit (10+ digit runs, linkedin/github/URLs), so neither the model nor
  an editor can leak them. Email is a per-résumé toggle (on by default, as in
  CodeWalnut's current template).
- **People edit before use**: every field, skill group, section, entry and
  bullet is editable in the candidate drawer; the draft is stored per
  candidate per opening (`codewalnut_resume`).
- **Rendering is ours, not the model's**: one template, two outputs — PDF via
  openhtmltopdf (maintained `io.github` fork; HTML + CSS; Liberation Sans
  embedded, SIL OFL) and an editable Word file built directly as
  WordprocessingML. Both carry the CodeWalnut logo, consistent headings and
  the footer (`ATS_RESUME_FOOTER`).
- **CodeWalnut screening** (optional): passed CodeWalnut tests are listed
  ("Java basics test: 80% (pass mark 60%)").
- **Save** stores the PDF as the candidate's `CODEWALNUT_RESUME` document (a
  new version each time), so the existing "Share with client" sends it.

## Consequences

- A client-ready résumé in about a minute instead of half an hour of Word
  editing, with the same look every time.
- The AI can still misstate a fact; the editor says to check every line
  against the original, and the original stays attached.
- Adds openhtmltopdf + PDFBox (~10 MB) to the backend.

## Update 2026-10-06

- The PDF and Word layout now follow CodeWalnut's résumé template (a sample
  CodeWalnut résumé from the recruiting team): top accent bar, "CODE WALNUT /
  TALENT PROFILE" label, contact line, blue section headings over thin rules,
  role and dates on one line, projects with their stack in grey, education
  with the institution underneath, numbered footer. Colours and sizes are
  taken from that template; the logo is the template's higher-resolution one.
- **Email is always shown** (the per-résumé toggle is retired): clients and
  recruiters expect it on every profile. Saving a PDF without an email is
  refused.
- **One GitHub or portfolio link is kept** (normalised, e.g.
  `github.com/asha`), as in the template; LinkedIn and other social or
  contact links are still removed, and links inside the text are still
  stripped.

