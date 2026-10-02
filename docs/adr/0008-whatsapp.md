# ADR-0008: WhatsApp from the candidate conversation — click-to-chat now, Business API when set up

- **Status**: accepted
- **Date**: 2026-10-02
- **Builds on**: ADR-0006 (conversations and email)

## Context

Recruiters message candidates on WhatsApp ("can you send me your Aadhaar
card?") from their own phones, so those conversations are outside the ATS.
They want to write once in the candidate's conversation for that job and
have it go out by email and/or WhatsApp.

WhatsApp only allows fully automatic sending through the WhatsApp Business
Platform (Meta Cloud API). That needs Meta Business verification, a
dedicated phone number, a pre-approved message template for messages that
start a conversation (free text is only allowed within 24 hours of the
candidate's last message), and per-message charges.

## Decision

- **One composer, several channels.** In "Chat with candidate" the recruiter
  writes once and ticks Email and/or WhatsApp. The message is always saved
  in the conversation and shown on the candidate's page.
- **Without the Business API (default):** ticking WhatsApp opens WhatsApp
  (app or web) via a `wa.me` click-to-chat link with the candidate's number
  and the message ready; the recruiter presses Send. The ATS records it as
  "WhatsApp opened", with history and audit entries. Replies arrive in the
  recruiter's WhatsApp, not the ATS. The new tab is opened during the click
  (before the network call) so pop-up blockers allow it.
- **With the Business API** (`WHATSAPP_ACCESS_TOKEN` + `WHATSAPP_PHONE_NUMBER_ID`):
  the server sends from CodeWalnut's WhatsApp number using the approved
  template (first name, role, message as one line plus the candidate-page
  link), or as free text inside the 24-hour window. Synchronous, like email:
  if WhatsApp alone fails, nothing is saved; if email went out and WhatsApp
  failed, the message is saved and the recruiter sees a warning.
- **Replies and receipts** (`WHATSAPP_VERIFY_TOKEN` + `WHATSAPP_APP_SECRET`):
  Meta calls `/webhooks/whatsapp`. Every call is checked against the
  `X-Hub-Signature-256` HMAC of the raw body; duplicates are ignored by
  WhatsApp message id. Text replies are matched to the candidate by mobile
  number and land in their latest application's conversation (so they show
  as "waiting for your reply"); delivery receipts update the message's
  status (sent, delivered, read, failed). Files sent on WhatsApp are not
  downloaded; the candidate is asked to upload documents on their page,
  where kinds, checks and access rules apply (BGV-04, BGV-05).
- Numbers: 10-digit numbers get the default country code (91); `+`/`00`
  numbers are kept. Dev and demo use a fake WhatsApp client.

## Consequences

- Works today with no setup; the Business API is a configuration change, not
  a code change.
- Click-to-chat can't confirm the recruiter actually pressed Send; the
  status says "opened", not "sent".
- Business API costs per conversation and needs a template approved by Meta
  (Utility category) — see docs/deploy-railway.md.
