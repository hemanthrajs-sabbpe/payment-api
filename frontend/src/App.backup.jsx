import { useEffect, useState } from 'react'

import './App.css'

import './index.css'
import { getPayments, createSabbPePayment } from './api'



const API_BASE_URL = 'http://localhost:8080'



function getAuthHeaders() {

  const headers = {

    Accept: 'application/json',

  }



  const tokenKeys = [

    'jwtToken',

    'token',

    'jwt',

    'accessToken',

  ]



  for (const key of tokenKeys) {

    const token = localStorage.getItem(key)



    if (token) {

      headers.Authorization = `Bearer ${token}`

      break

    }

  }



  return headers

}



function PaymentResult() {

  const [payment, setPayment] = useState(null)

  const [loading, setLoading] = useState(true)

  const [error, setError] = useState('')



  useEffect(() => {

    let cancelled = false



    async function loadPayment() {

      setLoading(true)

      setError('')



      const params = new URLSearchParams(

        window.location.search

      )



      const pathMatch = window.location.pathname.match(

        /^\/payment-result\/(\d+)\/?$/

      )



      const paymentId =

        params.get('paymentId') || pathMatch?.[1]



      if (!paymentId || !/^\d+$/.test(paymentId)) {

        setError('A valid payment ID was not provided.')

        setLoading(false)

        return

      }



      try {

        const response = await fetch(

          `${API_BASE_URL}/api/payments/${paymentId}`,

          {

            method: 'GET',

            headers: getAuthHeaders(),

          }

        )



        if (!response.ok) {

          if (response.status === 401) {

            throw new Error(

              'Your session has expired. Please log in again.'

            )

          }



          if (response.status === 403) {

            throw new Error('Access denied.')

          }



          if (response.status === 404) {

            throw new Error('Payment record not found.')

          }



          throw new Error(

            `Unable to load payment. HTTP ${response.status}.`

          )

        }



        const data = await response.json()



        if (!cancelled) {

          setPayment(data)

        }

      } catch (err) {

        if (!cancelled) {

          setError(

            err.message || 'Unable to load payment details.'

          )

        }

      } finally {

        if (!cancelled) {

          setLoading(false)

        }

      }

    }



    loadPayment()



    return () => {

      cancelled = true

    }

  }, [])



  if (loading) {

    return (

      <main className="result-page">

        <section className="result-card">

          <div className="result-spinner" />

          <div className="result-message">

            <h1>Checking payment</h1>

            <p>Retrieving your payment details...</p>

          </div>

        </section>

      </main>

    )

  }



  if (error) {

    return (

      <main className="result-page">

        <section className="result-card">

          <div className="result-message">

            <div className="result-icon result-icon-error">

              !

            </div>



            <h1>Unable to load payment</h1>

            <p>{error}</p>

          </div>



          <button

            className="secondary-button"

            type="button"

            onClick={() => window.location.reload()}

          >

            Try Again

          </button>



          <button

            className="secondary-button"

            type="button"

            onClick={() => {

              window.location.href = '/'

            }}

          >

            Back to Dashboard

          </button>

        </section>

      </main>

    )

  }



  const status = String(

    payment?.status || 'UNKNOWN'

  ).toUpperCase()



  const isSuccess = status === 'SUCCESS'



  const isFailure = [

    'FAILED',

    'FAILURE',

    'INITIATION_FAILED',

    'TOKEN_GENERATION_FAILED',

  ].includes(status)



  const iconClass = isSuccess

    ? 'result-icon-success'

    : isFailure

      ? 'result-icon-error'

      : 'result-icon-pending'



  return (

    <main className="result-page">

      <section className="result-card">

        <div className="result-message">

          <div className={`result-icon ${iconClass}`}>

            {isSuccess ? '✓' : isFailure ? '!' : '…'}

          </div>



          <h1>

            {isSuccess

              ? 'Payment Successful'

              : isFailure

                ? 'Payment Failed'

                : 'Payment Pending'}

          </h1>



          <p>

            {isSuccess

              ? 'Your payment is marked successful in the database.'

              : isFailure

                ? 'The payment could not be completed successfully.'

                : 'The final payment status has not yet been confirmed.'}

          </p>



          <div className="result-amount">

            {payment.amount != null

              ? `₹${Number(payment.amount).toFixed(2)}`

              : '—'}

          </div>

        </div>



        <div className="result-details">

          <h2>Payment Details</h2>



          <DetailRow

            label="Payment ID"

            value={payment.id}

          />



          <DetailRow

            label="Customer"

            value={payment.customerName || '—'}

          />



          <DetailRow

            label="Status"

            value={status}

          />



          {payment.merchantOrderRef && (

            <DetailRow

              label="Order Reference"

              value={payment.merchantOrderRef}

            />

          )}



          {payment.transactionId && (

            <DetailRow

              label="Transaction ID"

              value={payment.transactionId}

            />

          )}

        </div>



        <p className="result-note">

          This page displays the payment status returned by

          your backend.

        </p>



        <button

          className="secondary-button result-home-button"

          type="button"

          onClick={() => window.location.reload()}

        >

          Refresh Payment Status

        </button>



        <button

          className="secondary-button result-home-button"

          type="button"

          onClick={() => {

            window.location.href = '/'

          }}

        >

          Back to Dashboard

        </button>

      </section>

    </main>

  )

}



function DetailRow({ label, value }) {

  return (

    <div className="result-detail-row">

      <span>{label}</span>

      <strong>{value}</strong>

    </div>

  )

}



const navigation = [

  {

    section: 'MAIN MENU',

    items: [

      { id: 'dashboard', icon: '▦', label: 'Dashboard' },

      { id: 'create', icon: '+', label: 'Create Payment' },

      { id: 'paylink', icon: '↗', label: 'Pay by Link' },

      { id: 'history', icon: '▤', label: 'Payment History' },

    ],

  },

  {

    section: 'PAYMENT TOOLS',

    items: [

      { id: 'split', icon: '⇄', label: 'Split Payment' },

      { id: 'integrations', icon: '⌘', label: 'Integrations' },

    ],

  },

]



function Sidebar({ activePage, onNavigate }) {

  return (

    <aside className="sidebar">

      <div className="brand">

        <div className="brand-icon">₹</div>



        <div>

          <h2>Payment API</h2>

          <span>Payment Management</span>

        </div>

      </div>



      {navigation.map((group) => (

        <div key={group.section}>

          <p className="nav-label">{group.section}</p>



          <nav>

            {group.items.map((item) => (

              <button

                key={item.id}

                type="button"

                className={`nav-item ${

                  activePage === item.id ? 'active' : ''

                }`}

                onClick={() => onNavigate(item.id)}

              >

                <span aria-hidden="true">{item.icon}</span>

                {item.label}

              </button>

            ))}

          </nav>

        </div>

      ))}



      <div className="sidebar-bottom">

        <div className="environment">

          <span className="status-dot" />



          <div>

            <strong>Development</strong>

            <small>Local environment</small>

          </div>

        </div>



        <p>Payment API · Development Dashboard</p>

      </div>

    </aside>

  )

}



function Topbar({ title }) {

  return (

    <header className="topbar">

      <div>

        <p className="eyebrow">PAYMENT MANAGEMENT</p>

        <h1>{title}</h1>

      </div>



      <div className="profile">

        <div>

          <strong>Payment Admin</strong>

          <small>Merchant Dashboard</small>

        </div>



        <div className="avatar">PA</div>

      </div>

    </header>

  )

}



function Dashboard({ onNavigate }) {
  const [payments, setPayments] = useState([])
  const [statsLoading, setStatsLoading] = useState(true)
  const [statsError, setStatsError] = useState('')

  useEffect(() => {
    let cancelled = false

    async function loadDashboardPayments() {
      setStatsLoading(true)
      setStatsError('')
      try {
        const data = await getPayments()
        if (!Array.isArray(data)) throw new Error('Unexpected response from the payments API.')
        if (!cancelled) setPayments(data)
      } catch (error) {
        if (!cancelled) setStatsError(error.message || 'Unable to load payment statistics.')
      } finally {
        if (!cancelled) setStatsLoading(false)
      }
    }

    loadDashboardPayments()
    return () => { cancelled = true }
  }, [])

  const normalizedStatuses = payments.map((payment) => String(payment.status || '').toUpperCase())
  const totalPayments = payments.length
  const successfulPayments = normalizedStatuses.filter((status) => status === 'SUCCESS').length
  const pendingPayments = normalizedStatuses.filter((status) => status === 'PENDING').length
  const failedPayments = normalizedStatuses.filter((status) =>
    ['FAILED', 'FAILURE', 'INITIATION_FAILED', 'TOKEN_GENERATION_FAILED'].includes(status)
  ).length

  const statValue = (value) => statsLoading ? '…' : statsError ? '—' : String(value)
  const statDescription = statsLoading
    ? 'Loading live data...'
    : statsError
      ? 'Unable to load live data'
      : 'Live data from payment API'

  return (

    <>

      <section className="welcome">

        <div>

          <p className="welcome-tag">PAYMENT WORKSPACE</p>



          <h2>Welcome to Payment API</h2>



          <p>

            Manage payments, generate payment links, and

            track transactions in one place.

          </p>

        </div>



        <button

          className="primary-button"

          type="button"

          onClick={() => onNavigate('create')}

        >

          + Create Payment

        </button>

      </section>



      {statsError && (
        <div className="notice" role="alert">
          <strong>Dashboard data unavailable</strong>
          <p>{statsError}</p>
          <p>Make sure the backend is running and the current JWT token is valid.</p>
        </div>
      )}

      <section className="stats-grid">

        <StatCard

          icon="₹"

          color="blue"

          title="Total Payments"

          value={statValue(totalPayments)}

          description={statDescription}

        />



        <StatCard

          icon="✓"

          color="green"

          title="Successful Payments"

          value={statValue(successfulPayments)}

          description={statDescription}

        />



        <StatCard

          icon="◷"

          color="orange"

          title="Pending Payments"

          value={statValue(pendingPayments)}

          description={statDescription}

        />



        <StatCard

          icon="!"

          color="red"

          title="Failed Payments"

          value={statValue(failedPayments)}

          description={statDescription}

        />

      </section>



      <section className="content-grid">

        <div className="panel">

          <div className="panel-header">

            <div>

              <h2>Quick Actions</h2>

              <p>Choose what you want to do next.</p>

            </div>

          </div>



          <div className="quick-actions">

            <button

              className="action-card"

              type="button"

              onClick={() => onNavigate('create')}

            >

              <span className="action-icon">₹</span>

              <strong>Create Payment</strong>

              <small>Start a payment transaction</small>

              <span className="action-arrow">→</span>

            </button>



            <button

              className="action-card"

              type="button"

              onClick={() => onNavigate('paylink')}

            >

              <span className="action-icon">↗</span>

              <strong>Generate Pay Link</strong>

              <small>Create a payment link</small>

              <span className="action-arrow">→</span>

            </button>



            <button

              className="action-card"

              type="button"

              onClick={() => onNavigate('history')}

            >

              <span className="action-icon">▤</span>

              <strong>Payment History</strong>

              <small>Review transaction records</small>

              <span className="action-arrow">→</span>

            </button>



            <button

              className="action-card"

              type="button"

              onClick={() => onNavigate('split')}

            >

              <span className="action-icon">⇄</span>

              <strong>Split Payment</strong>

              <small>Manage payment allocations</small>

              <span className="action-arrow">→</span>

            </button>

          </div>

        </div>



        <div className="panel">

          <div className="panel-header">

            <div>

              <h2>Payment Integrations</h2>

              <p>Existing payment providers</p>

            </div>

          </div>



          <IntegrationRow

            icon="E"

            name="Easebuzz"

            description="Existing payment integration"

          />



          <IntegrationRow

            icon="S"

            name="SabbPe"

            description="Pay by Link integration"

          />



          <div className="notice">

            <strong>Integration status</strong>

            <p>

              Provider connectivity and API status have not

              been checked by this dashboard.

            </p>

          </div>



          <button

            className="secondary-button"

            type="button"

            onClick={() => onNavigate('integrations')}

          >

            View Integrations

          </button>

        </div>

      </section>

    </>

  )

}



function StatCard({ icon, color, title, value, description }) {

  return (

    <div className="stat-card">

      <div className="stat-heading">

        <span>{title}</span>

        <span className={`stat-icon ${color}`}>

          {icon}

        </span>

      </div>



      <h2>{value}</h2>

      <p>{description}</p>

    </div>

  )

}



function IntegrationRow({ icon, name, description }) {

  return (

    <div className="integration-row">

      <div className="integration-icon">{icon}</div>



      <div>

        <strong>{name}</strong>

        <small>{description}</small>

      </div>



      <span className="mini-status">Configured</span>

    </div>

  )

}



function CreatePayment({ onNavigate }) {
  const [customerName, setCustomerName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState('')

  async function handleSubmit(event) {
    event.preventDefault()
    setMessage('')

    const trimmedName = customerName.trim()
    const trimmedEmail = email.trim()
    const trimmedPhone = phone.trim()
    const paymentAmount = Number(amount)

    if (!trimmedName) {
      setMessage('Please enter the customer name.')
      return
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
      setMessage('Please enter a valid email address.')
      return
    }

    if (!/^[6-9]\d{9}$/.test(trimmedPhone)) {
      setMessage('Enter a valid 10-digit Indian mobile number.')
      return
    }

    if (!Number.isFinite(paymentAmount) || paymentAmount <= 0) {
      setMessage('Enter an amount greater than zero.')
      return
    }

    // The backend payment endpoint currently accepts only customerName and amount.
    // Do not submit until the request contract supports the additional fields.
    setMessage(
      'Details validated. Payment submission is not connected yet; your existing backend and Easebuzz integration have not been changed.'
    )
  }

  return (
    <div className="content-grid">
      <section className="panel create-panel">
        <div className="panel-header">
          <div>
            <h2>Create Payment</h2>
            <p>Enter the customer and payment details.</p>
          </div>
          <span className="stat-icon blue">₹</span>
        </div>

        <form className="payment-form" onSubmit={handleSubmit}>
          <label htmlFor="customerName">Customer Name</label>
          <input
            id="customerName"
            type="text"
            placeholder="Enter customer name"
            value={customerName}
            onChange={(event) => setCustomerName(event.target.value)}
            autoComplete="name"
            maxLength={100}
            required
          />

          <label htmlFor="customerEmail">Email Address</label>
          <input
            id="customerEmail"
            type="email"
            placeholder="customer@example.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
          />

          <label htmlFor="customerPhone">Phone Number</label>
          <input
            id="customerPhone"
            type="tel"
            placeholder="10-digit mobile number"
            value={phone}
            onChange={(event) => {
              const digits = event.target.value.replace(/\D/g, '').slice(0, 10)
              setPhone(digits)
            }}
            autoComplete="tel-national"
            inputMode="numeric"
            maxLength={10}
            pattern="[6-9][0-9]{9}"
            title="Enter a valid 10-digit Indian mobile number."
            required
          />

          <label htmlFor="paymentAmount">Amount (₹)</label>
          <input
            id="paymentAmount"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="e.g. 100.00"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />

          <label htmlFor="paymentDescription">Payment Description</label>
          <input
            id="paymentDescription"
            type="text"
            placeholder="e.g. Demo product"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            maxLength={250}
          />

          <button
            className="primary-button"
            type="submit"
            disabled={loading}
          >
            {loading ? 'Processing...' : 'Proceed to Payment →'}
          </button>

          {message && (
            <div className="api-error" role="status">
              <p>{message}</p>
            </div>
          )}

          <p className="demo-note">
            Payment processing will be enabled after the form is connected to
            the existing backend and Easebuzz checkout.
          </p>
        </form>
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Payment Information</h2>
            <p>Review the details before proceeding.</p>
          </div>
        </div>

        <div className="payment-details">
          <div>
            <span>Customer</span>
            <strong>{customerName.trim() || '—'}</strong>
          </div>
          <div>
            <span>Email</span>
            <strong>{email.trim() || '—'}</strong>
          </div>
          <div>
            <span>Phone</span>
            <strong>{phone || '—'}</strong>
          </div>
          <div>
            <span>Amount</span>
            <strong>
              {amount && Number(amount) > 0
                ? `₹${Number(amount).toFixed(2)}`
                : '—'}
            </strong>
          </div>
          <div>
            <span>Description</span>
            <strong>{description.trim() || '—'}</strong>
          </div>
        </div>

        <div className="notice">
          <strong>Payment security</strong>
          <p>
            Payments must be processed through the backend and payment
            provider. Do not enter card details here.
          </p>
        </div>

        <button
          className="secondary-button"
          type="button"
          onClick={() => onNavigate('paylink')}
        >
          Open Pay by Link
        </button>
      </section>
    </div>
  )
}

function PayByLink() {
  const [customerName, setCustomerName] = useState('')
  const [email, setEmail] = useState('')
  const [mobile, setMobile] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [paymentDetails, setPaymentDetails] = useState(null)
  const [generatedLink, setGeneratedLink] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setMessage('')
    setError('')
    setPaymentDetails(null)
    setGeneratedLink('')
    setCopied(false)

    const numericAmount = Number(amount)
    if (!customerName.trim()) {
      setError('Please enter the customer name.')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Please enter a valid email address.')
      return
    }
    if (!/^[6-9]\d{9}$/.test(mobile.trim())) {
      setError('Enter a valid 10-digit Indian mobile number.')
      return
    }
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError('Enter an amount greater than zero.')
      return
    }

    setLoading(true)
    try {
      const result = await createSabbPePayment({
        customerName: customerName.trim(),
        amount: numericAmount,
        productInfo: description.trim() || 'SabbPe Payment',
        email: email.trim(),
        phone: mobile.trim(),
      })

      if (!result || typeof result !== 'object') {
        throw new Error('Unexpected response from the SabbPe payment API.')
      }

      setPaymentDetails(result)
      const possibleLink = result.checkoutUrl || result.paymentUrl || result.payment_url
      if (typeof possibleLink === 'string' && /^https?:\/\//i.test(possibleLink)) {
        setGeneratedLink(possibleLink)
      }

      const deliveryStatus = result.linkStatus || result.link_status || 'Not provided'
      setMessage(
        `SabbPe request created. Payment ID: ${result.paymentId ?? 'not returned'}. ` +
        `Link delivery status: ${deliveryStatus}. Check the delivery details below and your email/WhatsApp.`
      )
    } catch (err) {
      setError(err?.message || 'Unable to generate the SabbPe payment link.')
    } finally {
      setLoading(false)
    }
  }

  async function copyLink() {
    if (!generatedLink) return
    try {
      await navigator.clipboard.writeText(generatedLink)
      setCopied(true)
      setError('')
    } catch {
      setError('Unable to copy automatically. Select and copy the link manually.')
    }
  }

  return (
    <div className="content-grid">
      <section className="panel create-panel">
        <div className="panel-header">
          <div>
            <h2>Generate Payment Link</h2>
            <p>Create a payment request for your customer through SabbPe.</p>
          </div>
        </div>

        <form className="payment-form" onSubmit={handleSubmit}>
          <label htmlFor="linkCustomer">Customer Name</label>
          <input
            id="linkCustomer"
            value={customerName}
            onChange={(event) => setCustomerName(event.target.value)}
            placeholder="Enter customer name"
            required
          />

          <label htmlFor="linkEmail">Email Address</label>
          <input
            id="linkEmail"
            type="email"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            placeholder="customer@example.com"
            required
          />

          <label htmlFor="linkMobile">Mobile Number</label>
          <input
            id="linkMobile"
            type="tel"
            value={mobile}
            onChange={(event) => setMobile(event.target.value)}
            placeholder="Enter 10-digit mobile number"
            pattern="[6-9][0-9]{9}"
            maxLength={10}
            required
          />

          <label htmlFor="linkAmount">Amount (₹)</label>
          <input
            id="linkAmount"
            type="number"
            min="0.01"
            step="0.01"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            placeholder="Enter amount"
            required
          />

          <label htmlFor="linkDescription">Description</label>
          <input
            id="linkDescription"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            placeholder="Payment description"
          />

          <button className="primary-button" type="submit" disabled={loading}>
            {loading ? 'Generating...' : 'Generate Payment Link'}
          </button>
        </form>

        {error && (
          <div className="api-error" role="alert">
            <p>{error}</p>
          </div>
        )}
        {message && (
          <div className="notice" role="status">
            <p>{message}</p>
          </div>
        )}
      </section>

      <section className="panel">
        <div className="panel-header">
          <div>
            <h2>Payment Link Details</h2>
            <p>Generated link and delivery information.</p>
          </div>
        </div>

        <div className="notice">
          <strong>Payment link status</strong>
          <p>
            {paymentDetails
              ? 'SabbPe returned a response. Review the details below.'
              : 'No payment request generated yet.'}
          </p>
        </div>

        <div className="payment-details">
          <div>
            <span>Payment ID</span>
            <strong>{paymentDetails?.paymentId ?? '—'}</strong>
          </div>
          <div>
            <span>Customer</span>
            <strong>{customerName || '—'}</strong>
          </div>
          <div>
            <span>Amount</span>
            <strong>{amount ? `₹${Number(amount).toFixed(2)}` : '—'}</strong>
          </div>
          <div>
            <span>Merchant Order Reference</span>
            <strong>{paymentDetails?.merchantOrderRef ?? '—'}</strong>
          </div>
          <div>
            <span>SabbPe Payment Link Reference</span>
            <strong>{paymentDetails?.pblOrderRef ?? '—'}</strong>
          </div>
          <div>
            <span>Payment Status</span>
            <strong>{paymentDetails?.paymentStatus ?? 'NOT GENERATED'}</strong>
          </div>
          <div>
            <span>Link Delivery Status</span>
            <strong>{paymentDetails?.linkStatus ?? '—'}</strong>
          </div>
          <div>
            <span>Delivery Details</span>
            <strong>
              {paymentDetails?.delivery
                ? JSON.stringify(paymentDetails.delivery)
                : '—'}
            </strong>
          </div>
        </div>

        {generatedLink && (
          <>
            <label htmlFor="generatedLink">Payment URL</label>
            <input
              id="generatedLink"
              className="generated-link-input"
              value={generatedLink}
              readOnly
            />
            <button className="secondary-button" type="button" onClick={copyLink}>
              {copied ? 'Copied!' : 'Copy Payment Link'}
            </button>
            <a href={generatedLink} target="_blank" rel="noopener noreferrer">
              Open Payment Link
            </a>
          </>
        )}

        <p className="demo-note">
          The payment remains pending until the backend verifies the actual payment result.
          SabbPe handles email and WhatsApp delivery according to its API response.
        </p>
      </section>
    </div>
  )
}

function PaymentHistory() {
  const [payments, setPayments] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadPayments() {
    setLoading(true)
    setError('')

    try {
      const data = await getPayments()

      if (!Array.isArray(data)) {
        throw new Error('Unexpected response from the payments API.')
      }

      setPayments(data)
    } catch (err) {
      setError(err.message || 'Unable to load payment history.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadPayments()
  }, [])

  return (
    <section className="panel">
      <div className="panel-header">
        <div>
          <h2>Payment History</h2>
          <p>Review your payment transactions.</p>
        </div>
        <button
          className="secondary-button"
          type="button"
          onClick={loadPayments}
          disabled={loading}
        >
          {loading ? 'Loading...' : 'Refresh'}
        </button>
      </div>

      {error && (
        <div className="notice" role="alert">
          <strong>Unable to load payments</strong>
          <p>{error}</p>
          <p>Check that the backend is running and that you have a valid JWT token.</p>
        </div>
      )}

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>PAYMENT ID</th>
              <th>CUSTOMER</th>
              <th>AMOUNT</th>
              <th>ORDER REFERENCE</th>
              <th>STATUS</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan="5" className="table-message">Loading payment history...</td>
              </tr>
            ) : error ? (
              <tr>
                <td colSpan="5" className="table-message">Payment records could not be loaded.</td>
              </tr>
            ) : payments.length === 0 ? (
              <tr>
                <td colSpan="5" className="table-message">No payment records found.</td>
              </tr>
            ) : (
              payments.map((payment) => (
                <tr key={payment.id}>
                  <td>{payment.id ?? '—'}</td>
                  <td>{payment.customerName || '—'}</td>
                  <td>{payment.amount != null ? `₹${Number(payment.amount).toFixed(2)}` : '—'}</td>
                  <td>{payment.transactionId || '—'}</td>
                  <td>
                    <span className={`status-badge status-${String(payment.status || 'UNKNOWN').toLowerCase()}`}>
                      {payment.status || 'UNKNOWN'}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}


function SplitPayment() {

  return (

    <>

      <section className="welcome">

        <div>

          <p className="welcome-tag">PAYMENT TOOLS</p>



          <h2>Split Payment</h2>



          <p>

            Manage restaurant group bills and percentage-based

            payment allocations.

          </p>

        </div>

      </section>



      <section className="content-grid">

        <div className="panel">

          <div className="panel-header">

            <div>

              <h2>Restaurant Group Bill</h2>

              <p>One bill shared between multiple customers.</p>

            </div>

          </div>



          <div className="notice">

            <strong>Multiple customer payments</strong>

            <p>

              Create an original restaurant bill, calculate each

              person's share, and track individual payments.

              The final bill becomes paid only after every share

              is verified.

            </p>

          </div>

        </div>



        <div className="panel">

          <div className="panel-header">

            <div>

              <h2>Percentage-Based Split</h2>

              <p>Allocate one payment among multiple recipients.</p>

            </div>

          </div>



          <div className="notice">

            <strong>Seller · Agent · Platform</strong>

            <p>

              Configure allocation percentages and calculate

              each recipient's share. Actual settlement requires

              a supported provider payout or split-settlement API.

            </p>

          </div>

        </div>

      </section>

    </>

  )

}



function Integrations() {

  return (

    <section className="panel">

      <div className="panel-header">

        <div>

          <h2>Payment Integrations</h2>

          <p>Existing payment-provider integrations.</p>

        </div>

      </div>



      <IntegrationRow

        icon="E"

        name="Easebuzz"

        description="Existing integration"

      />



      <IntegrationRow

        icon="S"

        name="SabbPe"

        description="Existing integration and Pay by Link"

      />



      <div className="notice">

        <strong>Provider connectivity</strong>

        <p>

          This page describes the configured integrations.

          It does not perform a live connectivity test.

        </p>

      </div>

    </section>

  )

}



const pageTitles = {

  dashboard: 'Dashboard',

  create: 'Create Payment',

  paylink: 'Pay by Link',

  history: 'Payment History',

  split: 'Split Payment',

  integrations: 'Integrations',

}



export default function App() {

  const path = window.location.pathname



  if (

    path === '/payment-result' ||

    /^\/payment-result\/\d+\/?$/.test(path)

  ) {

    return <PaymentResult />

  }



  return <PaymentDashboard />

}



function PaymentDashboard() {

  const [activePage, setActivePage] = useState('dashboard')



  function navigate(page) {

    setActivePage(page)

    window.scrollTo({ top: 0, behavior: 'smooth' })

  }



  function renderPage() {

    switch (activePage) {

      case 'create':

        return <CreatePayment onNavigate={navigate} />



      case 'paylink':

        return <PayByLink />



      case 'history':

        return <PaymentHistory />



      case 'split':

        return <SplitPayment />



      case 'integrations':

        return <Integrations />



      default:

        return <Dashboard onNavigate={navigate} />

    }

  }



  return (

    <div className="app-shell">

      <Sidebar

        activePage={activePage}

        onNavigate={navigate}

      />



      <main className="main-content">

        <Topbar title={pageTitles[activePage]} />



        {renderPage()}



        <footer className="footer">

          <span>Payment API</span>

          <span>Payment Management Dashboard</span>

        </footer>

      </main>

    </div>

  )

}