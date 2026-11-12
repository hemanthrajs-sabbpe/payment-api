import React, { useState, useEffect } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { getSimulatorQrDetails, processSimulatorOutcome } from '../api'
import { CheckCircle2, XCircle, Clock, AlertCircle, ShieldCheck, ArrowRight, RefreshCw } from 'lucide-react'

export default function QrPaymentSimulatorPage() {
  const [searchParams] = useSearchParams()
  const rawToken = searchParams.get('token') || ''

  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [details, setDetails] = useState(null)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)

  useEffect(() => {
    if (!rawToken) {
      setError('Missing QR token. Please scan an authorized payment QR code.')
      setLoading(false)
      return
    }

    fetchDetails()
  }, [rawToken])

  async function fetchDetails() {
    setLoading(true)
    setError('')
    try {
      const data = await getSimulatorQrDetails(rawToken)
      setDetails(data)
    } catch (err) {
      setError(err.message || 'Failed to load QR payment details.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSimulate(action) {
    if (!rawToken || !details?.paymentId) {
      setError('Cannot process payment: missing token or payment ID.')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      const res = await processSimulatorOutcome({
        token: rawToken,
        paymentId: details.paymentId,
        action: action, // 'SUCCESS' or 'FAILURE'
      })
      setResult(res)
    } catch (err) {
      setError(err.message || 'Payment simulation failed.')
    } finally {
      setSubmitting(false)
    }
  }

  const formatCurrency = (val, cur = 'INR') => {
    const num = Number(val) || 0
    return `${cur === 'INR' ? '₹' : cur + ' '}${num.toFixed(2)}`
  }

  const formatExpiry = (isoString) => {
    if (!isoString) return '—'
    try {
      return new Date(isoString).toLocaleString('en-IN', {
        dateStyle: 'medium',
        timeStyle: 'medium',
      })
    } catch {
      return isoString
    }
  }

  return (
    <div style={{
      minHeight: '100vh',
      background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
      padding: '24px 16px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: 'Inter, "Segoe UI", sans-serif',
      color: '#0f172a'
    }}>
      <div style={{
        width: '100%',
        maxWidth: '460px',
        background: '#ffffff',
        borderRadius: '16px',
        boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.2)',
        overflow: 'hidden'
      }}>
        {/* HEADER */}
        <div style={{
          background: '#1e293b',
          color: '#ffffff',
          padding: '20px 24px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid rgba(255, 255, 255, 0.1)'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <div style={{
              width: '36px',
              height: '36px',
              borderRadius: '8px',
              background: '#3b82f6',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <ShieldCheck size={22} color="#ffffff" />
            </div>
            <div>
              <h2 style={{ margin: 0, fontSize: '17px', fontWeight: '600' }}>Customer QR Simulator</h2>
              <p style={{ margin: 0, fontSize: '11px', color: '#94a3b8' }}>Secure Sandbox Payment Verification</p>
            </div>
          </div>
          <span style={{
            fontSize: '11px',
            background: 'rgba(59, 130, 246, 0.2)',
            color: '#60a5fa',
            padding: '3px 8px',
            borderRadius: '12px',
            fontWeight: '600',
            textTransform: 'uppercase'
          }}>
            Sandbox
          </span>
        </div>

        {/* BODY */}
        <div style={{ padding: '24px' }}>
          {loading && (
            <div style={{ textAlign: 'center', padding: '40px 10px' }}>
              <RefreshCw size={32} color="#3b82f6" className="spin" style={{ margin: '0 auto 12px' }} />
              <p style={{ color: '#64748b', fontSize: '14px', margin: 0 }}>Validating QR payment token...</p>
            </div>
          )}

          {!loading && error && !result && (
            <div style={{
              background: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '12px',
              padding: '20px',
              textAlign: 'center'
            }}>
              <AlertCircle size={36} color="#ef4444" style={{ margin: '0 auto 10px' }} />
              <h3 style={{ margin: '0 0 6px', color: '#991b1b', fontSize: '16px' }}>Unable to Complete Payment</h3>
              <p style={{ margin: '0 0 16px', color: '#b91c1c', fontSize: '13px', lineHeight: 1.4 }}>
                {error}
              </p>
              {rawToken && (
                <button
                  type="button"
                  onClick={fetchDetails}
                  style={{
                    padding: '8px 16px',
                    borderRadius: '8px',
                    border: '1px solid #dc2626',
                    background: '#ffffff',
                    color: '#dc2626',
                    fontSize: '13px',
                    fontWeight: '600'
                  }}
                >
                  Retry Scan Validation
                </button>
              )}
            </div>
          )}

          {!loading && result && (
            <div>
              {result.status === 'SUCCESS' && (
                <div style={{
                  background: '#f0fdf4',
                  border: '1px solid #bbf7d0',
                  borderRadius: '12px',
                  padding: '24px 20px',
                  textAlign: 'center'
                }}>
                  <CheckCircle2 size={48} color="#16a34a" style={{ margin: '0 auto 12px' }} />
                  <h3 style={{ margin: '0 0 6px', color: '#166534', fontSize: '18px' }}>Payment Approved</h3>
                  <p style={{ margin: '0 0 16px', color: '#15803d', fontSize: '13px' }}>
                    {result.message || 'Payment completed successfully.'}
                  </p>
                  <div style={{
                    background: '#ffffff',
                    border: '1px solid #dcfce7',
                    borderRadius: '8px',
                    padding: '12px',
                    fontSize: '13px',
                    textAlign: 'left',
                    marginBottom: '16px'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span style={{ color: '#64748b' }}>Amount Paid:</span>
                      <strong style={{ color: '#166534' }}>{formatCurrency(result.amount)}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span style={{ color: '#64748b' }}>Payment ID:</span>
                      <strong>#{result.paymentId}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ color: '#64748b' }}>Allocation ID:</span>
                      <strong>#{result.allocationId}</strong>
                    </div>
                  </div>
                  <span style={{
                    display: 'inline-block',
                    fontSize: '12px',
                    color: '#15803d',
                    fontWeight: '500'
                  }}>
                    Allocation marked SUCCESS on merchant ledger.
                  </span>
                </div>
              )}

              {result.status === 'FAILED' && (
                <div style={{
                  background: '#fef2f2',
                  border: '1px solid #fecaca',
                  borderRadius: '12px',
                  padding: '24px 20px',
                  textAlign: 'center'
                }}>
                  <XCircle size={48} color="#dc2626" style={{ margin: '0 auto 12px' }} />
                  <h3 style={{ margin: '0 0 6px', color: '#991b1b', fontSize: '18px' }}>Payment Simulated as Failed</h3>
                  <p style={{ margin: '0 0 16px', color: '#b91c1c', fontSize: '13px' }}>
                    {result.message || 'Payment attempt was failed as requested.'}
                  </p>
                  <div style={{
                    background: '#ffffff',
                    border: '1px solid #fee2e2',
                    borderRadius: '8px',
                    padding: '12px',
                    fontSize: '13px',
                    textAlign: 'left',
                    marginBottom: '16px'
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
                      <span style={{ color: '#64748b' }}>Attempted Amount:</span>
                      <strong style={{ color: '#991b1b' }}>{formatCurrency(result.amount)}</strong>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <span style={{ color: '#64748b' }}>Payment ID:</span>
                      <strong>#{result.paymentId}</strong>
                    </div>
                  </div>
                  <span style={{
                    display: 'inline-block',
                    fontSize: '12px',
                    color: '#991b1b',
                    fontWeight: '500'
                  }}>
                    Session expired. Merchant may re-issue an allocation QR.
                  </span>
                </div>
              )}

              {result.status === 'TIMEOUT' && (
                <div style={{
                  background: '#fffbeb',
                  border: '1px solid #fde68a',
                  borderRadius: '12px',
                  padding: '24px 20px',
                  textAlign: 'center'
                }}>
                  <Clock size={48} color="#d97706" style={{ margin: '0 auto 12px' }} />
                  <h3 style={{ margin: '0 0 6px', color: '#92400e', fontSize: '18px' }}>QR Code Expired</h3>
                  <p style={{ margin: '0 0 16px', color: '#b45309', fontSize: '13px' }}>
                    {result.message || 'The QR session reached its expiry before authorization.'}
                  </p>
                  <span style={{
                    display: 'inline-block',
                    fontSize: '12px',
                    color: '#92400e',
                    fontWeight: '500'
                  }}>
                    Recorded as TIMEOUT. Ask merchant to generate a new QR.
                  </span>
                </div>
              )}
            </div>
          )}

          {!loading && !result && details && (
            <div>
              {/* DETAILS SUMMARY CARD */}
              <div style={{
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                borderRadius: '12px',
                padding: '16px 20px',
                marginBottom: '20px'
              }}>
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'baseline',
                  marginBottom: '12px',
                  borderBottom: '1px solid #e2e8f0',
                  paddingBottom: '10px'
                }}>
                  <div>
                    <span style={{ fontSize: '11px', color: '#64748b', textTransform: 'uppercase', fontWeight: '600' }}>
                      Split Group
                    </span>
                    <h4 style={{ margin: '2px 0 0', fontSize: '15px', color: '#1e293b' }}>
                      {details.splitTitle || 'Split Payment'}
                    </h4>
                  </div>
                  <span style={{
                    fontSize: '12px',
                    background: '#eff6ff',
                    color: '#2563eb',
                    padding: '3px 8px',
                    borderRadius: '6px',
                    fontWeight: '600'
                  }}>
                    {details.status}
                  </span>
                </div>

                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', fontSize: '13px' }}>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '11px' }}>Participant</span>
                    <strong style={{ color: '#0f172a' }}>{details.participantName || 'Guest'}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '11px' }}>Payment Attempt</span>
                    <strong style={{ color: '#0f172a' }}>#{details.paymentId}</strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '11px' }}>Payable Amount</span>
                    <strong style={{ color: '#16a34a', fontSize: '16px' }}>
                      {formatCurrency(details.amount, details.currency)}
                    </strong>
                  </div>
                  <div>
                    <span style={{ color: '#64748b', display: 'block', fontSize: '11px' }}>Expires At</span>
                    <span style={{ color: '#64748b', fontSize: '12px' }}>
                      {formatExpiry(details.expiresAt)}
                    </span>
                  </div>
                </div>
              </div>

              {/* ACTION BUTTONS */}
              <div style={{ marginBottom: '16px' }}>
                <p style={{
                  margin: '0 0 10px',
                  fontSize: '12px',
                  color: '#475569',
                  textAlign: 'center',
                  fontWeight: '500'
                }}>
                  Simulate Customer Mobile UPI Action:
                </p>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <button
                    type="button"
                    disabled={submitting}
                    onClick={() => handleSimulate('SUCCESS')}
                    style={{
                      background: '#16a34a',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '10px',
                      padding: '12px 16px',
                      fontSize: '14px',
                      fontWeight: '600',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                      boxShadow: '0 2px 4px rgba(22, 163, 74, 0.2)',
                      cursor: submitting ? 'not-allowed' : 'pointer',
                      opacity: submitting ? 0.7 : 1
                    }}
                  >
                    <CheckCircle2 size={18} />
                    Pay SUCCESS
                  </button>

                  <button
                    type="button"
                    disabled={submitting}
                    onClick={() => handleSimulate('FAILURE')}
                    style={{
                      background: '#dc2626',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '10px',
                      padding: '12px 16px',
                      fontSize: '14px',
                      fontWeight: '600',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '8px',
                      boxShadow: '0 2px 4px rgba(220, 38, 38, 0.2)',
                      cursor: submitting ? 'not-allowed' : 'pointer',
                      opacity: submitting ? 0.7 : 1
                    }}
                  >
                    <XCircle size={18} />
                    Fail PAYMENT
                  </button>
                </div>
              </div>

              <div style={{
                background: '#f1f5f9',
                borderRadius: '8px',
                padding: '10px 12px',
                fontSize: '11px',
                color: '#64748b',
                lineHeight: 1.4,
                textAlign: 'center'
              }}>
                This is a sandboxed verification environment. No actual banking networks, cards, or accounts are billed.
              </div>
            </div>
          )}
        </div>

        {/* FOOTER */}
        <div style={{
          background: '#f8fafc',
          borderTop: '1px solid #e2e8f0',
          padding: '12px 24px',
          textAlign: 'center',
          fontSize: '12px',
          color: '#94a3b8'
        }}>
          SabbPe QR Payment Simulation Service
        </div>
      </div>
    </div>
  )
}
