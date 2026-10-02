import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AssistantPlan } from '../api/assistant'
import { fakeFetch } from '../test/fakeFetch'
import { AssistantPlanCard } from './AssistantPlanCard'

const plan: AssistantPlan = {
  instruction: 'sagar, sucheth, amogh are shortlisted and ravi is rejected',
  summary: 'Move 3 candidates to Shortlisted and reject 1.',
  aiGenerated: true,
  actions: [
    { type: 'MOVE_STAGE', applicationId: 'a1', candidateName: 'Sagar Kumar', fromStage: 'INTERVIEWED', fromLabel: 'Interviewed', toStage: 'SHORTLISTED', toLabel: 'Shortlisted', note: null, needsReason: false },
    { type: 'MOVE_STAGE', applicationId: 'a2', candidateName: 'Sucheth R', fromStage: 'INTERVIEWED', fromLabel: 'Interviewed', toStage: 'SHORTLISTED', toLabel: 'Shortlisted', note: null, needsReason: false },
    { type: 'MOVE_STAGE', applicationId: 'a5', candidateName: 'Ravi Teja', fromStage: 'INTERVIEWED', fromLabel: 'Interviewed', toStage: 'REJECTED', toLabel: 'Rejected', note: null, needsReason: true },
  ],
  unresolved: [
    {
      mention: 'amogh',
      options: [
        { applicationId: 'a3', name: 'Amogh S', stageLabel: 'Interviewed' },
        { applicationId: 'a4', name: 'Amogh K', stageLabel: 'Screening' },
      ],
      toStage: 'SHORTLISTED',
      toLabel: 'Shortlisted',
      note: null,
    },
    { mention: 'zoya', options: [], toStage: 'SHORTLISTED', toLabel: 'Shortlisted', note: null },
  ],
  notes: ['Divya N is already at Shortlisted.'],
}

describe('AssistantPlanCard', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('is labelled as AI, asks for missing reasons and applies only what is ticked', async () => {
    const fetch = fakeFetch([{ method: 'PATCH', path: '/applications/', body: {} }])
    const onApplied = vi.fn()
    render(<AssistantPlanCard plan={plan} onApplied={onApplied} onClose={() => undefined} />)

    expect(screen.getByText('Suggested by AI')).toBeInTheDocument()
    expect(screen.getByText(/Nothing has changed yet/)).toBeInTheDocument()
    expect(screen.getByText(/no one by that name in this opening/)).toBeInTheDocument()
    expect(screen.getByText('Divya N is already at Shortlisted.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Amogh K (Screening)' }))
    expect(screen.getByRole('button', { name: 'Apply 4 changes' })).toBeDisabled()
    await userEvent.type(screen.getByLabelText('Reason for Ravi Teja'), 'Weak in React')
    await userEvent.click(screen.getByLabelText(/Sucheth R/))
    await userEvent.click(screen.getByRole('button', { name: 'Apply 3 changes' }))

    expect(await screen.findByText('Applied 3 changes.')).toBeInTheDocument()
    const calls = fetch.mock.calls.map(([url, init]) => [String(url), JSON.parse(String(init?.body))])
    expect(calls).toEqual([
      ['/api/v1/applications/a1/stage', { stage: 'SHORTLISTED' }],
      ['/api/v1/applications/a5/stage', { stage: 'REJECTED', note: 'Weak in React' }],
      ['/api/v1/applications/a4/stage', { stage: 'SHORTLISTED' }],
    ])
    expect(onApplied).toHaveBeenCalled()
  })
})
