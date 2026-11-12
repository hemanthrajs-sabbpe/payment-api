import React, { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getPayments } from '../api'
import {
  CreditCard,
  CheckCircle2,
  Clock,
  AlertTriangle,
  ArrowRight,
  PlusCircle,
  Link2,
  QrCode,
  GitFork,
  History,
  TrendingUp,
  ShieldCheck,
  Building2
} from 'lucide-react'

export default function Dashboard() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false
    async function load() {
      setLoading(true)
      setError('')
      try {
        const data = await getPayments()
        if (Array.isArray(data) && !cancelled) {
          setPayments(data)
        }
      } catch (err) {
        if (!cancelled) setError(err.message || 'Unable to load dashboard metrics')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [])

  const normalizedStatuses = payments.map((p) => String(p.status || '').toUpperCase())
  const totalCount = payments.length
  const successCount = normalizedStatuses.filter((s) => s === 'SUCCESS').length
  const pendingCount = normalizedStatuses.filter((s) => s === 'PENDING').length
  const failedCount = normalizedStatuses.filter((s) =>
    ['FAILED', 'FAILURE', 'INITIATION_FAILED', 'TOKEN_GENERATION_FAILED', 'CANCELLED'].includes(s)
  ).length

  const totalVolume = payments
    .filter((p) => String(p.status).toUpperCase() === 'SUCCESS')
    .reduce((sum, p) => sum + (Number(p.amount) || 0), 0)

  return (
    <div className="page-wrapper">
      {/* Hero Welcome Banner */}
      <section className="welcome-banner">
        <div className="welcome-content">
          <div className="badge-pill">SABBPE UAT ECOSYSTEM READY</div>
          <h2>Merchant Payment Terminal</h2>
          <p>
            Process live transactions through SabbPe Dynamic QR and Pay by Link, manage 12 sector-specific group splits,
            and monitor automated webhook status updates directly from UAT MariaDB.
          </p>
        </div>
        <div className="welcome-cta">
          <button className="btn-primary" onClick={() => navigate('/create-payment')}>
            <PlusCircle size={18} />
            <span>Create Payment</span>
          </button>
          <button className="btn-secondary" onClick={() => navigate('/pay-by-link')}>
            <Link2 size={18} />
            <span>Generate Link</span>
          </button>
        </div>
      </section>

      {error && (
        <div className="alert-box alert-warning">
          <AlertTriangle size={18} />
          <div>
            <strong>Backend Connection Notice</strong>
            <p>{error}</p>
          </div>
        </div>
      )}

      {/* Metrics Row */}
      <section className="metrics-grid">
        <div className="metric-card metric-blue">
          <div className="metric-header">
            <span>Total Transactions</span>
            <div className="metric-icon-wrap"><CreditCard size={20} /></div>
          </div>
          <div className="metric-value">{loading ? '...' : totalCount}</div>
          <div className="metric-subtext">Recorded in UAT MariaDB</div>
        </div>

        <div className="metric-card metric-green">
          <div className="metric-header">
            <span>Successful Settlements</span>
            <div className="metric-icon-wrap"><CheckCircle2 size={20} /></div>
          </div>
          <div className="metric-value">{loading ? '...' : successCount}</div>
          <div className="metric-subtext">Volume: ₹{loading ? '...' : totalVolume.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</div>
        </div>

        <div className="metric-card metric-amber">
          <div className="metric-header">
            <span>Pending Verifications</span>
            <div className="metric-icon-wrap"><Clock size={20} /></div>
          </div>
          <div className="metric-value">{loading ? '...' : pendingCount}</div>
          <div className="metric-subtext">Awaiting customer payment</div>
        </div>

        <div className="metric-card metric-red">
          <div className="metric-header">
            <span>Failed / Cancelled</span>
            <div className="metric-icon-wrap"><AlertTriangle size={20} /></div>
          </div>
          <div className="metric-value">{loading ? '...' : failedCount}</div>
          <div className="metric-subtext">Declined or expired requests</div>
        </div>
      </section>

      {/* Analytics & Quick Action Section */}
      <div className="dashboard-columns">
        {/* Status Distribution */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Status Distribution</h3>
              <p className="card-subtitle">Real-time ledger breakdown</p>
            </div>
            <span className="live-tag">Live Database</span>
          </div>

          <div className="distribution-bars">
            {[
              { label: 'Successful', count: successCount, color: '#10b981' },
              { label: 'Pending', count: pendingCount, color: '#f59e0b' },
              { label: 'Failed / Cancelled', count: failedCount, color: '#ef4444' },
            ].map((item) => {
              const pct = totalCount ? Math.round((item.count / totalCount) * 100) : 0
              return (
                <div key={item.label} className="dist-row">
                  <div className="dist-label-row">
                    <span>{item.label}</span>
                    <strong>{loading ? '...' : `${item.count} (${pct}%)`}</strong>
                  </div>
                  <div className="progress-track">
                    <div
                      className="progress-fill"
                      style={{ width: `${pct}%`, backgroundColor: item.color }}
                    />
                  </div>
                </div>
              )
            })}
          </div>

          <div className="provider-badges-box">
            <span className="badge-outline"><ShieldCheck size={14} /> SabbPe Gateway</span>
            <span className="badge-outline"><Link2 size={14} /> Pay by Link</span>
            <span className="badge-outline"><Building2 size={14} /> Easebuzz Hosted</span>
          </div>
        </div>

        {/* Quick Actions Card */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Quick Actions</h3>
              <p className="card-subtitle">One-click payment management</p>
            </div>
          </div>

          <div className="actions-grid">
            <button className="action-tile" onClick={() => navigate('/create-payment')}>
              <div className="action-tile-icon icon-blue"><PlusCircle size={20} /></div>
              <div className="action-tile-info">
                <strong>Create Payment</strong>
                <small>Checkout, Gateway & Sandbox</small>
              </div>
              <ArrowRight size={16} className="action-tile-arrow" />
            </button>

            <button className="action-tile" onClick={() => navigate('/pay-by-link')}>
              <div className="action-tile-icon icon-purple"><Link2 size={20} /></div>
              <div className="action-tile-info">
                <strong>Pay by Link</strong>
                <small>SMS & WhatsApp sharing</small>
              </div>
              <ArrowRight size={16} className="action-tile-arrow" />
            </button>

            <button className="action-tile" onClick={() => navigate('/qr-payments')}>
              <div className="action-tile-icon icon-green"><QrCode size={20} /></div>
              <div className="action-tile-info">
                <strong>Dynamic QR Payment</strong>
                <small>Scan-and-pay with UPI</small>
              </div>
              <ArrowRight size={16} className="action-tile-arrow" />
            </button>

            <button className="action-tile" onClick={() => navigate('/split-payments')}>
              <div className="action-tile-icon icon-orange"><GitFork size={20} /></div>
              <div className="action-tile-info">
                <strong>Sector-Wise Splits</strong>
                <small>12 business categories</small>
              </div>
              <ArrowRight size={16} className="action-tile-arrow" />
            </button>
          </div>
        </div>
      </div>

      {/* Recent Activity Table */}
      <div className="card">
        <div className="card-header">
          <div>
            <h3>Recent Payment Activity</h3>
            <p className="card-subtitle">Last 5 records from UAT database</p>
          </div>
          <button className="btn-text" onClick={() => navigate('/transactions')}>
            <span>View All</span>
            <ArrowRight size={16} />
          </button>
        </div>

        {loading ? (
          <div className="table-loading">Loading transaction records...</div>
        ) : payments.length === 0 ? (
          <div className="table-empty">No payment transactions found in database.</div>
        ) : (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Customer Name</th>
                  <th>Order Reference</th>
                  <th>Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {payments.slice(0, 5).map((p) => {
                  const status = String(p.status || 'UNKNOWN').toUpperCase()
                  let badgeClass = 'status-pending'
                  if (status === 'SUCCESS') badgeClass = 'status-success'
                  else if (['FAILED', 'FAILURE', 'CANCELLED'].includes(status)) badgeClass = 'status-failed'

                  return (
                    <tr key={p.id}>
                      <td className="font-mono">#{p.id}</td>
                      <td><strong>{p.customerName || 'Customer'}</strong></td>
                      <td className="font-mono text-muted">{p.merchantOrderRef || p.pblOrderRef || '—'}</td>
                      <td>{p.paymentMethod || p.gatewayProvider || 'SabbPe UAT'}</td>
                      <td><strong>₹{Number(p.amount || 0).toFixed(2)}</strong></td>
                      <td><span className={`badge ${badgeClass}`}>{status}</span></td>
                      <td>
                        <button
                          className="btn-sm"
                          onClick={() => navigate(`/receipt/${p.id}`)}
                        >
                          View Details
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
