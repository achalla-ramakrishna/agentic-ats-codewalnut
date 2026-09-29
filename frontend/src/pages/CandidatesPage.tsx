import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { searchApplications, type ApplicationRow, type Stage } from '../api/tracker'
import { useMe } from '../auth/AuthContext'
import { CandidateDrawer, type DrawerTab } from '../components/CandidateDrawer'
import { Card, PageHeader } from '../components/ui'
import { useOpenFromQuery } from '../components/useOpenFromQuery'
import { useStages } from '../components/useStages'
import '../components/tracker.css'

export function CandidatesPage() {
  const me = useMe()
  const stages = useStages()
  const [q, setQ] = useState('')
  const [stage, setStage] = useState<Stage | ''>('')
  const [rows, setRows] = useState<ApplicationRow[] | null>(null)
  const [open, setOpen] = useState<ApplicationRow | null>(null)
  const [openTab, setOpenTab] = useState<DrawerTab>('profile')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const t = setTimeout(() => {
      searchApplications(q, stage || undefined).then(setRows).catch(() => setRows([]))
    }, 250)
    return () => clearTimeout(t)
  }, [q, stage, reload])
  const openDrawer = useCallback((row: ApplicationRow, tab: DrawerTab) => {
    setOpen(row)
    setOpenTab(tab)
  }, [])
  useOpenFromQuery(rows, openDrawer)

  return (
    <div className="stack">
      <PageHeader title="Candidates" description="Everyone across all openings." />
      <div className="row">
        <input className="input" aria-label="Search" placeholder="Search name, email or phone" value={q} onChange={(e) => setQ(e.target.value)} style={{ minWidth: 280 }} />
        <select className="select" aria-label="Stage filter" value={stage} onChange={(e) => setStage(e.target.value as Stage | '')}>
          <option value="">All stages</option>
          {stages.map((s) => (
            <option key={s.key} value={s.key}>
              {s.label}
            </option>
          ))}
        </select>
      </div>
      <Card>
        {!rows && <p className="muted">Loading…</p>}
        {rows && rows.length === 0 && <p className="muted" style={{ margin: 0 }}>No candidates found.</p>}
        {rows && rows.length > 0 && (
          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Opening</th>
                  <th>Stage</th>
                  <th>Email</th>
                  <th>Phone</th>
                  <th>Updated</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.id}>
                    <td>
                      <button type="button" className="row-link" onClick={() => openDrawer(r, 'profile')}>
                        {r.name}
                      </button>
                    </td>
                    <td>
                      <Link to={`/jobs/${r.jobId}`}>{r.jobTitle}</Link>
                    </td>
                    <td>{r.stageLabel}</td>
                    <td>{r.email ?? '—'}</td>
                    <td>{r.phone ?? '—'}</td>
                    <td className="muted">{new Date(r.updatedAt).toLocaleDateString()}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
      {open && (
        <CandidateDrawer
          key={open.id}
          row={open}
          canEdit={me.capabilities.includes('MANAGE_JOBS')}
          initialTab={openTab}
          onClose={() => setOpen(null)}
          onChanged={() => setReload((n) => n + 1)}
        />
      )}
    </div>
  )
}
