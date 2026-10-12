import { api } from './client'

/** Interview self-booking (PIPE-24). */
export interface SlotView {
  id: string
  startAt: string
  endAt: string
  timeZone: string
  applicationId: string | null
  candidateName: string | null
  bookedAt: string | null
  interviewId: string | null
}

export interface SlotsResponse {
  slots: SlotView[]
  free: number
  booked: number
  scheduled: number
}

export interface CreateSlotsInput {
  from: string
  to: string
  weekdaysOnly: boolean
  windows: { start: string; end: string }[]
  durationMinutes: number
  timeZone: string
  interviewerEmails: string[]
}

export interface PublicSlot {
  id: string
  startAt: string
  endAt: string
}

export interface BookingPageData {
  firstName: string
  jobTitle: string
  timeZone: string
  durationMinutes: number
  booked: PublicSlot | null
  scheduled: boolean
  slots: PublicSlot[]
}

const json = (body: unknown) => JSON.stringify(body)

export const getSlots = (jobId: string) => api<SlotsResponse>(`/jobs/${jobId}/slots`)
export const createSlots = (jobId: string, input: CreateSlotsInput) =>
  api<{ created: number; skipped: number; slots: SlotsResponse }>(`/jobs/${jobId}/slots`, { method: 'POST', body: json(input) })
export const deleteSlot = (slotId: string) => api<SlotsResponse>(`/slots/${slotId}`, { method: 'DELETE' })
export const sendSlotInvites = (jobId: string) =>
  api<{ scheduled: number; failed: string[] }>(`/jobs/${jobId}/slots/send-invites`, { method: 'POST' })
export const getBookingLinks = (applicationIds: string[]) =>
  api<{ links: Record<string, string>; problems: Record<string, string> }>('/booking-links', {
    method: 'POST',
    body: json({ applicationIds }),
  })

export const getBookingPage = (token: string) => api<BookingPageData>(`/public/booking/${token}`)
export const bookSlot = (token: string, slotId: string) =>
  api<BookingPageData>(`/public/booking/${token}`, { method: 'POST', body: json({ slotId }) })
