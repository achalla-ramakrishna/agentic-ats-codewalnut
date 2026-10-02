/**
 * Release notes shown under "What's new", newest first. Add an entry in the same PR as any
 * change users will notice (see AGENTS.md). Keep it short, in plain words, with the steps to
 * try it. `capability` hides entries from people who can't use the feature.
 */
export interface WhatsNewEntry {
  /** Stable and unique, e.g. "2026-10-02-whatsapp". Never reuse or change one. */
  id: string
  date: string
  title: string
  summary: string
  steps: string[]
  link?: { to: string; label: string }
  capability?: string
}

export const WHATS_NEW: WhatsNewEntry[] = [
  {
    id: '2026-10-02-default-stage-applied',
    date: '2026-10-02',
    title: 'New candidates start at Applied / Sourced',
    summary: 'Imported and hand-added candidates now start at Applied / Sourced instead of Interviewed. You can still pick another stage before adding or importing.',
    steps: ['Openings → Import from spreadsheet or Add candidate: the Stage box now starts at Applied / Sourced.'],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-02-opening-search',
    date: '2026-10-02',
    title: 'Search candidates in an opening',
    summary: 'No more scrolling: type a name, email or phone number at the top of the candidate list to find someone in that opening.',
    steps: [
      'Open an opening and type in the search box above the stage buttons (or press / to jump there).',
      'It works together with the stage buttons, e.g. Interviewed + “kumar”.',
      'When only one candidate matches, press Enter to open them. Esc clears the search.',
    ],
    capability: 'VIEW_CANDIDATES',
  },
  {
    id: '2026-10-02-whatsapp',
    date: '2026-10-02',
    title: 'WhatsApp from the candidate chat',
    summary: 'Write once in a candidate’s chat and send it by email, WhatsApp or both. Everything stays in the ATS for the team to see.',
    steps: [
      'Open an opening, click a candidate, then the Chat with candidate tab.',
      'Type your message or pick a template.',
      'Tick Email and/or WhatsApp, then Send.',
      'WhatsApp opens with the message ready: press Send there. The ATS records it in the chat and history.',
    ],
    capability: 'MESSAGE_CANDIDATES',
  },
  {
    id: '2026-10-02-whats-new',
    date: '2026-10-02',
    title: "What's new",
    summary: 'This page. New features are listed here with how to use them, and the menu shows a badge when there is something new.',
    steps: ['Open What’s new from the menu whenever the badge appears.'],
  },
  {
    id: '2026-10-01-client-access',
    date: '2026-10-01',
    title: 'Clients can sign in and get what you share',
    summary:
      'A client’s hiring manager (e.g. Blend) signs in with Google and sees only the candidates and details you share with them, and can message you about each one.',
    steps: [
      'Clients → add the client’s contact by email.',
      'Open the candidate → Profile tab → Share with <client>…, tick contact details, profile and documents → Share.',
      'You can change or stop sharing at any time, and see when they last viewed it.',
      'Their questions appear in the Chat with <client> tab and under Messages.',
    ],
    link: { to: '/clients', label: 'Go to Clients' },
    capability: 'SHARE_WITH_CLIENTS',
  },
  {
    id: '2026-10-01-bgv',
    date: '2026-10-01',
    title: 'Candidate profiles and background-verification documents',
    summary:
      'Full candidate profiles (date of birth, addresses, education, emergency contact) and documents for background checks: masked Aadhaar, PAN, degree, photo.',
    steps: [
      'Open a candidate → Profile tab → Edit profile.',
      'Background verification → Request from candidate, tick the documents → Request. Then send the ready-made email or WhatsApp.',
      'The candidate uploads them on their candidate page; you see them here with “uploaded by the candidate”.',
      'Aadhaar and PAN are visible only to Admins, Recruiters and Account Managers.',
    ],
    capability: 'VIEW_CANDIDATES',
  },
  {
    id: '2026-09-29-messages',
    date: '2026-09-29',
    title: 'Chat with candidates, team chat and email',
    summary:
      'Every candidate has a chat with the candidate (they reply from their candidate page) and an internal team chat. Emails go from your Gmail with templates.',
    steps: [
      'Open a candidate → Chat with candidate or Team chat.',
      'After moving someone to Shortlisted, Selected or Rejected, click “Email <name>” to send the matching template.',
      'Messages in the menu shows conversations waiting for your reply.',
    ],
    link: { to: '/messages', label: 'Open Messages' },
    capability: 'MESSAGE_CANDIDATES',
  },
  {
    id: '2026-09-29-interviews',
    date: '2026-09-29',
    title: 'Schedule interviews with Google Meet',
    summary: 'Schedule from the candidate panel: it goes on your Google Calendar with a Meet link, and Google emails the invite to the candidate and interviewers.',
    steps: [
      'Open a candidate → Schedule interview.',
      'The first time in a session, click Connect Google (Calendar & Gmail).',
      'Pick date, time, interviewers and send. Upcoming interviews are under Interviews.',
    ],
    link: { to: '/interviews', label: 'Open Interviews' },
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-09-29-job-links',
    date: '2026-09-29',
    title: 'Shareable job links',
    summary: 'Write the job description, turn on the link and share it. Candidates sign in with Google, apply and upload their résumé.',
    steps: [
      'Open an opening → Job details & share link → fill in the details.',
      'Turn link on → Copy link, and share it on WhatsApp, email or LinkedIn.',
      'Applicants appear at Applied / Sourced with their résumé.',
    ],
    link: { to: '/jobs', label: 'Go to Openings' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-09-28-tracker',
    date: '2026-09-28',
    title: 'Hiring tracker',
    summary: 'Openings, candidates moving through stages, notes, two résumés per candidate, spreadsheet import and a dashboard.',
    steps: ['Openings → New opening.', 'Add candidates one by one or paste a table from Excel / Google Sheets with Import.'],
    link: { to: '/jobs', label: 'Go to Openings' },
    capability: 'VIEW_JOBS',
  },
]

export function entriesFor(capabilities: string[]): WhatsNewEntry[] {
  return WHATS_NEW.filter((e) => !e.capability || capabilities.includes(e.capability))
}
