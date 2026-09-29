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
      'Hi {firstName},\n\nTo move forward with your application for {jobTitle}, could you please share:\n\n' +
      '- Your latest résumé\n- Your degree certificate or final-semester marksheet\n- A government ID\n\nBest regards,\n{senderName}\nCodeWalnut',
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

/** Which template to suggest after moving a candidate to a stage. */
export const TEMPLATE_FOR_STAGE: Partial<Record<Stage, string>> = {
  SCREENING: 'screening',
  SHORTLISTED: 'shortlisted',
  SELECTED: 'selected',
  REJECTED: 'rejected',
}

export function fillTemplate(text: string, values: { candidateName: string; jobTitle: string; senderName: string }) {
  const firstName = values.candidateName.trim().split(/\s+/)[0] ?? ''
  return text
    .replaceAll('{firstName}', firstName)
    .replaceAll('{jobTitle}', values.jobTitle)
    .replaceAll('{senderName}', values.senderName)
}
