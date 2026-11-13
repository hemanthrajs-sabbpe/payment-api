import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import {
  createSabbPeGatewayPayment,
  createSabbPePayment,
  initiateEasebuzzPayment,
  regenerateEasebuzzPayment,
  getPaymentById,
  getSabbPeGatewayPaymentStatus
} from '../api'
import {
  QrCode,
  Link2,
  Smartphone,
  CreditCard,
  Copy,
  ExternalLink,
  RefreshCw,
  CheckCircle2,
  AlertCircle,
  FileText
} from 'lucide-react'

export default function CreatePaymentPage() {
  const navigate = useNavigate()
  const [method, setMethod] = useState('pbl')
  const [customerName, setCustomerName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [amount, setAmount] = useState('100.00')
  const [description, setDescription] = useState('Test payment order')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')
  const [resultData, setResultData] = useState(null)
  const [copied, setCopied] = useState(false)
  const [statusLoading, setStatusLoading] = useState(false)
  const [regenerating, setRegenerating] = useState(false)

  const defaultDeeplink = `upi://mandate?amrule=MAX&tn=SABBPE%20Mandate&validityend=13102027&mode=04&mn=SABBPE%20Mandate&cu=INR&pn=SABBPE&purpose=14&fam=1.00&tid=FJJ11b9417b3f6341a4952b7b3001907ab9&pa=sabbpeuat@fblpay&am=${amount || '10.00'}&validitystart=14102026&recur=ASPRESENTED&tr=FGPd268ace036ae40dfb214c5b34a87f8c7&mc=7322&txnType=CREATE&orgid=000000&rev=N`

  async function handleSubmit(event) {
    event.preventDefault()
    setMessage('')
    setResultData(null)

    const trimmedName = customerName.trim()
    const trimmedEmail = email.trim()
    const trimmedPhone = phone.trim()
    const trimmedDescription = description.trim()
    const paymentAmount = Number(amount)

    if (!paymentAmount || paymentAmount <= 0) {
      setMessage('Please enter a valid amount greater than 0.')
      return
    }

    setLoading(true)
    try {
      if (method === 'gateway_qr') {
        const res = await createSabbPeGatewayPayment({
          amount: paymentAmount,
          customerName: trimmedName || 'Guest Customer',
          customerEmail: trimmedEmail || 'customer@example.com',
          customerPhone: trimmedPhone || '9876543210',
          productInfo: trimmedDescription || 'Normal Gateway Payment'
        })
        if (!res?.paymentUrl) throw new Error('No payment URL returned from SabbPe Gateway.')
        setResultData({
          type: 'qr',
          title: 'SabbPe Dynamic Checkout QR',
          qrValue: res.paymentUrl,
          paymentUrl: res.paymentUrl,
          paymentId: res.paymentId,
          merchantOrderRef: res.merchantOrderRef,
          status: res.status || 'PENDING',
          amount: res.amount,
          emailDispatchStatus: res.emailDispatchStatus
        })
      } else if (method === 'upi_mandate') {
        const link = defaultDeeplink
        setResultData({
          type: 'upi_mandate',
          title: 'SABBPE Mandate Deeplink',
          qrValue: link,
          paymentUrl: link,
          amount: paymentAmount,
          note: 'Scan QR with any UPI app to authorize mandate, or tap button on mobile.'
        })
      } else if (method === 'pbl') {
        if (!trimmedName) throw new Error('Customer name is required for Pay By Link.')
        if (!trimmedEmail) throw new Error('Customer email is required for Pay By Link.')
        if (!trimmedPhone) throw new Error('Customer mobile number is required for Pay By Link.')

        const digits = trimmedPhone.replace(/\D/g, '')
        const res = await createSabbPePayment({
          customerName: trimmedName,
          amount: paymentAmount,
          productInfo: trimmedDescription || 'Pay by Link',
          email: trimmedEmail,
          phone: digits.length === 12 ? digits.slice(2) : digits,
        })

        const pUrl = res?.checkoutUrl || res?.paymentUrl || res?.url || res?.link
        const linkStat = res?.linkStatus || 'PENDING'
        const deliveryMsg = linkStat === 'DELIVERY_FAILED'
          ? 'Notification delivery failed at upstream provider. Status remains PENDING.'
          : linkStat === 'SENT'
          ? 'Notification dispatched via Email / WhatsApp.'
          : `Link delivery status: ${linkStat}.`

        setResultData({
          type: 'pbl',
          title: 'SabbPe Pay by Link Dispatched',
          paymentUrl: pUrl,
          qrValue: pUrl || '',
          paymentId: res?.paymentId || res?.id,
          merchantOrderRef: res?.merchantOrderRef,
          pblOrderRef: res?.pblOrderRef,
          amount: paymentAmount,
          status: res?.paymentStatus || 'PENDING',
          linkStatus: linkStat,
          note: `Payment link created (${res?.pblOrderRef || 'Ref'}). ${deliveryMsg}`
        })
      } else if (method === 'easebuzz') {
        if (!trimmedName) throw new Error('Customer name is required for Easebuzz.')
        if (!trimmedEmail) throw new Error('Customer email is required for Easebuzz.')
        if (!trimmedPhone) throw new Error('Customer mobile number is required for Easebuzz.')

        const res = await initiateEasebuzzPayment({
          firstname: trimmedName,
          email: trimmedEmail,
          phone: trimmedPhone,
          amount: paymentAmount,
          productinfo: trimmedDescription || 'Easebuzz Payment',
        })

        if (!res?.checkoutUrl) throw new Error('Easebuzz did not return a valid checkout URL.')

        setResultData({
          type: 'qr',
          title: 'Easebuzz Dynamic Checkout QR',
          qrValue: res.checkoutUrl,
          paymentUrl: res.checkoutUrl,
          paymentId: res.paymentId,
          merchantOrderRef: res.transactionId,
          status: res.status || 'PENDING',
          amount: paymentAmount,
          emailDispatchStatus: res.emailDispatchStatus || 'NOT_REQUESTED',
          note: res.emailDispatchStatus === 'QUEUED'
            ? `Live Easebuzz checkout generated. Invoice delivery queued for ${trimmedEmail} via SMTP relay.`
            : 'Live Easebuzz checkout generated. Scan QR or click Open Checkout to pay.'
        })
      }
    } catch (error) {
      console.error('Payment initiation failed:', error)
      setMessage(error.message || 'Unable to initiate payment. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  // Auto-polling for active payment
  React.useEffect(() => {
    if (!resultData?.paymentId || resultData?.status === 'SUCCESS') return

    const interval = setInterval(async () => {
      try {
        const res = await getSabbPeGatewayPaymentStatus(resultData.paymentId)
        if (res?.status === 'SUCCESS') {
          setResultData((prev) => ({ ...prev, status: 'SUCCESS' }))
          clearInterval(interval)
        }
      } catch (e) {
        // quiet poll
      }
    }, 2500)

    return () => clearInterval(interval)
  }, [resultData?.paymentId, resultData?.status])

  const handleCheckStatus = async () => {
    if (!resultData?.paymentId) return
    setStatusLoading(true)
    try {
      // Use general getPaymentById to accurately fetch status for any provider (Easebuzz, SabbPe, etc.)
      const res = await getPaymentById(resultData.paymentId)
      if (res?.status) {
        setResultData((prev) => ({ ...prev, status: res.status }))
      }
    } catch (err) {
      alert('Could not fetch status: ' + err.message)
    } finally {
      setStatusLoading(false)
    }
  }

  const handleRegenerate = async () => {
    if (!resultData?.paymentId) return
    setRegenerating(true)
    setMessage('')
    try {
      const trimmedName = customerName.trim()
      const trimmedEmail = email.trim()
      const trimmedPhone = phone.trim()
      const paymentAmount = Number(amount)

      const res = await regenerateEasebuzzPayment(resultData.paymentId, {
        firstname: trimmedName || 'Customer',
        email: trimmedEmail,
        phone: trimmedPhone,
        amount: paymentAmount,
        productinfo: description.trim() || 'Easebuzz Payment',
      })

      if (!res?.checkoutUrl) throw new Error('Regeneration did not return a valid checkout URL.')

      setResultData({
        type: 'qr',
        title: 'Easebuzz Dynamic Checkout QR (Replacement)',
        qrValue: res.checkoutUrl,
        paymentUrl: res.checkoutUrl,
        paymentId: res.paymentId,
        merchantOrderRef: res.transactionId,
        status: res.status || 'PENDING',
        amount: paymentAmount,
        emailDispatchStatus: res.emailDispatchStatus || 'NOT_REQUESTED',
        note: `Fresh checkout session #${res.paymentId} generated after verified previous attempt failure.`
      })
      setMessage(`Fresh checkout session created successfully (#${res.paymentId}).`)
    } catch (err) {
      setMessage(err.message || 'Payment regeneration failed.')
    } finally {
      setRegenerating(false)
    }
  }

  const copyUrl = () => {
    if (!resultData?.paymentUrl) return
    navigator.clipboard.writeText(resultData.paymentUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="page-wrapper">
      <div className="form-result-layout">
        {/* Left Column: Configuration Form */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Payment Configuration</h3>
              <p className="card-subtitle">Select checkout provider and customer specifications</p>
            </div>
          </div>

          <div className="method-selector-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))' }}>
            <div
              className={`method-tile ${method === 'pbl' ? 'method-selected' : ''}`}
              onClick={() => { setMethod('pbl'); setResultData(null) }}
            >
              <Link2 size={24} className="method-tile-icon" style={{ color: '#2563eb' }} />
              <strong>SabbPe PBL</strong>
              <small>Payment Link</small>
            </div>

            <div
              className={`method-tile ${method === 'easebuzz' ? 'method-selected' : ''}`}
              onClick={() => { setMethod('easebuzz'); setResultData(null) }}
            >
              <CreditCard size={24} className="method-tile-icon" />
              <strong>Easebuzz</strong>
              <small>Sandbox Checkout</small>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="standard-form">
            <div className="form-group">
              <label htmlFor="paymentAmount">Amount (₹) *</label>
              <input
                id="paymentAmount"
                type="number"
                min="1.00"
                step="0.01"
                placeholder="e.g. 100.00"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="customerName">Customer Name {method === 'pbl' || method === 'easebuzz' ? '*' : '(Optional)'}</label>
              <input
                id="customerName"
                type="text"
                placeholder="e.g. John Doe"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                required={method === 'pbl' || method === 'easebuzz'}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="customerEmail">Email Address {method === 'pbl' || method === 'easebuzz' ? '*' : '(Optional)'}</label>
              <input
                id="customerEmail"
                type="email"
                placeholder="e.g. customer@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required={method === 'pbl' || method === 'easebuzz'}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="customerPhone">Mobile Number {method === 'pbl' || method === 'easebuzz' ? '*' : '(Optional)'}</label>
              <input
                id="customerPhone"
                type="tel"
                placeholder="e.g. 9876543210"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                maxLength={10}
                required={method === 'pbl' || method === 'easebuzz'}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="paymentDescription">Description / Order Purpose</label>
              <input
                id="paymentDescription"
                type="text"
                placeholder="e.g. Service settlement"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                maxLength={250}
                disabled={loading}
              />
            </div>

            {message && (
              <div className="alert-box alert-danger">
                <AlertCircle size={18} />
                <span>{message}</span>
              </div>
            )}

            <button type="submit" disabled={loading} className="btn-primary btn-block">
              {loading ? 'Processing via SabbPe...' : (
                method === 'pbl' ? 'Generate Pay by Link' :
                'Proceed to Easebuzz Checkout'
              )}
            </button>
          </form>
        </div>

        {/* Right Column: Dynamic Output Card */}
        <div className="card">
          <div className="card-header">
            <div>
              <h3>{resultData?.title || 'Payment Preview'}</h3>
              <p className="card-subtitle">{resultData ? 'Dynamic checkout generated' : 'Parameters will appear here'}</p>
            </div>
          </div>

          {resultData ? (
            <div className="qr-result-box">
              {resultData.qrValue ? (
                <div className="qr-canvas-container">
                  <QRCodeSVG value={resultData.qrValue} size={220} includeMargin={true} level="M" />
                </div>
              ) : null}

              <div className="qr-meta-box">
                {resultData.paymentId && (
                  <div className="qr-meta-row">
                    <span>Payment ID:</span>
                    <strong>#{resultData.paymentId}</strong>
                  </div>
                )}
                {resultData.pblOrderRef && (
                  <div className="qr-meta-row">
                    <span>PBL Order Ref:</span>
                    <strong className="font-mono">{resultData.pblOrderRef}</strong>
                  </div>
                )}
                {resultData.merchantOrderRef && (
                  <div className="qr-meta-row">
                    <span>Order Ref:</span>
                    <span className="font-mono">{resultData.merchantOrderRef}</span>
                  </div>
                )}
                <div className="qr-meta-row">
                  <span>Payable Amount:</span>
                  <strong className="text-primary">₹{Number(resultData.amount || amount).toFixed(2)}</strong>
                </div>
                {resultData.status && (
                  <div className="qr-meta-row">
                    <span>Backend Status:</span>
                    <span className={`badge ${resultData.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                      {resultData.status}
                    </span>
                  </div>
                )}
                {resultData.linkStatus && (
                  <div className="qr-meta-row">
                    <span>Delivery Status:</span>
                    <span className={`badge ${resultData.linkStatus === 'SENT' ? 'status-success' : 'status-pending'}`}>
                      {resultData.linkStatus}
                    </span>
                  </div>
                )}
                {resultData.emailDispatchStatus && (
                  <div className="qr-meta-row">
                    <span>Email Dispatch:</span>
                    <span className={`badge ${resultData.emailDispatchStatus === 'SMTP_ACCEPTED' ? 'status-success' : 'status-pending'}`}>
                      {resultData.emailDispatchStatus}
                    </span>
                  </div>
                )}
              </div>

              <div className="action-buttons-wrap">
                {resultData.paymentUrl && (
                  <>
                    <button type="button" className="btn-secondary" onClick={copyUrl}>
                      <Copy size={16} />
                      <span>{copied ? 'Copied!' : 'Copy Link'}</span>
                    </button>
                    <a
                      href={resultData.paymentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn-secondary"
                    >
                      <ExternalLink size={16} />
                      <span>Open Checkout</span>
                    </a>
                  </>
                )}

                {resultData.paymentId && (
                  <button
                    type="button"
                    className="btn-primary"
                    disabled={statusLoading}
                    onClick={handleCheckStatus}
                  >
                    <RefreshCw size={16} className={statusLoading ? 'spin' : ''} />
                    <span>{statusLoading ? 'Querying...' : 'Verify Status'}</span>
                  </button>
                )}

                {resultData.paymentId && (
                  <button
                    type="button"
                    className={resultData.status === 'SUCCESS' ? 'btn-success' : 'btn-secondary'}
                    onClick={() => navigate(`/receipt/${resultData.paymentId}`)}
                  >
                    <FileText size={16} />
                    <span>View Receipt</span>
                  </button>
                )}

                {resultData.paymentId && method === 'easebuzz' && (resultData.status === 'FAILED' || resultData.status === 'TIMEOUT' || resultData.status === 'CANCELLED') && (
                  <button
                    type="button"
                    className="btn-danger"
                    disabled={regenerating}
                    onClick={handleRegenerate}
                    style={{ backgroundColor: '#ef4444', color: '#ffffff', borderColor: '#dc2626' }}
                  >
                    <RefreshCw size={16} className={regenerating ? 'spin' : ''} />
                    <span>{regenerating ? 'Regenerating...' : 'Regenerate Checkout'}</span>
                  </button>
                )}
              </div>

              {resultData.note && <p className="qr-note-text">{resultData.note}</p>}
            </div>
          ) : (
            <div className="empty-preview-pane">
              <div className="preview-summary-list">
                <div className="preview-item">
                  <span>Selected Channel</span>
                  <strong>
                    {method === 'pbl' ? 'Pay by Link' : 'Easebuzz Checkout'}
                  </strong>
                </div>
                <div className="preview-item">
                  <span>Customer Name</span>
                  <strong>{customerName.trim() || '—'}</strong>
                </div>
                <div className="preview-item">
                  <span>Amount</span>
                  <strong>{amount ? `₹${Number(amount).toFixed(2)}` : '—'}</strong>
                </div>
                <div className="preview-item">
                  <span>Ready State</span>
                  <span className="badge status-pending">Awaiting Submission</span>
                </div>
              </div>
              <div className="alert-box alert-info" style={{ marginTop: 20 }}>
                <CheckCircle2 size={16} />
                <span>Submitting creates an authentic order reference in UAT MariaDB.</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
