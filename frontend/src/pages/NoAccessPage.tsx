import { Link } from 'react-router-dom'
import { Card, PageHeader } from '../components/ui'

export function NoAccessPage() {
  return (
    <>
      <PageHeader title="Not available" />
      <Card>
        <p>This page doesn't exist or isn't part of your role.</p>
        <Link to="/">Back to dashboard</Link>
      </Card>
    </>
  )
}
