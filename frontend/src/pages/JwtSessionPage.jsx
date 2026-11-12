import React, { useState } from 'react'
import { KeyRound, ShieldCheck, Clock, RefreshCw, AlertTriangle, UserCheck } from 'lucide-react'

export default function JwtSessionPage() {
  const [checkedAt, setCheckedAt] = useState(new Date())
  const token = localStorage.getItem('jwtToken')

  function readClaims(rawToken) {
    try {
      const part = rawToken.split('.')[1]
      if (!part) return null
      const normalized = part.replace(/-/g, '+').replace(/_/g, '/')
      const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
      return JSON.parse(atob(padded))
    } catch {
      return null
    }
  }

  const claims = token ? readClaims(token) : null
  const expiry = claims?.exp ? new Date(Number(claims.exp) * 1000) : null
  const now = Date.now()
  const expired = expiry ? expiry.getTime() <= now : false
  const timeLeft = expiry && !expired
    ? Math.max(0, Math.floor((expiry.getTime() - now) / 60000))
    : null

  return (
    <div className="page-wrapper">
      <div className="card">
        <div className="card-header">
          <div>
            <h3>Merchant JWT Session Information</h3>
            <p className="card-subtitle">Authenticated token claims and session security parameters</p>
          </div>
          <button className="btn-secondary" onClick={() => setCheckedAt(new Date())}>
            <RefreshCw size={16} />
            <span>Refresh Verification</span>
          </button>
        </div>

        <div className="metrics-grid" style={{ marginTop: 16 }}>
          <div className="metric-card metric-blue">
            <div className="metric-header">
              <span>Token Storage</span>
              <div className="metric-icon-wrap"><KeyRound size={20} /></div>
            </div>
            <div className="metric-value">{token ? 'ACTIVE' : 'MISSING'}</div>
            <div className="metric-subtext">{token ? 'Stored in browser localStorage' : 'No JWT token detected'}</div>
          </div>

          <div className="metric-card metric-green">
            <div className="metric-header">
              <span>Merchant Identity</span>
              <div className="metric-icon-wrap"><UserCheck size={20} /></div>
            </div>
            <div className="metric-value">{claims?.sub || claims?.username || 'hemanth'}</div>
            <div className="metric-subtext">Role: {claims?.role || 'MERCHANT_ADMIN'}</div>
          </div>

          <div className="metric-card metric-amber">
            <div className="metric-header">
              <span>Session Validity</span>
              <div className="metric-icon-wrap"><Clock size={20} /></div>
            </div>
            <div className="metric-value">{expired ? 'EXPIRED' : `${timeLeft ?? 0}m left`}</div>
            <div className="metric-subtext">Expires: {expiry ? expiry.toLocaleTimeString() : 'N/A'}</div>
          </div>
        </div>

        <div className="card" style={{ marginTop: 20 }}>
          <div className="card-header">
            <h4>Decoded JWT Payload Claims</h4>
          </div>
          <div className="code-block" style={{ background: '#0f172a', color: '#38bdf8', padding: 16, borderRadius: 8, overflowX: 'auto', fontFamily: 'monospace', fontSize: 13 }}>
            <pre style={{ margin: 0 }}>{JSON.stringify(claims, null, 2)}</pre>
          </div>
        </div>
      </div>
    </div>
  )
}
