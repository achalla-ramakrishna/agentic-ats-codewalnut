import { getBookingLinks } from '../api/booking'
import { postMessage } from '../api/messages'
import { moveStage, type Stage } from '../api/tracker'
import { fillTemplate, templateKeyFor } from './emailTemplates'

export interface SendTarget {
  applicationId: string
  name: string
  jobTitle: string
}

/**
 * Emails each person their own copy, one at a time, from the sender's Gmail: fills {firstName},
 * {jobTitle} and {senderName}, and {bookingLink} with their personal booking link (PIPE-24). Used by
 * the bulk email panel and "Today's emails" (PIPE-25).
 */
export async function sendEach(
  targets: SendTarget[],
  email: { subject: string; body: string; templateKey?: string; senderName: string; moveTo?: Stage },
  onProgress: (text: string) => void,
): Promise<{ sent: string[]; failed: string[] }> {
  const sent: string[] = []
  const failed: string[] = []
  const usesLink = (email.subject + email.body).includes('{bookingLink}')
  let links: Record<string, string> = {}
  let problems: Record<string, string> = {}
  if (usesLink && targets.length) {
    onProgress('Preparing booking links…')
    const r = await getBookingLinks(targets.map((t) => t.applicationId))
    links = r.links
    problems = r.problems
  }
  for (const [n, t] of targets.entries()) {
    if (usesLink && !links[t.applicationId]) {
      failed.push(`${t.name}: ${problems[t.applicationId] ?? 'no booking link'}`)
      continue
    }
    const fill = (text: string) => {
      const filled = fillTemplate(text, { candidateName: t.name, jobTitle: t.jobTitle, senderName: email.senderName })
      return usesLink ? filled.replaceAll('{bookingLink}', links[t.applicationId]) : filled
    }
    onProgress(`Sending ${n + 1} of ${targets.length}…`)
    try {
      await postMessage(t.applicationId, {
        channel: 'CANDIDATE',
        subject: fill(email.subject),
        body: fill(email.body),
        sendEmail: true,
        templateKey: templateKeyFor(email.templateKey),
      })
      sent.push(t.name)
      if (email.moveTo) {
        // The email went out; a failed stage move is reported but doesn't count as not sent.
        await moveStage(t.applicationId, email.moveTo).catch((e: unknown) =>
          failed.push(`${t.name}: emailed, but couldn't move the stage (${e instanceof Error ? e.message : 'error'})`),
        )
      }
    } catch (e) {
      failed.push(`${t.name}: ${e instanceof Error ? e.message : 'not sent'}`)
    }
  }
  return { sent, failed }
}
