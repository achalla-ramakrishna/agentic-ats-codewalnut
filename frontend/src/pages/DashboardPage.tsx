import { Link } from 'react-router-dom'
import { useMe } from '../auth/AuthContext'
import { Card, PageHeader } from '../components/ui'

export function DashboardPage() {
  const me = useMe()
  const sections = me.navigation.filter((item) => item.path !== '/')

  return (
    <>
      <PageHeader title={`Welcome${me.name ? `, ${me.name.split(' ')[0]}` : ''}`} description="Your hiring work at a glance." />
      <Card className="stack">
        <h2>Your sections</h2>
        {sections.length === 0 ? (
          <p className="muted">Nothing else is assigned to your role yet.</p>
        ) : (
          <ul>
            {sections.map((item) => (
              <li key={item.key}>
                <Link to={item.path}>{item.label}</Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </>
  )
}
