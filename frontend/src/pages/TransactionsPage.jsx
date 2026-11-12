import React, { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { getPayments } from '../api'
import { History, RefreshCw, FileText, CheckCircle2, Clock, AlertTriangle, Layers, UserCheck } from 'lucide-react'

const API_BASE_URL = (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1')
  ? ('http://' + window.location.hostname + ':8080/api')
  : 'http://localhost:8080/api'

function getAuthHeaders() {
  const headers = { Accept: 'application/json' }
  const token = localStorage.getItem('jwtToken')
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

export default function TransactionsPage() {
  const navigate = useNavigate()
  const [payments, setPayments] = useState([])
  const [masterTransactions, setMasterTransactions] = useState([])
  const [individualTransactions, setIndividualTransactions] = useState([])
  const [view, setView] = useState('payments')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const money = (val) => (val == null || Number.isNaN(Number(val)))
    ? '—'
    : `₹${Number(val).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

  async function requestJson(path) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: 'GET',
      headers: getAuthHeaders(),
    })
    const body = await response.text()
    if (!response.ok) {
      let message = body
      try { message = JSON.parse(body)?.message || JSON.parse(body)?.error || body } catch {}
      throw new Error(message || `Request failed (${response.status})`)
    }
    if (!body) return null
    return JSON.parse(body)
  }

  async function loadData() {
    setLoading(true)
    setError('')
    try {
      const [paymentData, splitResponse] = await Promise.all([
        getPayments(),
        requestJson('/payment-splits'),
      ])

      const splits = Array.isArray(splitResponse)
        ? splitResponse
        : Array.isArray(splitResponse?.content) ? splitResponse.content : []

      const detailResults = await Promise.all(splits.map(async (listedSplit) => {
        try {
          const detail = await requestJson(`/payment-splits/${encodeURIComponent(listedSplit.id)}`)
          const master = detail?.masterBill || detail?.split || detail || listedSplit
          const summary = detail?.summary || detail?.paymentSummary || {}
          let participants = Array.isArray(detail?.participants) ? detail.participants : []

          if (!participants.length) {
            try {
              const allocationRows = await requestJson(`/payment-splits/${encodeURIComponent(master.id ?? listedSplit.id)}/allocations`)
              if (Array.isArray(allocationRows)) participants = allocationRows
            } catch {}
          }

          const splitId = master.id ?? listedSplit.id
          const totalAmount = Number(master.totalAmount ?? summary.totalAmount ?? listedSplit.totalAmount ?? 0)
          const paidAmount = Number(summary.paidAmount ?? master.paidAmount ?? 0)
          const remainingAmount = Number(summary.remainingAmount ?? Math.max(0, totalAmount - paidAmount))

          const normalizedParticipants = participants.map((p) => ({
            ...p,
            splitId,
            splitReference: master.splitReference || listedSplit.splitReference || null,
            id: p.allocationId ?? p.id,
            paymentId: p.paymentId ?? null,
            participantName: p.participantName ?? p.customerName ?? 'Participant',
            allocationAmount: p.allocationAmount ?? p.amount ?? 0,
            paidAmount: p.paidAmount ?? 0,
            status: p.allocationStatus ?? p.status ?? 'PENDING',
          }))

          return {
            id: splitId,
            splitReference: master.splitReference || listedSplit.splitReference || null,
            splitType: master.splitType || listedSplit.splitType || 'EQUAL',
            paymentMode: master.paymentMode || listedSplit.paymentMode || 'EACH_PAYS',
            totalAmount,
            paidAmount,
            remainingAmount,
            paidParticipants: participants.filter((item) => String(item.allocationStatus || item.status || '').toUpperCase() === 'SUCCESS').length,
            totalParticipants: participants.length,
            status: master.status || listedSplit.status || 'PENDING',
            createdAt: master.createdAt || null,
            participants: normalizedParticipants,
          }
        } catch {
          return {
            id: listedSplit.id,
            splitReference: listedSplit.splitReference || null,
            splitType: listedSplit.splitType || 'EQUAL',
            paymentMode: listedSplit.paymentMode || 'EACH_PAYS',
            totalAmount: Number(listedSplit.totalAmount || 0),
            paidAmount: 0,
            remainingAmount: Number(listedSplit.totalAmount || 0),
            paidParticipants: 0,
            totalParticipants: 0,
            status: listedSplit.status || 'PENDING',
            participants: [],
          }
        }
      }))

      setPayments(Array.isArray(paymentData) ? paymentData : [])
      setMasterTransactions(detailResults)
      setIndividualTransactions(detailResults.flatMap((s) => s.participants || []))
    } catch (err) {
      setError(err.message || 'Unable to load transaction records')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadData()
  }, [])

  return (
    <div className="page-wrapper">
      <div className="card">
        <div className="card-header">
          <div>
            <h3>Transaction Ledger & Audit Trail</h3>
            <p className="card-subtitle">Verified records synchronized between UAT MariaDB and SabbPe Ecosystem</p>
          </div>
          <button className="btn-secondary" onClick={loadData} disabled={loading}>
            <RefreshCw size={16} className={loading ? 'spin' : ''} />
            <span>{loading ? 'Refreshing...' : 'Refresh Ledger'}</span>
          </button>
        </div>

        {error && (
          <div className="alert-box alert-danger">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
        )}

        <div className="tab-pills-bar">
          <button
            className={`tab-pill ${view === 'payments' ? 'active' : ''}`}
            onClick={() => setView('payments')}
          >
            <span>All Payments</span>
            <span className="tab-pill-count">{payments.length}</span>
          </button>
          <button
            className={`tab-pill ${view === 'master' ? 'active' : ''}`}
            onClick={() => setView('master')}
          >
            <span>Master Splits</span>
            <span className="tab-pill-count">{masterTransactions.length}</span>
          </button>
          <button
            className={`tab-pill ${view === 'individual' ? 'active' : ''}`}
            onClick={() => setView('individual')}
          >
            <span>Participant Allocations</span>
            <span className="tab-pill-count">{individualTransactions.length}</span>
          </button>
        </div>

        {/* View 1: Direct Payments */}
        {view === 'payments' && (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Payment ID</th>
                  <th>Customer Name</th>
                  <th>Order Reference</th>
                  <th>Provider / Method</th>
                  <th>Amount</th>
                  <th>Status</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan="7" className="table-loading">Querying UAT database...</td></tr>
                ) : payments.length === 0 ? (
                  <tr><td colSpan="7" className="table-empty">No payments recorded yet.</td></tr>
                ) : (
                  payments.map((p) => {
                    const status = String(p.status || 'UNKNOWN').toUpperCase()
                    let badgeClass = 'status-pending'
                    if (status === 'SUCCESS') badgeClass = 'status-success'
                    else if (['FAILED', 'FAILURE', 'CANCELLED'].includes(status)) badgeClass = 'status-failed'

                    return (
                      <tr key={p.id}>
                        <td className="font-mono"><strong>#{p.id}</strong></td>
                        <td><strong>{p.customerName || 'Customer'}</strong></td>
                        <td className="font-mono text-muted">{p.merchantOrderRef || p.pblOrderRef || '—'}</td>
                        <td>{p.gatewayProvider || p.paymentMethod || 'SabbPe'}</td>
                        <td><strong>{money(p.amount)}</strong></td>
                        <td><span className={`badge ${badgeClass}`}>{status}</span></td>
                        <td>
                          <button
                            className="btn-sm"
                            onClick={() => navigate(`/receipt/${p.id}`)}
                          >
                            <FileText size={14} style={{ display: 'inline', verticalAlign: -2 }} />
                            <span>Receipt</span>
                          </button>
                        </td>
                      </tr>
                    )
                  })
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* View 2: Master Splits */}
        {view === 'master' && (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Split ID</th>
                  <th>Split Reference</th>
                  <th>Type & Mode</th>
                  <th>Total Amount</th>
                  <th>Paid Amount</th>
                  <th>Remaining</th>
                  <th>Participants</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan="8" className="table-loading">Loading master splits...</td></tr>
                ) : masterTransactions.length === 0 ? (
                  <tr><td colSpan="8" className="table-empty">No master split bills found.</td></tr>
                ) : (
                  masterTransactions.map((s) => (
                    <tr key={s.id}>
                      <td className="font-mono"><strong>#{s.id}</strong></td>
                      <td className="font-mono text-muted">{s.splitReference || '—'}</td>
                      <td>{s.splitType} · {s.paymentMode}</td>
                      <td><strong>{money(s.totalAmount)}</strong></td>
                      <td className="text-success"><strong>{money(s.paidAmount)}</strong></td>
                      <td className="text-danger">{money(s.remainingAmount)}</td>
                      <td>{s.paidParticipants} / {s.totalParticipants} paid</td>
                      <td>
                        <span className={`badge ${s.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                          {s.status}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* View 3: Individual Allocations */}
        {view === 'individual' && (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Split #</th>
                  <th>Alloc ID</th>
                  <th>Participant</th>
                  <th>Payment ID</th>
                  <th>Allocated Share</th>
                  <th>Paid</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {loading ? (
                  <tr><td colSpan="7" className="table-loading">Loading allocations...</td></tr>
                ) : individualTransactions.length === 0 ? (
                  <tr><td colSpan="7" className="table-empty">No participant allocations found.</td></tr>
                ) : (
                  individualTransactions.map((alloc) => (
                    <tr key={`${alloc.splitId}-${alloc.id}`}>
                      <td className="font-mono">#{alloc.splitId}</td>
                      <td className="font-mono">#{alloc.id}</td>
                      <td><strong>{alloc.participantName}</strong></td>
                      <td className="font-mono text-muted">{alloc.paymentId ? `#${alloc.paymentId}` : 'Not linked'}</td>
                      <td><strong>{money(alloc.allocationAmount)}</strong></td>
                      <td className="text-success">{money(alloc.paidAmount)}</td>
                      <td>
                        <span className={`badge ${alloc.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                          {alloc.status}
                        </span>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
