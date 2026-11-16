import React, { useState, useEffect } from 'react'
import { useParams, useSearchParams, useNavigate } from 'react-router-dom'
import { Printer, ArrowLeft, RefreshCw, CheckCircle2, AlertTriangle, ShieldCheck, Download } from 'lucide-react'

const API_BASE_URL = (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1')
  ? ('http://' + window.location.hostname + ':8080/api')
  : 'http://localhost:8080/api'

function getAuthHeaders() {
  const headers = { Accept: 'application/json' }
  const token = localStorage.getItem('jwtToken')
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

export default function ReceiptPage() {
  const { id } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const paymentId = id || searchParams.get('paymentId')
  const queryToken = searchParams.get('token') || searchParams.get('receiptToken')

  const [payment, setPayment] = useState(null)
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [reconciliationNotice, setReconciliationNotice] = useState('')
  const [pollCount, setPollCount] = useState(0)
  const isReconcilingRef = React.useRef(false)

  async function fetchRecord(isManual = false) {
    if (!paymentId) {
      setError('No payment reference specified.')
      setLoading(false)
      return
    }

    if (isReconcilingRef.current) {
      return
    }

    if (isManual) {
      setRefreshing(true)
    } else if (!payment) {
      setLoading(true)
    }
    setError('')
    try {
      const response = await fetch(`${API_BASE_URL}/payments/${paymentId}`, {
        method: 'GET',
        headers: getAuthHeaders(),
      })
      if (!response.ok) {
        throw new Error(`Payment record #${paymentId} not found (${response.status})`)
      }
      let data = await response.json()

      const activeToken = queryToken || data?.receiptToken || ''

      // If pending with PBL ref, trigger verified backend status check
      if (data?.pblOrderRef && String(data.status).toUpperCase() === 'PENDING') {
        isReconcilingRef.current = true
        try {
          const recUrl = activeToken
            ? `${API_BASE_URL}/sabbpe/reconciliation/payments/${paymentId}/reconcile?receiptToken=${encodeURIComponent(activeToken)}`
            : `${API_BASE_URL}/sabbpe/reconciliation/payments/${paymentId}/reconcile`

          const recHeaders = {
            Accept: 'application/json',
            'Content-Type': 'application/json',
          }
          const jwt = localStorage.getItem('jwtToken')
          if (jwt) recHeaders.Authorization = `Bearer ${jwt}`
          if (activeToken) recHeaders['X-Receipt-Token'] = activeToken

          const recRes = await fetch(recUrl, {
            method: 'POST',
            headers: recHeaders,
          })

          if (recRes.ok) {
            const recData = await recRes.json()
            if (recData?.status) {
              data = { ...data, ...recData }
            }
          } else if (recRes.status === 403) {
            setReconciliationNotice('Public reconciliation token validation failed. Contact merchant or refresh login.')
          }

          // Refetch fresh payment representation from database
          const refRes = await fetch(`${API_BASE_URL}/payments/${paymentId}`, {
            method: 'GET',
            headers: getAuthHeaders(),
          })
          if (refRes.ok) {
            data = await refRes.json()
          }
        } catch (recErr) {
          console.warn('[ReceiptPage] Reconciliation network attempt failed:', recErr)
          setReconciliationNotice('Reconciliation network check pending gateway settlement.')
        } finally {
          isReconcilingRef.current = false
        }
      }

      setPayment(data)
    } catch (err) {
      setError(err.message || 'Unable to retrieve payment information.')
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }

  useEffect(() => {
    fetchRecord(false)
  }, [paymentId])

  // Automatic polling when status is PENDING:
  // - Retries every 3 seconds
  // - Maximum 5 attempts per receipt visit
  // - Stops immediately when terminal status (SUCCESS / FAILED / CANCELLED / TIMEOUT) is reached
  // - Cleans up timer when unmounting or leaving page
  useEffect(() => {
    if (!payment) return
    const currentStatus = String(payment.status || '').toUpperCase()
    const terminalStatuses = ['SUCCESS', 'FAILED', 'FAILURE', 'CANCELLED', 'TIMEOUT', 'REFUNDED']

    if (terminalStatuses.includes(currentStatus)) {
      return // Stop polling immediately
    }

    if (currentStatus === 'PENDING' && payment.pblOrderRef && pollCount < 5) {
      const timer = setTimeout(() => {
        setPollCount((prev) => prev + 1)
        fetchRecord(false)
      }, 3000)
      return () => clearTimeout(timer)
    }
  }, [payment, pollCount])

  const status = String(payment?.status || 'UNKNOWN').toUpperCase()
  const isSuccess = status === 'SUCCESS'
  const isFailed = ['FAILED', 'FAILURE', 'CANCELLED', 'TIMEOUT'].includes(status)

  return (
    <div className="page-wrapper">
      <div className="receipt-container">
        {/* Action Header (Hidden in Print) */}
        <div className="receipt-action-bar no-print">
          <button className="btn-secondary" onClick={() => navigate('/transactions')}>
            <ArrowLeft size={16} />
            <span>Back to Transactions</span>
          </button>

          <div style={{ display: 'flex', gap: 10 }}>
            <button className="btn-secondary" onClick={() => fetchRecord(true)} disabled={loading || refreshing}>
              <RefreshCw size={16} className={(loading || refreshing) ? 'spin' : ''} />
              <span>{refreshing ? 'Verifying...' : 'Refresh Status'}</span>
            </button>
            <button className="btn-primary" onClick={() => window.print()} disabled={!payment}>
              <Printer size={16} />
              <span>Print Official Receipt</span>
            </button>
          </div>
        </div>

        {error ? (
          <div className="alert-box alert-danger">
            <AlertTriangle size={18} />
            <span>{error}</span>
          </div>
        ) : loading ? (
          <div className="receipt-sheet" style={{ textAlign: 'center', padding: '60px 20px' }}>
            <RefreshCw size={32} className="spin text-primary" style={{ margin: '0 auto 16px' }} />
            <h3>Loading Transaction Receipt #{paymentId}...</h3>
          </div>
        ) : (
          <div className="receipt-sheet printable-area">
            {/* Receipt Header */}
            <div className="receipt-header">
              <div className="receipt-brand">
                <div className="receipt-logo">₹</div>
                <div>
                  <h2>SABBPE PAYMENT PLATFORM</h2>
                  <p>One78 SabbPe Technology Solutions India Pvt. Ltd.</p>
                </div>
              </div>
              <div className="receipt-id-block">
                <span className="receipt-badge">OFFICIAL RECEIPT</span>
                <div className="receipt-order-no">Order #{payment.merchantOrderRef || payment.id}</div>
              </div>
            </div>

            <hr className="receipt-divider" />

            {/* Status Hero */}
            <div className={`receipt-status-banner ${isSuccess ? 'banner-success' : isFailed ? 'banner-failed' : 'banner-pending'}`}>
              {isSuccess ? <CheckCircle2 size={24} /> : isFailed ? <AlertTriangle size={24} /> : <RefreshCw size={24} />}
              <div>
                <strong>Payment Status: {status}</strong>
                <p>
                  {isSuccess
                    ? 'This transaction is confirmed and settled in UAT MariaDB.'
                    : isFailed
                    ? 'Transaction has failed, expired, or was cancelled at the gateway.'
                    : 'Transaction is currently awaiting final webhook/gateway settlement.'}
                </p>
              </div>
            </div>

            {/* Amount Box */}
            <div className="receipt-amount-box">
              <span>Total Amount Paid</span>
              <div className="receipt-amount-val">
                ₹{Number(payment.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              <small>Indian Rupee (INR) · Tax Inclusive</small>
            </div>

            {/* Details Grid */}
            <div className="receipt-details-grid">
              <div className="receipt-field">
                <span>Customer Name:</span>
                <strong>{payment.customerName || 'Customer'}</strong>
              </div>
              <div className="receipt-field">
                <span>Internal Payment ID:</span>
                <strong className="font-mono">#{payment.id}</strong>
              </div>
              <div className="receipt-field">
                <span>Merchant Order Reference:</span>
                <strong className="font-mono">{payment.merchantOrderRef || '—'}</strong>
              </div>
              <div className="receipt-field">
                <span>Gateway Provider:</span>
                <strong>{payment.gatewayProvider || 'SabbPe UAT'}</strong>
              </div>
              <div className="receipt-field">
                <span>Payment Method:</span>
                <strong>{payment.paymentMethod || 'Dynamic QR / PayByLink'}</strong>
              </div>
              <div className="receipt-field">
                <span>SabbPe PBL Reference:</span>
                <strong className="font-mono">{payment.pblOrderRef || '—'}</strong>
              </div>
              <div className="receipt-field">
                <span>Master Txn Reference:</span>
                <strong className="font-mono">{payment.masterTransactionId || payment.transactionId || '—'}</strong>
              </div>
              <div className="receipt-field">
                <span>Settlement Timestamp:</span>
                <strong>{new Date().toLocaleString('en-IN')}</strong>
              </div>
            </div>

            {/* Footer Notice */}
            <div className="receipt-footer">
              <div className="receipt-auth-badge">
                <ShieldCheck size={16} />
                <span>Verified by SabbPe UAT Ecosystem Server (192.168.1.10)</span>
              </div>
              <p>
                This computer-generated document is issued by the SabbPe Payment Platform and serves as valid
                proof of payment. For queries, contact support@sabbpe.com.
              </p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
