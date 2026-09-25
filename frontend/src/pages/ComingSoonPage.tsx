import { Card, PageHeader } from '../components/ui'

/** Placeholder for sections whose feature lands in a later chunk. */
export function ComingSoonPage({ title, chunk, spec }: { title: string; chunk: string; spec: string }) {
  return (
    <>
      <PageHeader title={title} />
      <Card>
        <p>
          This section arrives in <strong>{chunk}</strong>. Requirements: <code>docs/features/{spec}</code>.
        </p>
      </Card>
    </>
  )
}
