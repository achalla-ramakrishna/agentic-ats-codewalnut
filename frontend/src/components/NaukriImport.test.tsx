import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { NaukriImport } from './NaukriImport'

const profile = (line: number, name: string, email: string | null) => ({
  line, name, email, phone: null, currentLocation: 'Bengaluru', preferredLocations: null, experienceYears: 6, experience: '6 Year(s) 0 Month(s)',
  company: 'Example Tech', designation: 'Senior Software Engineer', salary: null, noticePeriod: '15 Days or less', headline: 'Java developer',
  keySkills: null, ugDegree: null, ugCollege: null, pgDegree: null, pgCollege: null, appliedOn: '09-Oct-2026',
})

const ranked = {
  fileName: 'export.xlsx', rankedBy: 'RULES', note: 'Ranked by rules.', hasDescription: true, total: 3, missingColumns: [],
  rows: [
    { profile: profile(2, 'Asha Test', 'asha@example.com'), matchPercent: 91, summary: 'Java and Spring Boot, 6 years', strengths: ['Spring Boot'], gaps: [], status: 'NEW' },
    { profile: profile(3, 'Ravi Test', 'ravi@example.com'), matchPercent: 64, summary: 'Some Java', strengths: [], gaps: ['Kafka'], status: 'NEW' },
    { profile: profile(4, 'Meena Test', 'meena@example.com'), matchPercent: 50, summary: 'Already here', strengths: [], gaps: [], status: 'ALREADY_IN_OPENING' },
  ],
}

describe('Import from Naukri', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('ranks the file, adds the chosen people, then emails them asking for their résumé (ADR-0026)', async () => {
    const fetchMock = fakeFetch([
      { path: '/jobs/j1/naukri/rank', method: 'POST', body: ranked },
      { path: '/jobs/j1/naukri/import', method: 'POST', body: { added: 1, rows: [{ applicationId: 'a1', name: 'Asha Test', email: 'asha@example.com', hasEmail: true }], skipped: [] } },
      { path: '/google/status', body: { available: true, calendarConnected: true, mailConnected: true, redirectUri: 'x' } },
      { path: '/applications/a1/messages', method: 'POST', status: 201, body: {} },
    ])
    const onAdded = vi.fn()
    render(<NaukriImport jobId="j1" jobTitle="Senior Java Developer" senderName="Priya Recruiter" onAdded={onAdded} onClose={() => undefined} />)

    await userEvent.upload(screen.getByLabelText('Naukri Excel file'), new File(['x'], 'export.xlsx'))
    await userEvent.click(screen.getByRole('button', { name: 'Rank against job description' }))

    expect(await screen.findByText('Java and Spring Boot, 6 years')).toBeInTheDocument()
    expect(screen.getByText('91%')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'Add Meena Test' })).toBeDisabled()
    expect(screen.getByText('Already in this opening')).toBeInTheDocument()
    // The best ones that can be added start ticked; untick Ravi.
    expect(screen.getByRole('checkbox', { name: 'Add Ravi Test' })).toBeChecked()
    await userEvent.click(screen.getByRole('checkbox', { name: 'Add Ravi Test' }))
    await userEvent.click(screen.getByRole('button', { name: 'Add 1 to this opening' }))

    expect(await screen.findByText(/Added 1 candidate to this opening/)).toBeInTheDocument()
    expect(onAdded).toHaveBeenCalled()
    const imported = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/naukri/import'))!
    const sentRows = JSON.parse(imported[1]!.body as string).rows
    expect(sentRows).toHaveLength(1)
    expect(sentRows[0]).toMatchObject({ matchPercent: 91, profile: { name: 'Asha Test' } })

    await userEvent.click(await screen.findByRole('button', { name: 'Send email to 1' }))
    expect(await screen.findByText(/Emailed 1 candidate/)).toBeInTheDocument()
    const message = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/applications/a1/messages'))!
    const body = JSON.parse(message[1]!.body as string)
    expect(body).toMatchObject({ channel: 'CANDIDATE', sendEmail: true })
    expect(body.body).toContain('Hi Asha,')
    expect(body.body).toContain('latest résumé')
    expect(body.body).toContain('Priya Recruiter')
    expect(body.subject).toContain('Senior Java Developer')
  })
})
