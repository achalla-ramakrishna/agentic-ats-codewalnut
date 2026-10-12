import type { Stage } from '../api/tracker'

/**
 * Starting points for candidate emails. The recruiter always sees and edits the text before
 * sending (docs/features/communication.md, MSG-10). Keep them short, warm and specific.
 */
export interface EmailTemplate {
  key: string
  label: string
  subject: string
  body: string
}

export const EMAIL_TEMPLATES: EmailTemplate[] = [
  {
    key: 'custom',
    label: 'Blank message',
    subject: 'Your application for {jobTitle} at CodeWalnut',
    body: 'Hi {firstName},\n\n\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'resume_request',
    label: 'Ask for résumé (job portal applicants)',
    subject: 'Your application for {jobTitle} at CodeWalnut – please share your résumé',
    body:
      'Hi {firstName},\n\nThank you for applying for the {jobTitle} role at CodeWalnut. Your profile looks like a good match and we would like to take it forward.\n\n' +
      'Could you reply to this email with:\n- your latest résumé (PDF)\n- your current and expected CTC\n- your notice period\n- two or three times this week that suit you for a short call\n\n' +
      'Once we have your résumé, we will set up the first conversation.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'book_slot',
    label: 'Book a 30-minute call (booking link)',
    subject: 'Pick a time for a 30-minute call – {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nThank you for sending your résumé for the {jobTitle} role. We would like to have a 30-minute conversation on Google Meet to get to know you and talk about the role.\n\n' +
      'Please pick a time that suits you here (it takes a few seconds, no sign-in needed):\n{bookingLink}\n\n' +
      'Once you have picked a time, you will receive a calendar invitation with the Google Meet link.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'booking_reminder',
    label: 'Reminder: pick a time (booking link)',
    subject: 'Reminder: pick a time for your call – {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nA quick reminder to pick a time for our 30-minute call about the {jobTitle} role. It takes a few seconds:\n{bookingLink}\n\n' +
      'If none of the times suit you, just reply to this email and we will find one together.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'interview_slots',
    label: 'Thanks for résumé – interview slots',
    subject: 'Interview for {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nThank you for sending your résumé for the {jobTitle} role. We would like to invite you to a short interview on Google Meet (about 30–45 minutes).\n\n' +
      'Could you reply with two or three times over the next few days that suit you? Once you confirm, I will send a calendar invite with the Meet link.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'follow_up',
    label: 'Follow-up (no reply yet)',
    subject: 'Following up – {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nI am following up on my earlier email about the {jobTitle} role at CodeWalnut. If you are still interested, please reply with your latest résumé and your notice period, and we will take it from there.\n\n' +
      'If you are no longer looking, a quick reply letting me know would be appreciated.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'received',
    label: 'Application received',
    subject: 'We received your application – {jobTitle}',
    body:
      'Hi {firstName},\n\nThank you for applying for the {jobTitle} role at CodeWalnut. We have received your application and our team is reviewing it.\n\n' +
      'We will get back to you with next steps soon.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'screening',
    label: 'Next steps – screening call',
    subject: 'Next steps for {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nThanks for your interest in the {jobTitle} role. We would like to have a short call to learn more about you.\n\n' +
      'Could you share two or three times that work for you this week?\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'shortlisted',
    label: 'Shortlisted',
    subject: "You're shortlisted – {jobTitle}",
    body:
      'Hi {firstName},\n\nGood news: you have been shortlisted for the {jobTitle} role at CodeWalnut.\n\n' +
      'We will share the details of the next round shortly.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'documents',
    label: 'Request documents',
    subject: 'Documents needed – {jobTitle}',
    body:
      'Hi {firstName},\n\nTo complete your onboarding and background verification for {jobTitle}, please upload these on your CodeWalnut candidate page:\n\n' +
      '- Masked Aadhaar card (download it from myaadhaar.uidai.gov.in; only the last 4 digits are visible)\n' +
      '- PAN card\n- Degree certificate or final-semester marksheet\n- A recent passport-size photo\n\n' +
      'Please also check that your mobile number, date of birth and addresses are filled in under "My profile".\n\n' +
      'Sign in with Google using this email address. Please do not send these documents by email.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'selected',
    label: 'Selected – next steps',
    subject: 'Congratulations – {jobTitle} at CodeWalnut',
    body:
      "Hi {firstName},\n\nCongratulations! We're happy to let you know that you have been selected for the {jobTitle} role at CodeWalnut.\n\n" +
      'Our team will reach out shortly with the offer details and next steps.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
  {
    key: 'rejected',
    label: 'Not progressing',
    subject: 'Your application for {jobTitle} at CodeWalnut',
    body:
      'Hi {firstName},\n\nThank you for your time and interest in the {jobTitle} role at CodeWalnut.\n\n' +
      'After careful consideration, we have decided not to move forward with your application at this time. ' +
      'This was not an easy decision, and we encourage you to apply for future openings that match your skills.\n\n' +
      'We wish you all the best.\n\nBest regards,\n{senderName}\nCodeWalnut',
  },
]

/** Short names for the Workflow view's "Emails sent" column (PIPE-23); tests send their own. */
export const TEMPLATE_SHORT: Record<string, string> = {
  resume_request: 'Résumé request',
  book_slot: 'Booking link',
  booking_reminder: 'Booking reminder',
  interview_slots: 'Interview slots',
  follow_up: 'Follow-up',
  received: 'Application received',
  screening: 'Screening call',
  shortlisted: 'Shortlisted',
  documents: 'Documents',
  selected: 'Selected',
  rejected: 'Rejected',
  test_invite: 'Test sent',
  test_reminder: 'Test reminder',
}

/** The template key to record with a message, or undefined for a free-form one. */
export const templateKeyFor = (key: string | undefined | null) => (key && key !== 'custom' && TEMPLATE_SHORT[key] ? key : undefined)

/** Sending these also moves the candidate to the stage they announce, when the recruiter ticks it. */
export const STAGE_FOR_TEMPLATE: Partial<Record<string, Stage>> = {
  shortlisted: 'SHORTLISTED',
  selected: 'OFFER_SENT',
}

/** Which template to suggest after moving a candidate to a stage. */
export const TEMPLATE_FOR_STAGE: Partial<Record<Stage, string>> = {
  SHORTLISTED: 'shortlisted',
  OFFER_SENT: 'selected',
  REJECTED: 'rejected',
}

export function fillTemplate(text: string, values: { candidateName: string; jobTitle: string; senderName: string }) {
  const firstName = values.candidateName.trim().split(/\s+/)[0] ?? ''
  return text
    .replaceAll('{firstName}', firstName)
    .replaceAll('{jobTitle}', values.jobTitle)
    .replaceAll('{senderName}', values.senderName)
}
