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
    id: '2026-10-04-feedback-during-interview',
    date: '2026-10-04',
    title: 'Fill in feedback during the interview',
    summary: 'The feedback form now opens 15 minutes before the interview. Rate things as you notice them, such as communication, and jot notes: it saves itself as a private draft that only you can see. Submit when the interview ends to share it with the panel.',
    steps: [
      'Open the interview from Interviews (or the candidate’s panel) and click Feedback form.',
      'Rate and take notes while you talk; “Draft saved” confirms it’s kept.',
      'At the end, choose a recommendation and Submit feedback.',
    ],
    link: { to: '/interviews', label: 'Open Interviews' },
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-10-04-give-feedback',
    date: '2026-10-04',
    title: 'Easier to find the feedback form',
    summary: 'The Interviews page now lists every interview from the last 30 days with a Give feedback button and shows whose feedback is still due. Held an interview on a Meet set up outside the app? Log it from the candidate’s panel and the feedback form is ready.',
    steps: [
      'Open Interviews in the menu and click Give feedback next to the interview.',
      'For an interview set up elsewhere: open the candidate, click “Log an interview held elsewhere”, then Feedback form.',
    ],
    link: { to: '/interviews', label: 'Open Interviews' },
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-10-04-edit-opening',
    date: '2026-10-04',
    title: 'Rename openings and clients',
    summary: 'Openings can now be renamed and moved to another client or hiring type, and clients can be renamed. If candidates in an opening are shared with its client, stop sharing them before moving it to another client.',
    steps: [
      'Open an opening and click Edit opening at the top.',
      'Change the name, hiring type, client or people needed, then Save.',
      'To rename a client, open Clients and click Rename next to its name.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-04-interview-kits',
    date: '2026-10-04',
    title: 'Interview kits from the job description',
    summary: 'One click turns an opening’s job description into an interview kit: the skills and level it asks for, the online test to send, and a round-by-round plan (screening, technical, live coding, system design, project deep-dive) with questions, answer guides and coding problems, plus a scorecard. Interviewers follow the kit, and the feedback form rates the job’s must-have skills.',
    steps: [
      'Open an opening and click Interview kit, then Generate interview kit.',
      'Check the skills, level and role; change them and Regenerate if needed.',
      'Click Create this test to make the online test; interviewers open the kit from Interviews or the feedback form.',
    ],
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-10-04-dsa-bank',
    date: '2026-10-04',
    title: 'Data structures & algorithms tests',
    summary: '750 new questions on data structures and algorithms, from Big-O, arrays and hashing for interns to trees, graphs, dynamic programming and union-find for experienced developers. About a third ask what a short Java, Python, JavaScript or C++ program prints. Fresher and junior developer role tests now include them.',
    steps: [
      'Tests → Build from bank → Data structures & algorithms.',
      'Pick freshers, 1–3 years or 3+ years, or tick topics yourself.',
      'Or build a role test for a fresher or junior developer: DSA questions are included.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-04-add-to-another-opening',
    date: '2026-10-04',
    title: 'Put a candidate forward for another client',
    summary: 'A good candidate for one client may suit another. From the candidate’s panel, add them to any other open opening in one step: their profile, résumés and documents come along, and each opening keeps its own stage and notes. The panel also lists every opening they’re in.',
    steps: [
      'Open the candidate in an opening (or from Candidates).',
      'Under Openings, click “Add to another opening”, pick the opening (grouped by client) and the starting stage.',
      'Add a note on why they fit, then “Add to opening”.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-04-admin-updates',
    date: '2026-10-04',
    title: 'Admin updates: feedback and shortlists at a glance',
    summary: 'Admins now get a short candidate summary when someone submits interview feedback, and when a candidate is Shortlisted, Selected, accepts an offer or joins. It lists the candidate’s details, test scores and the panel’s recommendations. Each update is kept under Admin updates and emailed to admins from the sender’s Gmail when it’s connected.',
    steps: [
      'Open Admin updates in the menu.',
      'Filter by Interview feedback or Stage changes; open the candidate or the feedback from any update.',
    ],
    link: { to: '/admin/updates', label: 'Open Admin updates' },
    capability: 'VIEW_ADMIN_UPDATES',
  },
  {
    id: '2026-10-04-interview-feedback',
    date: '2026-10-04',
    title: 'Feedback form after each interview',
    summary: 'After a Google Meet interview, everyone on the panel fills in a short form: how it went, a 1–4 rating for six areas, strengths, concerns, the questions they asked and a hire / no hire recommendation. You see the rest of the panel’s feedback only after giving yours, so opinions stay independent. Candidates and clients never see it.',
    steps: [
      'Open Interviews: “Waiting for your feedback” lists the interviews you still owe.',
      'Or open the candidate in an opening; under Interviews, click “Feedback form”.',
      'Rate the areas you covered, choose a recommendation and Submit. You can update it later.',
    ],
    link: { to: '/interviews', label: 'Open Interviews' },
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-10-04-interview-questions',
    date: '2026-10-04',
    title: 'Interview questions for every role and level',
    summary: '150 interview questions in 17 categories — from data structures for freshers (in Java, Python, JavaScript and C++) to system design and AI — each with what a strong answer covers and the red flags, plus how to score the round. Only CodeWalnut staff who interview can see them.',
    steps: [
      'Open Interview questions in the menu (or from the Interviews page).',
      'Choose the role and level; tick Pick on the questions you’ll ask, then “Only my picks”.',
      'Untick “Show answer guides” before sharing your screen, or Print the list.',
    ],
    link: { to: '/interview-questions', label: 'Open Interview questions' },
    capability: 'VIEW_INTERVIEWS',
  },
  {
    id: '2026-10-04-coding-tests',
    date: '2026-10-04',
    title: 'Coding tests: candidates write and run real code',
    summary: 'A new question type: the candidate writes a program in Java, Python, JavaScript or C++, runs it on sample tests, and after they submit it is graded against hidden tests, with points for each test passed. 60 ready-made problems, from basics to graphs and dynamic programming, and developer role tests now include one. You see their code, which tests passed, and whether they left the tab or pasted code.',
    steps: [
      'Tests → Build from bank → By pattern → Coding, pick freshers, 1–3 years or 3+ years.',
      'Or add a “Write code” question to your own test, then “Try a solution” to check it.',
      'After the candidate submits, open Answers in their Tests tab.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-more-role-banks',
    date: '2026-10-03',
    title: 'Tests for Node.js, QA, DevOps and data analysts',
    summary: 'Four new question banks — Node.js, Testing & QA automation, DevOps & cloud and Data analytics — and matching roles: Node.js backend, full-stack Node + React (MERN), QA automation engineer, DevOps / cloud engineer and data analyst, from fresher to lead.',
    steps: ['Tests → Build from bank → By role, then pick one of the new roles and a level.', 'Or By topics, and choose one of the new areas.'],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-role-tests',
    date: '2026-10-03',
    title: 'Tests by role and seniority',
    summary: 'Pick a role — Java backend, React frontend, full-stack and more — and the candidate’s level from fresher to lead. The test mixes the right areas: the main stack, what it works with, CS fundamentals, system design for seniors and aptitude for freshers. New banks: CS fundamentals and System design.',
    steps: ['Tests → Build from bank → By role.', 'Pick the role and level, check the mix, then Create test.'],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-new-test-results',
    date: '2026-10-03',
    title: 'Know when a candidate finishes a test',
    summary: 'A number next to Tests in the menu shows results nobody has looked at yet. The Tests page lists them with score and pass or fail, and the candidate’s team chat gets a note with the result.',
    steps: [
      'Tests → New test results → Open to see the candidate’s answers, or Mark seen.',
      'A result stops being new once someone opens its answers.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-drawer-send-test',
    date: '2026-10-03',
    title: 'Send a test right from the candidate',
    summary: 'Click a candidate in an opening and use Send test at the top of their panel. Their tests, scores and answers are in the new Tests tab.',
    steps: ['Openings → open an opening → click a candidate → Send test.', 'Pick the test and the days to finish → Send test.'],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-send-to-candidates',
    date: '2026-10-03',
    title: 'Send a test to many candidates at once',
    summary: 'Open a ready test and send it to several candidates of an opening in one go. Each gets their own link and signs in with Google to take it. You can also copy a candidate’s link to share it yourself.',
    steps: [
      'Tests → open a test marked ready → Send to candidates.',
      'Pick the opening, tick the candidates (or “Select all who can get it”), set the days to finish → Send.',
      'To share a link yourself: open the candidate → Tests → Copy link.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-tech-banks',
    date: '2026-10-03',
    title: 'Java, Python, JavaScript, React, Angular and SQL tests',
    summary: 'The question bank now has technical tests for each stack, split by experience: Fundamentals for freshers, Applied for 1–3 years and Advanced for 3+ years. Many questions show code to read.',
    steps: [
      'Tests → Question bank → pick an area (e.g. Java) to browse its topics and the topic guide.',
      'Tests → Build from bank → pick the area, then choose topics, or use a preset like “Java — 1 to 3 years”.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-aptitude-topics',
    date: '2026-10-03',
    title: '1,300 aptitude questions and build-by-topic',
    summary: 'The aptitude bank now has 26 topics with 50 questions each (17 easy, 17 medium, 16 hard), and a topic guide that explains each one. Building a test no longer means picking questions: tick the topics, choose how hard and in what order, and create.',
    steps: [
      'Tests → Question bank → open “Topic guide” to see what each topic covers.',
      'Tests → Build from bank → By topics: tick topics, set questions per topic, difficulty mix and order → Create test.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-delete-test',
    date: '2026-10-03',
    title: 'Delete tests nobody has taken',
    summary: 'Made a test by mistake? You can now delete it, as long as no candidate has started it. Once someone has taken it, archive it instead so their result stays.',
    steps: ['Tests → click the test → Delete.', 'If it was sent but not started, you’ll be warned that those links will stop working.'],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-question-bank',
    date: '2026-10-03',
    title: 'Aptitude question bank and test builder',
    summary:
      'A bank of 173 aptitude questions for freshers, with charts, clocks, Venn diagrams and picture puzzles — like TCS, Infosys, Wipro and Cognizant tests. Build a paper in one click with the difficulty mix you want, and see scores per section.',
    steps: [
      'Menu → Tests → Question bank to browse: filter by section, topic and difficulty; tick “Show answers” to check them.',
      'Click Build from bank, pick a pattern (Quick screening, TCS NQT style …) or set easy/medium/hard per section, choose the order, then Build.',
      'Check the draft, Mark ready, and send it from a candidate’s Profile → Tests as before.',
      'Results show the score per section (Numerical, Logical, Verbal).',
      'Add your own questions (with a picture) or ✨ draft more with AI — AI drafts wait for your approval.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-view-as',
    date: '2026-10-03',
    title: 'View as a candidate, client or role',
    summary:
      'Admins can now see exactly what a candidate, a client’s hiring manager or a recruiter sees — before inviting them. It is read-only: nothing is saved or sent while you look.',
    steps: [
      'Menu → View as.',
      'Pick a CodeWalnut role, a client contact, or search for a candidate, and click View as.',
      'Look around. The yellow banner shows who you are viewing as.',
      'Click Back to admin when you’re done (it also ends by itself after 30 minutes).',
    ],
    link: { to: '/admin/view-as', label: 'Open View as' },
    capability: 'MANAGE_USERS',
  },
  {
    id: '2026-10-03-codewalnut-resume',
    date: '2026-10-03',
    title: 'Make the CodeWalnut résumé in a minute',
    summary:
      'The AI turns the candidate’s original résumé into the client-ready CodeWalnut version: mobile number removed, a summary tailored to the opening, CodeWalnut logo and footer, and passed tests. You edit it and save it as PDF (or download Word).',
    steps: [
      'Open a candidate who has an original résumé → Profile → CodeWalnut résumé → ✨ Create with AI.',
      'Click Edit and check every line against the original. Change the summary, skills or bullets as you like.',
      'Choose whether to show their email and the “CodeWalnut screening” test results.',
      'Preview PDF, then Save PDF to documents. Share it with the client from “Share with client”.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-profile-from-resume',
    date: '2026-10-03',
    title: 'Profiles filled from the résumé',
    summary:
      'When the AI reads a résumé it now fills the empty profile fields: college, degree, graduation year, LinkedIn and address (and email or phone if missing). It never overwrites what you typed.',
    steps: [
      'New uploads fill the profile automatically.',
      'For résumés read before today, open the opening and click Analyze résumés in AI suggestions.',
      'Check the Profile tab; edit anything that looks wrong.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-tests',
    date: '2026-10-03',
    title: 'Send aptitude, Java and Python tests',
    summary:
      'Build tests in the ATS (the AI can draft the questions for you to check), send them to applicants by email or WhatsApp, and see the score as soon as they finish. Candidates take them on their CodeWalnut page against a timer.',
    steps: [
      'Menu → Tests → New test. Pick the kind (Aptitude, Java, Python…), time limit and pass mark.',
      'Click Draft (✨ AI) or add your own questions. Check every AI draft and its answer, then Mark ready.',
      'Open a candidate in an opening → Profile → Tests → Send test. Pick the test, the due date, Email and/or WhatsApp.',
      'Scores appear in the drawer and in the opening’s Test column. Use “Passed a test” to filter.',
      'Not started after 2 days? The Test column highlights it; click Remind in the drawer.',
    ],
    link: { to: '/tests', label: 'Open Tests' },
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-03-resume-intelligence',
    date: '2026-10-03',
    title: 'Upload résumés and let the AI sort them',
    summary:
      'Got a pile of résumés by email? Upload them all to an opening. The AI reads each one, adds the candidate at Applied / Sourced with their résumé, scores how well it matches the job description, and suggests whom to contact first and who is closest to selection.',
    steps: [
      'Save the résumés from Gmail (open the email → Download all).',
      'Open the opening → Upload résumés → choose all the files. Make sure the opening has a job description.',
      'Watch the Match column fill in. Sort by best match, or filter: strong match, has projects, has experience, skill, graduation year.',
      'See “AI suggestions” for Contact next and Closest to selection. Click a name to open their AI résumé insights.',
      'Ask questions in the search box, e.g. “who has worked on Spring Boot projects?” or “who hasn’t been interviewed yet?”, then ✨ Ask AI.',
      'Scores are advice based on the résumé only. You decide.',
    ],
    capability: 'MANAGE_JOBS',
  },
  {
    id: '2026-10-02-ai-assistant',
    date: '2026-10-02',
    title: 'Tell the AI what happened',
    summary:
      'In an opening, type something like “sagar, sucheth and amogh are shortlisted” and the AI assistant prepares the changes for you. You check them and click Apply.',
    steps: [
      'Open an opening and type your instruction in the search box.',
      'Click ✨ Ask AI (or press Ctrl+Enter).',
      'Check the suggested changes. If a name matches two people, pick the right one. Rejections need a reason.',
      'Click Apply. Nothing changes before that.',
    ],
    capability: 'MANAGE_JOBS',
  },
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
