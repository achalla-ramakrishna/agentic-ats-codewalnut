import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { BuildFromBank } from './BuildFromBank'
import { QuestionBankPanel } from './QuestionBankPanel'

const counts = ['QUANT', 'LOGICAL', 'VERBAL'].flatMap((section) =>
  ['EASY', 'MEDIUM', 'HARD'].map((difficulty) => ({ area: 'APTITUDE', section, sectionLabel: section, difficulty, count: 20 })),
)
const overview = {
  counts,
  topics: [{ section: 'LOGICAL', topic: 'Clocks', count: 6, withPictures: 6 }],
  presets: [
    { id: 'quick', name: 'Quick screening', description: '20 questions in 25 minutes', durationMinutes: 25, passPercent: 50,
      sections: [{ section: 'QUANT', easy: 3, medium: 3, hard: 2 }, { section: 'LOGICAL', easy: 3, medium: 3, hard: 2 }, { section: 'VERBAL', easy: 2, medium: 2, hard: 0 }] },
    { id: 'tcs-nqt', name: 'TCS NQT style (Foundation)', description: '65 questions', durationMinutes: 75, passPercent: 60,
      sections: [{ section: 'QUANT', easy: 8, medium: 8, hard: 4 }, { section: 'VERBAL', easy: 10, medium: 10, hard: 5 }, { section: 'LOGICAL', easy: 8, medium: 8, hard: 4 }] },
  ],
  guide: [
    { id: 'percent', section: 'QUANT', sectionLabel: 'Numerical ability', name: 'Percentages', covers: 'Percent of a number', example: '25% of 80?', easy: 17, medium: 17, hard: 16 },
    { id: 'clock', section: 'LOGICAL', sectionLabel: 'Logical reasoning', name: 'Clocks', covers: 'Angle between the hands', example: 'Angle at 3:30?', easy: 17, medium: 17, hard: 16 },
    { id: 'synonym', section: 'VERBAL', sectionLabel: 'Verbal ability', name: 'Synonyms', covers: 'Closest meaning', example: 'CANDID?', easy: 17, medium: 17, hard: 16 },
  ],
}
const svg = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10"></svg>'
const clockQuestion = {
  id: 'b1', area: 'APTITUDE', section: 'LOGICAL', sectionLabel: 'Logical reasoning', topic: 'Clocks', difficulty: 'MEDIUM',
  kind: 'SINGLE_CHOICE', prompt: 'What is the angle?', code: null, figure: svg, options: ['90°', '75°', '60°', '120°'],
  optionFigures: null, correct: [1], acceptedAnswers: [], points: 2, explanation: 'Use 30H − 5.5M.', source: 'AI', status: 'REVIEW', timesUsed: 0,
}

describe('Question bank', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('builds a paper from a preset', async () => {
    const fetchMock = fakeFetch([
      { path: '/question-bank/overview', body: overview },
      { path: '/question-bank/build', method: 'POST', status: 201, body: { summary: { id: 't9' }, questions: [] } },
    ])
    const onBuilt = vi.fn()
    render(<BuildFromBank onBuilt={onBuilt} onCancel={() => undefined} />)
    await userEvent.click(screen.getByRole('tab', { name: 'By pattern or section' }))

    expect(await screen.findByRole('button', { name: 'Build test (20 questions)' })).toBeInTheDocument()
    await userEvent.selectOptions(screen.getByLabelText('Pattern'), 'tcs-nqt')
    expect(screen.getByLabelText('Time (min)')).toHaveValue(75)
    await userEvent.selectOptions(screen.getByLabelText('Order of questions'), 'EASY_FIRST')
    await userEvent.click(screen.getByRole('button', { name: 'Build test (65 questions)' }))

    await vi.waitFor(() => expect(onBuilt).toHaveBeenCalledWith('t9'))
    const build = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/question-bank/build'))!
    expect(JSON.parse(build[1]!.body as string)).toMatchObject({
      durationMinutes: 75,
      passPercent: 60,
      order: 'EASY_FIRST',
      sections: [{ section: 'QUANT', easy: 8, medium: 8, hard: 4 }, { section: 'VERBAL', easy: 10, medium: 10, hard: 5 }, { section: 'LOGICAL', easy: 8, medium: 8, hard: 4 }],
    })
  })

  it('builds a paper from chosen topics, a difficulty mix and an order', async () => {
    const fetchMock = fakeFetch([
      { path: '/question-bank/overview', body: overview },
      { path: '/question-bank/build', method: 'POST', status: 201, body: { summary: { id: 't7' }, questions: [] } },
    ])
    const onBuilt = vi.fn()
    render(<BuildFromBank onBuilt={onBuilt} onCancel={() => undefined} />)

    await userEvent.click(await screen.findByRole('checkbox', { name: /Percentages/ }))
    await userEvent.click(screen.getByRole('checkbox', { name: /Clocks/ }))
    await userEvent.tripleClick(screen.getByLabelText('Questions per topic'))
    await userEvent.keyboard('5')
    await userEvent.selectOptions(screen.getByLabelText('Order of questions'), 'HARD_FIRST')
    expect(screen.getByText('10 questions from 2 topics: 4 easy, 4 medium, 2 hard.')).toBeInTheDocument()
    expect(screen.getByLabelText('Time (min)')).toHaveValue(12)
    await userEvent.click(screen.getByRole('button', { name: 'Create test (10 questions)' }))

    await vi.waitFor(() => expect(onBuilt).toHaveBeenCalledWith('t7'))
    const build = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/question-bank/build'))!
    expect(JSON.parse(build[1]!.body as string)).toMatchObject({
      order: 'HARD_FIRST',
      durationMinutes: 12,
      topics: [
        { section: 'QUANT', topic: 'Percentages', easy: 2, medium: 2, hard: 1 },
        { section: 'LOGICAL', topic: 'Clocks', easy: 2, medium: 2, hard: 1 },
      ],
    })
  })

  it('shows the topic guide', async () => {
    fakeFetch([
      { path: '/question-bank/overview', body: overview },
      { path: '/question-bank', body: { items: [], total: 0 } },
    ])
    render(<QuestionBankPanel />)
    const guide = await screen.findByRole('table', { name: 'Topic guide' })
    expect(within(guide).getByText('Angle between the hands')).toBeInTheDocument()
    expect(within(guide).getAllByText('17 / 17 / 16')).toHaveLength(3)
  })

  it('shows pictures and lets a reviewer approve an AI draft', async () => {
    const fetchMock = fakeFetch([
      { path: '/question-bank/overview', body: overview },
      { path: '/question-bank/b1/approve', method: 'POST', body: { ...clockQuestion, status: 'ACTIVE' } },
      { path: '/question-bank', body: { items: [clockQuestion], total: 1 } },
    ])
    render(<QuestionBankPanel />)

    expect(await screen.findByText('What is the angle?')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Question picture' }).getAttribute('src')).toMatch(/^data:image\/svg\+xml/)
    expect(screen.getByText('AI draft')).toBeInTheDocument()
    expect(within(screen.getByRole('table', { name: 'Questions in the bank' })).getAllByText('20')).toHaveLength(9)
    await userEvent.click(screen.getByRole('button', { name: 'Approve' }))
    expect(await screen.findByText(/it can now be used in tests/)).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith('/question-bank/b1/approve'))).toBe(true)
  })
})
