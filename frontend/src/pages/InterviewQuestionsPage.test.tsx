import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fakeFetch } from '../test/fakeFetch'
import { InterviewQuestionsPage } from './InterviewQuestionsPage'

const guide = {
  howTo: ['Open the test result first.'],
  scale: [{ score: 4, label: 'Strong', evidence: 'Correct with trade-offs.' }],
  roles: [{ id: 'java-backend', name: 'Java backend developer', categories: ['dsa-freshers', 'java'] }],
  categories: [
    {
      id: 'dsa-freshers',
      name: 'Data structures for freshers and interns',
      intro: 'The main signal for interns.',
      questions: [
        { level: 'F', topic: 'Hashing', question: 'How does a hash map find a key?', strong: 'Hash, bucket, compare keys.', redFlags: 'Unique slots.', language: null },
        { level: 'F', topic: 'Know your language', question: 'list vs deque for a queue?', strong: 'deque is O(1).', redFlags: 'pop(0).', language: 'Python' },
      ],
    },
    { id: 'java', name: 'Java and Spring', intro: null, questions: [
      { level: 'S', topic: 'JVM', question: 'Memory grows until OutOfMemoryError. How do you investigate?', strong: 'Heap dumps.', redFlags: 'Raise -Xmx.', language: null },
    ] },
    { id: 'sql', name: 'SQL and databases', intro: null, questions: [
      { level: 'F', topic: 'Joins', question: 'Find customers who never ordered.', strong: 'LEFT JOIN.', redFlags: 'NOT IN with NULLs.', language: null },
    ] },
  ],
}

describe('InterviewQuestionsPage (INT-21, INT-22)', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('filters by role, level and language, hides answer guides and picks questions', async () => {
    fakeFetch([{ path: '/interview-guide', body: guide }])
    render(
      <MemoryRouter initialEntries={['/interview-questions?role=java-backend']}>
        <InterviewQuestionsPage />
      </MemoryRouter>,
    )
    expect(await screen.findByText('How does a hash map find a key?')).toBeInTheDocument()
    // The role shows only its categories.
    expect(screen.queryByText('Find customers who never ordered.')).not.toBeInTheDocument()
    expect(screen.getByText('Hash, bucket, compare keys.')).toBeInTheDocument()

    await userEvent.click(within(screen.getByRole('group', { name: 'Level' })).getByRole('button', { name: 'Fresher' }))
    expect(screen.queryByText(/OutOfMemoryError/)).not.toBeInTheDocument()

    await userEvent.selectOptions(screen.getByLabelText('Language'), 'Python')
    expect(screen.getByText('list vs deque for a queue?')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText(/Show answer guides/))
    expect(screen.queryByText('Hash, bucket, compare keys.')).not.toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('Pick: list vs deque for a queue?'))
    await userEvent.click(screen.getByLabelText(/Only my picks \(1\)/))
    expect(screen.queryByText('How does a hash map find a key?')).not.toBeInTheDocument()
    expect(screen.getByText('list vs deque for a queue?')).toBeInTheDocument()
  })
})
