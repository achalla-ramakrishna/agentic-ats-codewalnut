import { describe, expect, it } from 'vitest'
import { parseExtraPeople } from './SendAiInterview'

describe('extra people for the AI interview (AINT-16)', () => {
  it('reads addresses with or without names, and flags what isn’t an email', () => {
    const r = parseExtraPeople('asha.rao92@example.com\nRavi Kumar <Ravi@Example.com>\nMeena Iyer, meena@example.com\nnot-an-email\nasha.rao92@example.com')
    expect(r.people).toEqual([
      { name: 'Asha Rao', email: 'asha.rao92@example.com' },
      { name: 'Ravi Kumar', email: 'ravi@example.com' },
      { name: 'Meena Iyer', email: 'meena@example.com' },
    ])
    expect(r.invalid).toEqual(['not-an-email'])
  })
})
