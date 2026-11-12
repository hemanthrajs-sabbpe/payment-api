import React, { useState } from 'react'
import { FileCheck2, RefreshCw, CheckCircle2, AlertTriangle, ShieldCheck } from 'lucide-react'

const API_BASE_URL = (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1')
  ? ('http://' + window.location.hostname + ':8080/api')
  : 'http://localhost:8080/api'

function getAuthHeaders() {
  const headers = { Accept: 'application/json' }
  const token = localStorage.getItem('jwtToken')
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

export default function ReconciliationPage() {
  const [paymentId, setPaymentId] = useState('246')
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')

  async function handleReconcile(e) {
    e.preventDefault()
    if (!paymentId.trim()) return
    setLoading(true)
    setError('')
    setResult(null)

    try {
      const response = await fetch(`${API_BASE_URL}/sabbpe/reconciliation/payments/${paymentId.trim()}/reconcile`, {
        method: 'POST',
        headers: getAuthHeaders(),
      })
      const text = await response.text()
      let data = text
      try { data = text ? JSON.parse(text) : null } catch {}

      if (!response.ok) {
        throw new Error(typeof data === 'string' ? data : (data?.message || data?.error || `Reconciliation failed (${response.status})`))
      }
      setResult(data)
    } catch (err) {
      setError(err.message || 'Unable to execute reconciliation query.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="page-wrapper">
      <div className="card">
        <div className="card-header">
          <div>
            <h3>SabbPe Ecosystem Reconciliation</h3>
            <p className="card-subtitle">
              Verify local payment records directly against SabbPe UAT provider status and update the internal ledger
            </p>
          </div>
          <FileCheck2 size={24} className="text-primary" />
        </div>

        <form onSubmit={handleReconcile} className="standard-form" style={{ maxWidth: 500, marginTop: 16 }}>
          <div className="form-group">
            <label htmlFor="recPaymentId">Database Payment ID *</label>
            <input
              id="recPaymentId"
              value={paymentId}
              onChange={(e) => setPaymentId(e.target.value)}
              placeholder="e.g. 246"
              required
              disabled={loading}
            />
          </div>

          {error && (
            <div className="alert-box alert-danger">
              <AlertTriangle size={18} />
              <span>{error}</span>
            </div>
          )}

          <button type="submit" disabled={loading} className="btn-primary">
            <RefreshCw size={16} className={loading ? 'spin' : ''} />
            <span>{loading ? 'Reconciling with SabbPe...' : 'Run Verified Reconciliation'}</span>
          </button>
        </form>

        {result && (
          <div className="card" style={{ marginTop: 24, background: '#f8fafc' }}>
            <div className="card-header">
              <h4>Reconciliation Response</h4>
              <span className="badge status-success">Verified with SabbPe</span>
            </div>
            <div className="code-block" style={{ background: '#0f172a', color: '#10b981', padding: 16, borderRadius: 8, overflowX: 'auto', fontFamily: 'monospace', fontSize: 13 }}>
              <pre style={{ margin: 0 }}>{JSON.stringify(result, null, 2)}</pre>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
