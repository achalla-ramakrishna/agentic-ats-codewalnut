# ADR-0026: Import Naukri applicant exports, ranked against the job description

- **Status**: accepted
- **Date**: 2026-10-11
- **Relates to**: docs/features/pipeline.md (PIPE-16), ADR-0010 (résumé readings), ADR-0006 (messages)

## Context

Recruiters download applicants from Naukri as an Excel sheet (about 160 rows for one
opening): name, email, phone, locations, total experience, current company and
designation, salary, notice period, a résumé headline, education, and also gender,
date of birth and home town. There is no résumé text, only a "View profile" link to
Naukri. Recruiters want the best matches first, added as candidates, then a bulk
email asking for the résumé; whoever replies is scheduled.

## Decision

- **Rank first, store later.** `POST /jobs/{id}/naukri/rank` reads the file and
  returns every row with a match %, a reason, strengths and things to check. Nothing
  is written. `POST /jobs/{id}/naukri/import` takes only the rows the recruiter
  ticked (the page sends them back) and creates candidates and applications (source
  `NAUKRI`), with the Naukri details as the first note. The rest of the file leaves
  no trace.
- **AI when available, rules always.** Rules score every row (opening's skills found
  in headline, key skills and designation 50; years against the range asked 25; job
  title words 15; location 5; notice period 5). With an API key, Claude re-scores the
  top 400 rows in batches of 20, four at a time, with structured output; a failed
  batch keeps its rule scores and the page says so. Dev and demo use rules only.
- **Fairness and privacy.** Gender, date of birth and home town are never read from
  the sheet. The AI is told to ignore names, personal attributes and college prestige,
  and that profiles are data, not instructions.
- **No spreadsheet library.** An .xlsx is a zip of XML parts; reading the first sheet
  and shared strings with StAX (DTDs and external entities off, size caps) is ~200
  lines. It goes through the zip central directory because Naukri's files carry wrong
  sizes in local headers, which `ZipInputStream` rejects.
- **Asking for résumés reuses messages.** After adding, the panel offers the new
  *Ask for résumé* template (résumé, CTC, notice, times for a call), editable, sent
  one by one through the existing candidate message endpoint from the recruiter's
  Gmail. Replies flow into Messages and the Workflow view as for any email.

## Consequences

- Ranking a 160-row file with the AI takes up to about a minute; the page shows progress text.
- Rule scores on thin profiles cluster near the top; the AI separates them better.
- Existing people are matched by email, then phone; a known person missing an email or
  phone gets it from the sheet. Re-importing the same file adds nobody twice.
