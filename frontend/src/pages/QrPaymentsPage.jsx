import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { createSabbPeGatewayPayment, getSabbPeGatewayPaymentStatus } from '../api'
import { QrCode, Copy, ExternalLink, RefreshCw, FileText, CheckCircle2, AlertCircle } from 'lucide-react'

export default function QrPaymentsPage() {
  const navigate = useNavigate()
  const [amount, setAmount] = useState('100.00')
  const [customerName, setCustomerName] = useState('John Doe')
  const [email, setEmail] = useState('customer@example.com')
  const [phone, setPhone] = useState('9876543210')
  const [productInfo, setProductInfo] = useState('SabbPe Dynamic Checkout QR')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [gatewayData, setGatewayData] = useState(null)
  const [checkingStatus, setCheckingStatus] = useState(false)
  const [statusMessage, setStatusMessage] = useState('')
  const [copied, setCopied] = useState(false)

  const handleGenerate = async (e) => {
    e.preventDefault()
    setError('')
    setStatusMessage('')
    setGatewayData(null)

    const numAmount = Number(amount)
    if (!numAmount || numAmount <= 0) {
      setError('Please enter a valid amount greater than 0.')
      return
    }

    setLoading(true)
    try {
      const response = await createSabbPeGatewayPayment({
        amount: numAmount,
        customerName: customerName.trim() || undefined,
        customerEmail: email.trim() || undefined,
        customerPhone: phone.trim() || undefined,
        productInfo: productInfo.trim() || 'Dynamic Gateway QR'
      })

      if (!response?.paymentUrl) {
        throw new Error('No payment URL returned by SabbPe Gateway.')
      }

      setGatewayData(response)
    } catch (err) {
      setError(err.message || 'Failed to initiate SabbPe Gateway QR payment.')
    } finally {
      setLoading(false)
    }
  }

  const handleCheckStatus = async () => {
    if (!gatewayData?.paymentId) return
    setCheckingStatus(true)
    setStatusMessage('')
    try {
      const res = await getSabbPeGatewayPaymentStatus(gatewayData.paymentId)
      if (res?.status) {
        setGatewayData((prev) => ({ ...prev, status: res.status }))
        setStatusMessage(`Current status: ${res.status}`)
      }
    } catch (err) {
      setStatusMessage('Could not fetch status: ' + err.message)
    } finally {
      setCheckingStatus(false)
    }
  }

  const handleCopy = () => {
    if (!gatewayData?.paymentUrl) return
    navigator.clipboard.writeText(gatewayData.paymentUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="page-wrapper">
      <div className="form-result-layout">
        <div className="card">
          <div className="card-header">
            <div>
              <h3>Dynamic QR Payment Generator</h3>
              <p className="card-subtitle">Generate scannable UPI & Card payment QR codes powered by SabbPe</p>
            </div>
            <QrCode size={24} className="text-primary" />
          </div>

          <form onSubmit={handleGenerate} className="standard-form">
            <div className="form-group">
              <label htmlFor="gwAmount">Payable Amount (₹) *</label>
              <input
                id="gwAmount"
                type="number"
                step="0.01"
                min="1.00"
                required
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                placeholder="e.g. 500.00"
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="gwCustomerName">Customer Name (Optional)</label>
              <input
                id="gwCustomerName"
                type="text"
                value={customerName}
                onChange={(e) => setCustomerName(e.target.value)}
                placeholder="e.g. John Doe"
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="gwEmail">Email Address (Optional)</label>
              <input
                id="gwEmail"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="e.g. customer@example.com"
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="gwPhone">Mobile Number (Optional)</label>
              <input
                id="gwPhone"
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                placeholder="e.g. 9876543210"
                maxLength={10}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="gwProductInfo">Order Purpose / Note</label>
              <input
                id="gwProductInfo"
                type="text"
                value={productInfo}
                onChange={(e) => setProductInfo(e.target.value)}
                placeholder="Payment purpose"
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

            <button type="submit" disabled={loading} className="btn-primary btn-block">
              {loading ? 'Creating Dynamic QR...' : 'Generate SabbPe Dynamic QR'}
            </button>
          </form>
        </div>

        <div className="card">
          <div className="card-header">
            <div>
              <h3>Universal Scannable QR</h3>
              <p className="card-subtitle">Scan via mobile camera or any UPI application</p>
            </div>
          </div>

          {gatewayData?.paymentUrl ? (
            <div className="qr-result-box">
              <div className="qr-canvas-container">
                <QRCodeSVG value={gatewayData.paymentUrl} size={220} includeMargin={true} level="H" />
              </div>

              <div className="qr-meta-box">
                <div className="qr-meta-row">
                  <span>Payment ID:</span>
                  <strong>#{gatewayData.paymentId}</strong>
                </div>
                <div className="qr-meta-row">
                  <span>Order Ref:</span>
                  <span className="font-mono">{gatewayData.merchantOrderRef}</span>
                </div>
                <div className="qr-meta-row">
                  <span>Amount:</span>
                  <strong className="text-primary">₹{Number(gatewayData.amount).toFixed(2)}</strong>
                </div>
                <div className="qr-meta-row">
                  <span>Current Status:</span>
                  <span className={`badge ${gatewayData.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                    {gatewayData.status}
                  </span>
                </div>
              </div>

              <div className="action-buttons-wrap">
                <button type="button" className="btn-secondary" onClick={handleCopy}>
                  <Copy size={16} />
                  <span>{copied ? 'Copied URL!' : 'Copy Link'}</span>
                </button>
                <a
                  href={gatewayData.paymentUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="btn-secondary"
                >
                  <ExternalLink size={16} />
                  <span>Open Checkout</span>
                </a>
                <button
                  type="button"
                  className="btn-primary"
                  disabled={checkingStatus}
                  onClick={handleCheckStatus}
                >
                  <RefreshCw size={16} className={checkingStatus ? 'spin' : ''} />
                  <span>{checkingStatus ? 'Checking...' : 'Check Status'}</span>
                </button>
                {gatewayData.status === 'SUCCESS' && (
                  <button
                    type="button"
                    className="btn-success"
                    onClick={() => navigate(`/receipt/${gatewayData.paymentId}`)}
                  >
                    <FileText size={16} />
                    <span>View Receipt</span>
                  </button>
                )}
              </div>

              {statusMessage && (
                <div className="alert-box alert-info" style={{ marginTop: 12 }}>
                  <CheckCircle2 size={16} />
                  <span>{statusMessage}</span>
                </div>
              )}
            </div>
          ) : (
            <div className="empty-preview-pane">
              <div className="preview-summary-list">
                <div className="preview-item">
                  <span>Payment Gateway</span>
                  <strong>SabbPe Dynamic Checkout</strong>
                </div>
                <div className="preview-item">
                  <span>Merchant Order Ref</span>
                  <span className="text-muted">Auto-generated</span>
                </div>
                <div className="preview-item">
                  <span>Amount</span>
                  <strong>{amount ? `₹${Number(amount).toFixed(2)}` : '—'}</strong>
                </div>
              </div>

              <div className="alert-box alert-info" style={{ marginTop: 20 }}>
                <CheckCircle2 size={16} />
                <span>Scanning this QR will redirect to the SabbPe UAT checkout terminal with all payment channels enabled.</span>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
