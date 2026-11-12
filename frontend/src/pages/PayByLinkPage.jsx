import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { createSabbPePayment } from '../api'
import { Link2, Copy, ExternalLink, RefreshCw, FileText, CheckCircle2, AlertCircle } from 'lucide-react'

export default function PayByLinkPage() {
  const navigate = useNavigate()
  const [customerName, setCustomerName] = useState('PBL SIAN')
  const [email, setEmail] = useState('customer@sabbpe.com')
  const [mobile, setMobile] = useState('9876543210')
  const [amount, setAmount] = useState('100.00')
  const [description, setDescription] = useState('SabbPe Pay by Link collection')
  const [generatedLink, setGeneratedLink] = useState('')
  const [paymentId, setPaymentId] = useState(null)
  const [pblResult, setPblResult] = useState(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  function findPaymentId(result) {
    const candidates = [
      result?.paymentId, result?.payment_id, result?.payment?.id,
      result?.data?.paymentId, result?.data?.payment_id,
      result?.data?.payment?.id, result?.data?.id, result?.id,
    ]
    for (const value of candidates) {
      const number = Number(value)
      if (Number.isInteger(number) && number > 0) return number
    }
    return null
  }

  function findCheckoutUrl(result) {
    const keys = ['checkoutUrl', 'checkout_url', 'paymentUrl', 'payment_url', 'paymentLink', 'payment_link', 'redirectUrl', 'redirect_url', 'url', 'link']
    const seen = new Set()
    function search(value, depth = 0) {
      if (typeof value === 'string') return /^https?:\/\//i.test(value) ? value : ''
      if (!value || typeof value !== 'object' || depth > 5 || seen.has(value)) return ''
      seen.add(value)
      for (const key of keys) {
        const candidate = value[key]
        if (typeof candidate === 'string' && /^https?:\/\//i.test(candidate)) return candidate
      }
      for (const child of Object.values(value)) {
        const found = search(child, depth + 1)
        if (found) return found
      }
      return ''
    }
    return search(result)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setGeneratedLink('')
    setPaymentId(null)
    setPblResult(null)
    setCopied(false)
    setError('')
    setMessage('')

    const numericAmount = Number(amount)
    const digits = mobile.replace(/\D/g, '')
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError('Enter an amount greater than zero.')
      return
    }
    if (!email.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Enter a valid customer email address.')
      return
    }
    if (!(digits.length === 10 || (digits.length === 12 && digits.startsWith('91')))) {
      setError('Enter a valid 10-digit Indian mobile number.')
      return
    }

    setLoading(true)
    try {
      const result = await createSabbPePayment({
        customerName: customerName.trim(),
        amount: numericAmount,
        productInfo: description.trim() || 'Pay by Link',
        email: email.trim(),
        phone: digits.length === 12 ? digits.slice(2) : digits,
      })
      const id = findPaymentId(result)
      const url = findCheckoutUrl(result)
      setPaymentId(id)
      setGeneratedLink(url)
      setPblResult(result)

      if (result?.pblOrderRef || result?.linkCreated) {
        const linkStat = result.linkStatus || 'PENDING'
        const localEmail = result.localEmailDelivery
        let emailNote = ''
        if (localEmail === 'SMTP_ACCEPTED') {
          emailNote = ' Local email request accepted by SMTP server.'
        } else if (localEmail === 'FAILED') {
          emailNote = ' Local email send failed.'
        }

        if (linkStat === 'DELIVERY_FAILED') {
          setMessage(`Payment Link #${id} created (${result.pblOrderRef}). Upstream notification failed (HTTP 503). Link is ready below to copy or share manually.${emailNote}`)
        } else if (linkStat === 'SENT') {
          setMessage(`Payment Link #${id} created and dispatched via delivery channels.${emailNote}`)
        } else {
          setMessage(`Payment Link #${id} created (${result.pblOrderRef}). Delivery status: ${linkStat}.${emailNote}`)
        }
      } else if (!id && !url) {
        setMessage('The backend responded, but no payment ID or order reference was found.')
      } else {
        setMessage(`Payment request #${id} processed.`)
      }
    } catch (err) {
      setError(err?.message || 'Unable to create the SabbPe payment link.')
    } finally {
      setLoading(false)
    }
  }

  async function copyLink() {
    if (!generatedLink) return
    try {
      await navigator.clipboard.writeText(generatedLink)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Unable to copy automatically. Select and copy manually.')
    }
  }

  return (
    <div className="page-wrapper">
      <div className="form-result-layout">
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Pay by Link Generator</h3>
              <p className="card-subtitle">Generate official SabbPe hosted payment links for remote customers</p>
            </div>
            <Link2 size={24} className="text-primary" />
          </div>

          <form onSubmit={handleSubmit} className="standard-form">
            <div className="form-group">
              <label htmlFor="linkCustomer">Customer Name *</label>
              <input
                id="linkCustomer"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="Enter customer name"
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="linkEmail">Customer Email Address *</label>
              <input
                id="linkEmail"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="customer@example.com"
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="linkMobile">Customer Mobile Number (10 digits) *</label>
              <input
                id="linkMobile"
                type="tel"
                value={mobile}
                onChange={(e) => setMobile(e.target.value)}
                placeholder="9876543210"
                maxLength={10}
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="linkAmount">Payable Amount (₹) *</label>
              <input
                id="linkAmount"
                type="number"
                min="1.00"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="e.g. 100.00"
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="linkDescription">Purpose / Order Description</label>
              <input
                id="linkDescription"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="e.g. Invoice #1042 Settlement"
                maxLength={250}
                disabled={loading}
              />
            </div>

            {error && (
              <div className="alert-box alert-danger">
                <AlertCircle size={18} />
                <span>{error}</span>
              </div>
            )}

            {message && (
              <div className="alert-box alert-success">
                <CheckCircle2 size={18} />
                <span>{message}</span>
              </div>
            )}

            <button type="submit" disabled={loading} className="btn-primary btn-block">
              {loading ? 'Calling SabbPe UAT Service...' : 'Generate SabbPe Pay by Link'}
            </button>
          </form>
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <h3>Payment Link Output</h3>
              <p className="card-subtitle">Real-time hosted Easebuzz / SabbPe checkout target</p>
            </div>
          </div>

          {pblResult ? (
            <div className="qr-result-box">
              {generatedLink && (
                <div className="qr-canvas-container">
                  <QRCodeSVG value={generatedLink} size={210} includeMargin={true} level="M" />
                </div>
              )}

              <div className="qr-meta-box">
                {paymentId && (
                  <div className="qr-meta-row">
                    <span>Payment ID:</span>
                    <strong>#{paymentId}</strong>
                  </div>
                )}
                {pblResult.pblOrderRef && (
                  <div className="qr-meta-row">
                    <span>PBL Order Ref:</span>
                    <strong className="font-mono">{pblResult.pblOrderRef}</strong>
                  </div>
                )}
                {pblResult.merchantOrderRef && (
                  <div className="qr-meta-row">
                    <span>Merchant Ref:</span>
                    <span className="font-mono text-sm">{pblResult.merchantOrderRef}</span>
                  </div>
                )}
                <div className="qr-meta-row">
                  <span>Customer:</span>
                  <strong>{customerName}</strong>
                </div>
                <div className="qr-meta-row">
                  <span>Payable Amount:</span>
                  <strong className="text-primary">₹{Number(amount).toFixed(2)}</strong>
                </div>
                <div className="qr-meta-row">
                  <span>Payment Status:</span>
                  <span className={`badge ${pblResult.paymentStatus === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                    {pblResult.paymentStatus || 'PENDING'}
                  </span>
                </div>
                <div className="qr-meta-row">
                  <span>Delivery Status:</span>
                  <span className={`badge ${pblResult.linkStatus === 'SENT' ? 'status-success' : 'status-pending'}`}>
                    {pblResult.linkStatus || 'PENDING'}
                  </span>
                </div>
              </div>

              {generatedLink && (
                <div className="form-group" style={{ width: '100%' }}>
                  <label>Hosted Checkout URL</label>
                  <div className="input-with-icon">
                    <input value={generatedLink} readOnly className="font-mono text-sm" />
                  </div>
                </div>
              )}

              {pblResult.delivery && (
                <div className="qr-meta-box" style={{ width: '100%', fontSize: '0.82rem', marginTop: 8 }}>
                  <div className="qr-meta-row">
                    <span>Email Delivery:</span>
                    <strong>{pblResult.delivery.email?.status || 'N/A'}</strong>
                  </div>
                  <div className="qr-meta-row">
                    <span>WhatsApp Delivery:</span>
                    <strong>{pblResult.delivery.whatsapp?.status || 'N/A'}</strong>
                  </div>
                </div>
              )}

              <div className="action-buttons-wrap">
                {generatedLink && (
                  <>
                    <button type="button" className="btn-secondary" onClick={copyLink}>
                      <Copy size={16} />
                      <span>{copied ? 'Copied URL!' : 'Copy Link'}</span>
                    </button>
                    <a
                      href={generatedLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn-secondary"
                    >
                      <ExternalLink size={16} />
                      <span>Open Checkout</span>
                    </a>
                  </>
                )}
                {paymentId && (
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => navigate(`/receipt/${paymentId}`)}
                  >
                    <FileText size={16} />
                    <span>Track Status & Receipt</span>
                  </button>
                )}
              </div>
            </div>
          ) : (
            <div className="empty-preview-pane">
              <div className="preview-summary-list">
                <div className="preview-item">
                  <span>Target Merchant</span>
                  <strong>SBPBLmer01 (SabbPe PBL)</strong>
                </div>
                <div className="preview-item">
                  <span>Callback Notification</span>
                  <span className="font-mono text-muted">/api/sabbpe/callback</span>
                </div>
                <div className="preview-item">
                  <span>Customer Name</span>
                  <strong>{customerName || '—'}</strong>
                </div>
                <div className="preview-item">
                  <span>Amount</span>
                  <strong>{amount ? `₹${Number(amount).toFixed(2)}` : '—'}</strong>
                </div>
              </div>

              <div className="alert-box alert-info" style={{ marginTop: 20 }}>
                <CheckCircle2 size={16} />
                <span>When generated, SabbPe automatically provisions a live payment token and Easebuzz hosted link.</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
