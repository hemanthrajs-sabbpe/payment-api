import React, { useState, useEffect } from 'react'
import { QRCodeSVG } from 'qrcode.react'
import {
  getBusinessCategories,
  createSplitAllocationGatewayPayment,
  createSabbPePayment,
  generateSplitPaymentLinks,
  createEasebuzzAllocationPayment,
  createSimulatorAllocationQr
} from '../api'

const API_BASE_URL = (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1')
  ? ('http://' + window.location.hostname + ':8080/api')
  : 'http://localhost:8080/api'

function getAuthHeaders() {
  const headers = { Accept: 'application/json' }
  const token = localStorage.getItem('jwtToken')
  if (token) headers.Authorization = 'Bearer ' + token
  return headers
}

// 12 Backend business sectors + 13th Other / Custom sector
const SECTOR_CONFIG = [
  { code: 'PLAY_AREA', name: 'Play Area', icon: '🎮', desc: 'Amusement, games & recreational facilities' },
  { code: 'RESTAURANT', name: 'Restaurant', icon: '🍽️', desc: 'Dining, cafes & food service bills' },
  { code: 'TOURISM', name: 'Tourism', icon: '✈️', desc: 'Travel bookings, group tours & sightseeing' },
  { code: 'EVENTS', name: 'Events', icon: '🎟️', desc: 'Conferences, ticketed entries & banquets' },
  { code: 'ECOMMERCE', name: 'E-Commerce', icon: '🛍️', desc: 'Online cart, shared basket purchases' },
  { code: 'EDUCATION', name: 'Education', icon: '🎓', desc: 'Tuition fees, coaching & educational courses' },
  { code: 'HEALTHCARE', name: 'Healthcare', icon: '🏥', desc: 'Medical tests, clinical bills & treatments' },
  { code: 'LOGISTICS', name: 'Logistics', icon: '🚚', desc: 'Freight, multi-drop delivery & cargo freight' },
  { code: 'SHOPPING_MALL', name: 'Shopping Mall', icon: '🏬', desc: 'Retail complexes & multi-store purchases' },
  { code: 'REAL_ESTATE', name: 'Real Estate', icon: '🏢', desc: 'Rent, society maintenance & security deposits' },
  { code: 'SPORTS_FITNESS', name: 'Sports and Fitness', icon: '🏋️', desc: 'Gym memberships & sports facility booking' },
  { code: 'HOME_SERVICES', name: 'Home Services', icon: '🧹', desc: 'Housekeeping, maintenance & cleaning' },
  { code: 'OTHER', name: 'Other / Custom', icon: '💼', desc: 'General business & custom multi-party splits' }
]

const SPLIT_METHODS = [
  { id: 'EQUAL', name: 'Equal Split', desc: 'Divide total amount equally among all participants' },
  { id: 'CUSTOM', name: 'Custom Amount Split', desc: 'Manually specify custom amount for each person' },
  { id: 'PERCENTAGE', name: 'Percentage Split', desc: 'Allocate based on percentage shares (total 100%)' },
  { id: 'ONE_PAYER', name: 'One Payer', desc: 'One person covers the full bill for everyone' },
  { id: 'ITEM_BASED', name: 'Item-Based Split', desc: 'Assign specific items/services to participants' },
  { id: 'MIXED', name: 'Mixed Split', desc: 'Combine itemized items with shared remaining split' }
]

export default function SplitPaymentsPage() {
  const [businessCategories, setBusinessCategories] = useState([])
  const [selectedSector, setSelectedSector] = useState(SECTOR_CONFIG[1]) // Default Restaurant
  const [splitMethod, setSplitMethod] = useState('EQUAL')
  const [splitScope, setSplitScope] = useState('BUSINESS')
  const [organizationId, setOrganizationId] = useState('ORG-PBL-SIAN')
  const [totalAmount, setTotalAmount] = useState('100.00')

  // Platform Fee Configuration (Separate feature, NOT a sector)
  const [enablePlatformFee, setEnablePlatformFee] = useState(false)
  const [platformFeeType, setPlatformFeeType] = useState('PERCENTAGE') // PERCENTAGE or FIXED
  const [platformFeeValue, setPlatformFeeValue] = useState('2.5')
  const [feePayer, setFeePayer] = useState('SPLIT_PARTICIPANTS') // MERCHANT or SPLIT_PARTICIPANTS

  const emptyParticipant = (idx) => ({
    participantName: `Participant ${idx || 1}`,
    email: `guest${idx || 1}@example.com`,
    phone: `987654321${(idx || 1) % 10}`,
    participantType: 'FRIEND',
    allocationAmount: '',
    percentage: '',
    items: []
  })

  const [participants, setParticipants] = useState([emptyParticipant(1), emptyParticipant(2)])
  const [history, setHistory] = useState([])
  const [selectedSplit, setSelectedSplit] = useState(null)
  const [allocations, setAllocations] = useState([])
  const [allocationContacts, setAllocationContacts] = useState({})
  const [paymentLinks, setPaymentLinks] = useState({})
  const [activeQrModal, setActiveQrModal] = useState(null)
  const [activePblModal, setActivePblModal] = useState(null)
  const [contactPromptModal, setContactPromptModal] = useState(null)
  const [copiedPbl, setCopiedPbl] = useState(false)
  const [loading, setLoading] = useState(false)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [linkLoadingId, setLinkLoadingId] = useState(null)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    async function loadCategories() {
      try {
        const rows = await getBusinessCategories()
        if (Array.isArray(rows)) {
          setBusinessCategories(rows)
        }
      } catch (err) {
        console.warn('Could not load backend categories:', err.message)
      }
    }
    loadCategories()
    fetchHistory()
  }, [])

  const money = (val) => new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(Number(val) || 0)

  // Calculations
  const numTotal = Number(totalAmount) || 0
  let calculatedFee = 0
  if (enablePlatformFee && numTotal > 0) {
    if (platformFeeType === 'PERCENTAGE') {
      calculatedFee = Number(((numTotal * Number(platformFeeValue)) / 100).toFixed(2))
    } else {
      calculatedFee = Number(platformFeeValue) || 0
    }
  }

  const finalPayableTotal = feePayer === 'SPLIT_PARTICIPANTS' ? (numTotal + calculatedFee) : numTotal

  // Auto calculate when splitMethod is EQUAL
  useEffect(() => {
    if (splitMethod === 'EQUAL' && finalPayableTotal > 0 && participants.length > 0) {
      const count = participants.length
      const baseShare = Math.floor((finalPayableTotal / count) * 100) / 100
      const remainder = Number((finalPayableTotal - (baseShare * count)).toFixed(2))

      setParticipants(prev => prev.map((p, idx) => ({
        ...p,
        allocationAmount: String(idx === 0 ? Number((baseShare + remainder).toFixed(2)) : baseShare),
        percentage: ((1 / count) * 100).toFixed(2)
      })))
    } else if (splitMethod === 'ONE_PAYER' && finalPayableTotal > 0) {
      setParticipants(prev => prev.map((p, idx) => ({
        ...p,
        allocationAmount: idx === 0 ? String(finalPayableTotal) : '0.00',
        percentage: idx === 0 ? '100' : '0'
      })))
    }
  }, [splitMethod, finalPayableTotal, participants.length])

  async function apiCall(path, options = {}) {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        ...getAuthHeaders(),
        ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        ...(options.headers || {})
      }
    })
    const raw = await response.text()
    let data = raw
    try { data = raw ? JSON.parse(raw) : null } catch {}
    if (!response.ok) {
      const msg = typeof data === 'string' ? data : (data?.message || `Request failed (${response.status})`)
      throw new Error(msg)
    }
    return data
  }

  async function fetchHistory() {
    setHistoryLoading(true)
    try {
      const res = await apiCall('/payment-splits')
      setHistory(Array.isArray(res) ? res : [])
    } catch (e) {
      console.warn('History load failed:', e.message)
    } finally {
      setHistoryLoading(false)
    }
  }

  function handleAddParticipant() {
    if (participants.length >= 20) {
      setError('Maximum 20 participants allowed.')
      return
    }
    setParticipants(prev => [...prev, emptyParticipant(prev.length + 1)])
  }

  function handleRemoveParticipant(idx) {
    if (participants.length <= 1) return
    setParticipants(prev => prev.filter((_, i) => i !== idx))
  }

  function handleParticipantChange(idx, field, val) {
    setParticipants(prev => prev.map((p, i) => i === idx ? { ...p, [field]: val } : p))
  }

  // Create Master Split on backend
  async function handleCreateSplit(e) {
    e.preventDefault()
    setError('')
    setNotice('')
    setLoading(true)

    try {
      if (numTotal <= 0) throw new Error('Please enter a valid bill amount greater than 0.')

      // Validate sum of allocations
      const allocatedSum = participants.reduce((acc, p) => acc + (Number(p.allocationAmount) || 0), 0)
      if (Math.abs(allocatedSum - finalPayableTotal) > 0.05) {
        throw new Error(`Allocation total (₹${allocatedSum.toFixed(2)}) must equal final payable total (₹${finalPayableTotal.toFixed(2)}).`)
      }

      // Find matching backend business category id
      const backendCat = businessCategories.find(c => c.categoryCode === selectedSector.code)
      const businessCategoryId = backendCat ? backendCat.id : null

      const payload = {
        splitType: selectedSector.code,
        totalAmount: finalPayableTotal,
        currencyCode: 'INR',
        paymentMode: splitMethod === 'ONE_PAYER' ? 'ONE_PAYS' : 'INDIVIDUAL',
        platformFeePercentage: enablePlatformFee ? Number(platformFeeValue) : 0,
        splitScope: splitScope,
        organizationId: splitScope === 'BUSINESS' ? organizationId : null,
        businessCategoryId: splitScope === 'BUSINESS' ? businessCategoryId : null,
        allocations: participants.map(p => ({
          participantName: p.participantName.trim() || 'Guest',
          participantType: 'FRIEND',
          allocationAmount: Number(p.allocationAmount),
          allocationPercentage: Number(((Number(p.allocationAmount) / finalPayableTotal) * 100).toFixed(2))
        }))
      }

      const created = await apiCall('/payment-splits', {
        method: 'POST',
        body: JSON.stringify(payload)
      })

      if (!created?.id) throw new Error('Failed to create split record on backend.')

      setSelectedSplit(created)
      const loadedAllocations = await apiCall(`/payment-splits/${created.id}/allocations`)
      setAllocations(Array.isArray(loadedAllocations) ? loadedAllocations : [])

      // Map participant emails
      const contactMap = {}
      loadedAllocations.forEach((alloc, i) => {
        if (participants[i]) {
          contactMap[alloc.id] = {
            email: participants[i].email,
            phone: participants[i].phone
          }
        }
      })
      setAllocationContacts(contactMap)

      setNotice(`Master Split #${created.id} created successfully for ${selectedSector.name}! Now you can generate QRs.`)
      await fetchHistory()
      setTimeout(() => document.getElementById('split-details')?.scrollIntoView({ behavior: 'smooth' }), 100)
    } catch (err) {
      setError(err.message || 'Error creating split transaction.')
    } finally {
      setLoading(false)
    }
  }

  // Open existing split
  async function openSplit(item) {
    setSelectedSplit(item)
    setError('')
    setNotice('')
    try {
      const res = await apiCall(`/payment-splits/${item.id}/allocations`)
      setAllocations(Array.isArray(res) ? res : [])
    } catch (err) {
      setError('Unable to load allocations for this split.')
    }
  }

  // Auto-polling for active QR payment status
  useEffect(() => {
    if (!activeQrModal?.paymentId || activeQrModal?.status === 'SUCCESS') return

    const interval = setInterval(async () => {
      try {
        const res = await apiCall(`/payments/${activeQrModal.paymentId}`)
        if (res?.status === 'SUCCESS') {
          setActiveQrModal((prev) => prev ? { ...prev, status: 'SUCCESS' } : null)
          setNotice(`Payment for ${activeQrModal.participantName} succeeded!`)
          // refresh allocations
          if (selectedSplit?.id) {
            const refreshed = await apiCall(`/payment-splits/${selectedSplit.id}/allocations`)
            setAllocations(Array.isArray(refreshed) ? refreshed : [])
          }
          clearInterval(interval)
        }
      } catch (e) {}
    }, 2500)

    return () => clearInterval(interval)
  }, [activeQrModal?.paymentId, activeQrModal?.status, selectedSplit?.id])

  // Generate QR Payment Simulator QR
  async function handleGenerateSimulatorQr(allocation) {
    setLinkLoadingId(allocation.id)
    setError('')
    try {
      const res = await createSimulatorAllocationQr(selectedSplit.id, allocation.id)
      setActiveQrModal({
        type: 'SIMULATOR',
        allocationId: allocation.id,
        participantName: allocation.participantName,
        amount: res.amount,
        paymentId: res.paymentId,
        qrToken: res.qrToken,
        scanUrl: res.scanUrl,
        expiresAt: res.expiresAt,
        status: 'PENDING'
      })
      const refreshed = await apiCall(`/payment-splits/${selectedSplit.id}/allocations`)
      setAllocations(Array.isArray(refreshed) ? refreshed : [])
    } catch (err) {
      setError(err.message || 'Failed to generate Simulator QR.')
    } finally {
      setLinkLoadingId(null)
    }
  }

  // Generate SabbPe PBL Payment Link
  async function handleGeneratePblLink(allocation) {
    setError('')
    const contact = allocationContacts[allocation.id]

    // If valid contact details are missing, prompt user to input valid email and phone
    if (!contact || !contact.email || !contact.phone || !contact.email.includes('@') || contact.phone.length < 10) {
      setContactPromptModal({
        allocation,
        participantName: allocation.participantName,
        email: contact?.email || '',
        phone: contact?.phone || ''
      })
      return
    }

    await executeGeneratePblLink(allocation, contact.email, contact.phone)
  }

  async function executeGeneratePblLink(allocation, email, phone) {
    setLinkLoadingId(allocation.id)
    setError('')
    try {
      const trimmedEmail = email.trim()
      const trimmedPhone = phone.trim()

      if (!trimmedEmail || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(trimmedEmail)) {
        throw new Error('A valid email address is required.')
      }
      if (!trimmedPhone || !/^\+?[0-9]{10,15}$/.test(trimmedPhone)) {
        throw new Error('A valid 10-15 digit mobile number is required.')
      }

      // Preserve confirmed contact details in state
      setAllocationContacts(prev => ({
        ...prev,
        [allocation.id]: { email: trimmedEmail, phone: trimmedPhone }
      }))

      // Initiate genuine Easebuzz hosted checkout for this allocation
      const itemResult = await createEasebuzzAllocationPayment(
        selectedSplit.id,
        allocation.id,
        {
          email: trimmedEmail,
          phone: trimmedPhone
        }
      )

      if (!itemResult) {
        throw new Error('Invalid response from Easebuzz checkout API.')
      }

      if (itemResult.status === 'FAILED') {
        throw new Error(itemResult.message || 'Payment-link initiation failed on backend.')
      }

      const extractedUrl = itemResult.checkoutUrl || itemResult.paymentUrl || ''
      const txnRef = itemResult.transactionId || ''
      const paymentStatus = itemResult.status || 'PENDING'
      const paymentId = itemResult.paymentId || null
      const emailStatus = itemResult.emailDispatchStatus || 'NOT_REQUESTED'

      setActivePblModal({
        allocationId: allocation.id,
        participantName: allocation.participantName,
        recipientEmail: trimmedEmail,
        amount: allocation.allocationAmount,
        paymentId: paymentId,
        pblOrderRef: txnRef,
        paymentUrl: extractedUrl,
        paymentStatus: paymentStatus,
        localEmailDelivery: emailStatus,
        statusText: itemResult.message || 'Easebuzz hosted checkout session generated successfully'
      })

      setNotice(`Easebuzz hosted checkout generated for ${allocation.participantName} (Txn: ${txnRef || 'N/A'})!`)

      const refreshed = await apiCall(`/payment-splits/${selectedSplit.id}/allocations`)
      setAllocations(Array.isArray(refreshed) ? refreshed : [])
    } catch (err) {
      setError(err.message || 'Failed to generate Easebuzz hosted checkout.')
    } finally {
      setLinkLoadingId(null)
      setContactPromptModal(null)
    }
  }

  // Generate authentic static UPI QR and SabbPe Gateway payment
  async function handleGenerateAllocationQr(allocation) {
    setLinkLoadingId(allocation.id)
    setError('')
    try {
      const contact = allocationContacts[allocation.id] || { email: 'customer@example.com', phone: '9876543210' }
      let res = null
      try {
        res = await createSplitAllocationGatewayPayment(
          selectedSplit.id,
          allocation.id,
          {
            email: contact.email || 'customer@example.com',
            phone: contact.phone || '9876543210'
          }
        )
      } catch (gwErr) {
        console.warn('Gateway initiation fallback to direct static QR:', gwErr.message)
      }

      // Verified Direct UPI URI according to SabbPe specification
      const uniqueRef = res?.merchantOrderRef || `SPLIT-${selectedSplit.id}-${allocation.id}`
      const amountVal = Number(allocation.allocationAmount).toFixed(2)
      const staticUpiQr = `upi://pay?pa=sabbpeuat@fblpay&pn=SABBPE&mc=7322&tr=${encodeURIComponent(uniqueRef)}&am=${amountVal}&cu=INR&tn=${encodeURIComponent(`Split ${selectedSplit.id} - ${allocation.participantName}`)}`

      setActiveQrModal({
        allocationId: allocation.id,
        participantName: allocation.participantName,
        amount: allocation.allocationAmount,
        paymentId: res?.paymentId,
        paymentUrl: res?.paymentUrl || '',
        staticUpiQr: staticUpiQr,
        merchantOrderRef: uniqueRef,
        status: res?.status || 'PENDING'
      })

      // Refresh allocations
      const refreshed = await apiCall(`/payment-splits/${selectedSplit.id}/allocations`)
      setAllocations(Array.isArray(refreshed) ? refreshed : [])
    } catch (err) {
      setError(err.message || 'Failed to generate payment QR for allocation.')
    } finally {
      setLinkLoadingId(null)
    }
  }

  return (
    <div className="page-wrapper">
      <div style={{ marginBottom: 20 }}>
        <h2>SabbPe Universal Split Payment Module</h2>
        <p className="card-subtitle">
          Configure sector-wise group bills, fee structures, and generate verified static & dynamic payment QRs.
        </p>
      </div>

      {error && (
        <div className="alert-box alert-danger" style={{ marginBottom: 16 }}>
          <span>{error}</span>
        </div>
      )}
      {notice && (
        <div className="alert-box alert-info" style={{ marginBottom: 16 }}>
          <span>{notice}</span>
        </div>
      )}

      {/* STEP 1: BUSINESS SECTOR SELECTION */}
      <div className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <div>
            <h3>Step 1: Select Business Sector</h3>
            <p className="card-subtitle">Choose from the 12 active sectors or Other / Custom</p>
          </div>
          <span className="badge badge-outline">Sector: {selectedSector.name}</span>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(210px, 1fr))', gap: 12 }}>
          {SECTOR_CONFIG.map(sec => {
            const isSelected = selectedSector.code === sec.code
            return (
              <div
                key={sec.code}
                onClick={() => setSelectedSector(sec)}
                style={{
                  border: isSelected ? '2px solid #2563eb' : '1px solid #e2e8f0',
                  background: isSelected ? '#eff6ff' : '#ffffff',
                  borderRadius: 10,
                  padding: 14,
                  cursor: 'pointer',
                  transition: 'all 0.15s ease-in-out'
                }}
              >
                <div style={{ fontSize: 26, marginBottom: 6 }}>{sec.icon}</div>
                <strong style={{ display: 'block', fontSize: 14, color: isSelected ? '#1e40af' : '#1e293b' }}>
                  {sec.name}
                </strong>
                <p style={{ margin: '4px 0 0', fontSize: 11, color: '#64748b', lineHeight: 1.4 }}>
                  {sec.desc}
                </p>
              </div>
            )
          })}
        </div>
      </div>

      {/* STEP 2: SPLIT CONFIGURATION */}
      <form onSubmit={handleCreateSplit} className="card" style={{ marginBottom: 24 }}>
        <div className="card-header">
          <div>
            <h3>Step 2: Split Method & Bill Settings</h3>
            <p className="card-subtitle">Selected Sector: <strong>{selectedSector.name}</strong></p>
          </div>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 16, marginBottom: 20 }}>
          <div className="form-group">
            <label>Split Method *</label>
            <select
              value={splitMethod}
              onChange={(e) => setSplitMethod(e.target.value)}
              disabled={loading}
              style={{ width: '100%', padding: '9px 12px', borderRadius: 8, border: '1px solid #cbd5e1' }}
            >
              {SPLIT_METHODS.map(m => (
                <option key={m.id} value={m.id}>{m.name}</option>
              ))}
            </select>
            <small style={{ color: '#64748b' }}>
              {SPLIT_METHODS.find(m => m.id === splitMethod)?.desc}
            </small>
          </div>

          <div className="form-group">
            <label>Total Bill Amount (₹) *</label>
            <input
              type="number"
              step="0.01"
              min="1.00"
              required
              value={totalAmount}
              onChange={(e) => setTotalAmount(e.target.value)}
              disabled={loading}
              placeholder="e.g. 500.00"
              style={{ width: '100%', padding: '9px 12px', borderRadius: 8, border: '1px solid #cbd5e1' }}
            />
          </div>

          <div className="form-group">
            <label>Split Scope</label>
            <select
              value={splitScope}
              onChange={(e) => setSplitScope(e.target.value)}
              disabled={loading}
              style={{ width: '100%', padding: '9px 12px', borderRadius: 8, border: '1px solid #cbd5e1' }}
            >
              <option value="BUSINESS">Business / Organization Split</option>
              <option value="PERSONAL">Personal Split</option>
            </select>
          </div>

          {splitScope === 'BUSINESS' && (
            <div className="form-group">
              <label>Organization ID</label>
              <input
                type="text"
                value={organizationId}
                onChange={(e) => setOrganizationId(e.target.value)}
                disabled={loading}
                style={{ width: '100%', padding: '9px 12px', borderRadius: 8, border: '1px solid #cbd5e1' }}
              />
            </div>
          )}
        </div>

        {/* STEP 4: PLATFORM FEE CONFIGURATION (SEPARATE FEATURE) */}
        <div style={{ padding: 16, border: '1px solid #e2e8f0', borderRadius: 10, background: '#f8fafc', marginBottom: 20 }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 12 }}>
            <div>
              <strong style={{ fontSize: 14 }}>Platform Fee Configuration (Optional)</strong>
              <p style={{ margin: 0, fontSize: 12, color: '#64748b' }}>
                Separate surcharge setting — not a split type or business sector.
              </p>
            </div>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', fontSize: 13, fontWeight: 600 }}>
              <input
                type="checkbox"
                checked={enablePlatformFee}
                onChange={(e) => setEnablePlatformFee(e.target.checked)}
              />
              Enable Fee
            </label>
          </div>

          {enablePlatformFee && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 14 }}>
              <div className="form-group">
                <label>Calculation Type</label>
                <select
                  value={platformFeeType}
                  onChange={(e) => setPlatformFeeType(e.target.value)}
                  style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #cbd5e1' }}
                >
                  <option value="PERCENTAGE">Percentage (%)</option>
                  <option value="FIXED">Fixed Amount (₹)</option>
                </select>
              </div>

              <div className="form-group">
                <label>{platformFeeType === 'PERCENTAGE' ? 'Fee Percentage (%)' : 'Fixed Fee Amount (₹)'}</label>
                <input
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={platformFeeValue}
                  onChange={(e) => setPlatformFeeValue(e.target.value)}
                  style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #cbd5e1' }}
                />
              </div>

              <div className="form-group">
                <label>Charged To (Payer)</label>
                <select
                  value={feePayer}
                  onChange={(e) => setFeePayer(e.target.value)}
                  style={{ width: '100%', padding: 8, borderRadius: 6, border: '1px solid #cbd5e1' }}
                >
                  <option value="SPLIT_PARTICIPANTS">Added to Participants Split</option>
                  <option value="MERCHANT">Absorbed by Merchant</option>
                </select>
              </div>
            </div>
          )}

          {enablePlatformFee && (
            <div style={{ marginTop: 12, fontSize: 12, color: '#475569', display: 'flex', gap: 20 }}>
              <span>Base: <strong>{money(numTotal)}</strong></span>
              <span>Platform Fee: <strong>{money(calculatedFee)}</strong></span>
              <span>Final Payable: <strong style={{ color: '#2563eb' }}>{money(finalPayableTotal)}</strong></span>
            </div>
          )}
        </div>

        {/* STEP 3 & 5: PARTICIPANTS MANAGEMENT */}
        <div style={{ marginBottom: 20 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
            <strong style={{ fontSize: 14 }}>Participants ({participants.length})</strong>
            <button
              type="button"
              className="btn-secondary"
              onClick={handleAddParticipant}
              disabled={loading}
              style={{ fontSize: 12, padding: '6px 12px' }}
            >
              + Add Participant
            </button>
          </div>

          <div style={{ display: 'grid', gap: 10 }}>
            {participants.map((p, idx) => (
              <div
                key={idx}
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr)) 40px',
                  gap: 10,
                  alignItems: 'center',
                  background: '#f8fafc',
                  padding: 10,
                  borderRadius: 8,
                  border: '1px solid #e2e8f0'
                }}
              >
                <div>
                  <input
                    type="text"
                    placeholder="Name"
                    value={p.participantName}
                    onChange={(e) => handleParticipantChange(idx, 'participantName', e.target.value)}
                    required
                    style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                </div>
                <div>
                  <input
                    type="email"
                    placeholder="Email"
                    value={p.email}
                    onChange={(e) => handleParticipantChange(idx, 'email', e.target.value)}
                    required
                    style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                </div>
                <div>
                  <input
                    type="tel"
                    placeholder="Mobile"
                    value={p.phone}
                    onChange={(e) => handleParticipantChange(idx, 'phone', e.target.value)}
                    required
                    maxLength={10}
                    style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                </div>
                <div>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="Amount (₹)"
                    value={p.allocationAmount}
                    onChange={(e) => handleParticipantChange(idx, 'allocationAmount', e.target.value)}
                    disabled={splitMethod === 'EQUAL' || splitMethod === 'ONE_PAYER'}
                    required
                    style={{ width: '100%', padding: '6px 8px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                </div>
                <button
                  type="button"
                  onClick={() => handleRemoveParticipant(idx)}
                  disabled={participants.length <= 1}
                  style={{
                    background: 'none',
                    border: 'none',
                    color: '#ef4444',
                    cursor: participants.length <= 1 ? 'not-allowed' : 'pointer',
                    fontSize: 16
                  }}
                  title="Remove"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </div>

        <button type="submit" className="btn-primary btn-block" disabled={loading}>
          {loading ? 'Creating Split on SabbPe UAT...' : `Create Master Split & Allocations (${money(finalPayableTotal)})`}
        </button>
      </form>

      {/* STEP 5: CREATED SPLIT & QR CODES */}
      {selectedSplit && (
        <div id="split-details" className="card" style={{ marginBottom: 24 }}>
          <div className="card-header">
            <div>
              <h3>Master Split #{selectedSplit.id} — Allocations & Universal QRs</h3>
              <p className="card-subtitle">
                Total: {money(selectedSplit.totalAmount)} | Status: <strong>{selectedSplit.status}</strong>
              </p>
            </div>
            <button className="btn-secondary" onClick={() => openSplit(selectedSplit)}>
              Refresh Allocations
            </button>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                  <th style={{ padding: 10, textAlign: 'left' }}>Participant</th>
                  <th style={{ padding: 10, textAlign: 'left' }}>Allocation Amount</th>
                  <th style={{ padding: 10, textAlign: 'left' }}>Paid Amount</th>
                  <th style={{ padding: 10, textAlign: 'left' }}>Payment ID</th>
                  <th style={{ padding: 10, textAlign: 'left' }}>Status</th>
                  <th style={{ padding: 10, textAlign: 'right' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {allocations.map(alloc => (
                  <tr key={alloc.id} style={{ borderBottom: '1px solid #e2e8f0' }}>
                    <td style={{ padding: 10 }}>
                      <strong>{alloc.participantName}</strong>
                      <div style={{ fontSize: 11, color: '#64748b' }}>Allocation #{alloc.id}</div>
                    </td>
                    <td style={{ padding: 10 }}><strong>{money(alloc.allocationAmount)}</strong></td>
                    <td style={{ padding: 10 }}>{money(alloc.paidAmount)}</td>
                    <td style={{ padding: 10 }}>{alloc.paymentId ? `#${alloc.paymentId}` : '—'}</td>
                    <td style={{ padding: 10 }}>
                      <span className={`badge ${alloc.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                        {alloc.status}
                      </span>
                    </td>
                    <td style={{ padding: 10, textAlign: 'right' }}>
                      <div style={{ display: 'inline-flex', gap: 6 }}>
                        <button
                          type="button"
                          className="btn-secondary"
                          style={{ fontSize: 11, padding: '5px 10px', background: '#eff6ff', color: '#2563eb', borderColor: '#bfdbfe' }}
                          onClick={() => handleGeneratePblLink(alloc)}
                          disabled={linkLoadingId === alloc.id || alloc.status === 'SUCCESS'}
                          title={alloc.status === 'FAILED' ? 'Retry Easebuzz Pay by Link' : 'Generate Easebuzz Pay by Link'}
                        >
                          {alloc.status === 'FAILED' ? 'Retry PBL' : 'Easebuzz Link'}
                        </button>
                        <button
                          type="button"
                          className="btn-secondary"
                          style={{ fontSize: 11, padding: '5px 10px', background: '#f5f3ff', color: '#7c3aed', borderColor: '#ddd6fe' }}
                          onClick={() => handleGenerateSimulatorQr(alloc)}
                          disabled={linkLoadingId === alloc.id || alloc.status === 'SUCCESS'}
                          title="Generate Simulator QR for sandbox testing"
                        >
                          {linkLoadingId === alloc.id ? 'Loading...' : 'Simulator QR'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* CONTACT PROMPT MODAL FOR SPLIT PBL */}
      {contactPromptModal && (
        <div className="card" style={{ marginBottom: 24, background: '#f8fafc', border: '2px solid #2563eb' }}>
          <div className="card-header">
            <div>
              <h3>Participant Contact Details — {contactPromptModal.participantName}</h3>
              <p className="card-subtitle">
                Enter valid recipient contact info to dispatch the official Easebuzz Pay by Link.
              </p>
            </div>
            <button className="btn-secondary" onClick={() => setContactPromptModal(null)}>Cancel</button>
          </div>
          <form
            onSubmit={(e) => {
              e.preventDefault()
              executeGeneratePblLink(
                contactPromptModal.allocation,
                contactPromptModal.email,
                contactPromptModal.phone
              )
            }}
            style={{ padding: 16 }}
          >
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 16 }}>
              <div>
                <label style={{ display: 'block', fontSize: 13, marginBottom: 6, fontWeight: 500 }}>Customer Email *</label>
                <input
                  type="email"
                  value={contactPromptModal.email}
                  onChange={(e) => setContactPromptModal(prev => ({ ...prev, email: e.target.value }))}
                  placeholder="customer@example.com"
                  required
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', fontSize: 13, marginBottom: 6, fontWeight: 500 }}>Customer Mobile (10-15 digits) *</label>
                <input
                  type="tel"
                  value={contactPromptModal.phone}
                  onChange={(e) => setContactPromptModal(prev => ({ ...prev, phone: e.target.value }))}
                  placeholder="9876543210"
                  required
                  style={{ width: '100%', padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10 }}>
              <button
                type="button"
                className="btn-secondary"
                onClick={() => setContactPromptModal(null)}
              >
                Cancel
              </button>
              <button
                type="submit"
                className="btn-primary"
                disabled={linkLoadingId === contactPromptModal.allocation.id}
              >
                {linkLoadingId === contactPromptModal.allocation.id ? 'Generating...' : 'Generate Pay by Link'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* PBL OUTPUT MODAL */}
      {activePblModal && (
        <div className="card" style={{ marginBottom: 24, background: '#f8fafc', border: '2px solid #16a34a' }}>
          <div className="card-header">
            <div>
              <h3>Pay by Link — {activePblModal.participantName}</h3>
              <p className="card-subtitle">Payable Allocation Amount: <strong>{money(activePblModal.amount)}</strong></p>
            </div>
            <button className="btn-secondary" onClick={() => setActivePblModal(null)}>Close</button>
          </div>
          <div style={{ padding: 16 }}>
            <div className="qr-meta-box" style={{ marginBottom: 16 }}>
              {activePblModal.paymentId && (
                <div className="qr-meta-row">
                  <span>Payment ID:</span>
                  <strong>#{activePblModal.paymentId}</strong>
                </div>
              )}
              {activePblModal.pblOrderRef && (
                <div className="qr-meta-row">
                  <span>PBL Order Ref:</span>
                  <strong className="font-mono">{activePblModal.pblOrderRef}</strong>
                </div>
              )}
              <div className="qr-meta-row">
                <span>Payment Status:</span>
                <span className={`badge ${activePblModal.paymentStatus === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                  {activePblModal.paymentStatus}
                </span>
              </div>
              <div className="qr-meta-row">
                <span>Local Email Delivery:</span>
                <span
                  className={`badge ${
                    activePblModal.localEmailDelivery === 'SMTP_ACCEPTED'
                      ? 'status-success'
                      : activePblModal.localEmailDelivery === 'FAILED'
                      ? 'status-danger'
                      : 'status-pending'
                  }`}
                >
                  {activePblModal.localEmailDelivery === 'SMTP_ACCEPTED'
                    ? `SMTP_ACCEPTED (Accepted for ${activePblModal.recipientEmail || 'recipient'})`
                    : activePblModal.localEmailDelivery === 'FAILED'
                    ? 'FAILED (SMTP error)'
                    : activePblModal.localEmailDelivery}
                </span>
              </div>
            </div>

            {activePblModal.paymentUrl ? (
              <div>
                <label style={{ display: 'block', fontSize: 13, marginBottom: 6, fontWeight: 500 }}>
                  Hosted Checkout URL
                </label>
                <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
                  <input
                    value={activePblModal.paymentUrl}
                    readOnly
                    className="font-mono text-sm"
                    style={{ flex: 1, padding: '8px 10px', borderRadius: 6, border: '1px solid #cbd5e1' }}
                  />
                  <button
                    type="button"
                    className="btn-secondary"
                    onClick={async () => {
                      try {
                        await navigator.clipboard.writeText(activePblModal.paymentUrl)
                        setCopiedPbl(true)
                        setTimeout(() => setCopiedPbl(false), 2000)
                      } catch {
                        setError('Could not copy automatically. Select and copy manually.')
                      }
                    }}
                  >
                    {copiedPbl ? 'Copied!' : 'Copy Link'}
                  </button>
                  <a
                    href={activePblModal.paymentUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-primary"
                    style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}
                  >
                    Open Checkout
                  </a>
                </div>
              </div>
            ) : (
              <div className="alert-box alert-info" style={{ marginBottom: 16 }}>
                <span>
                  SabbPe reference: <strong>{activePblModal.pblOrderRef || 'Created'}</strong>.
                  {activePblModal.statusText ? ` (${activePblModal.statusText})` : ''}
                </span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* QR MODAL: MANDATE QR AND HOSTED URL QR */}
      {activeQrModal && (
        <div className="card" style={{ marginBottom: 24, background: '#f8fafc', border: '2px solid #2563eb' }}>
          <div className="card-header">
            <div>
              <h3>Payment QR — {activeQrModal.participantName}</h3>
              <p className="card-subtitle">Payable Amount: <strong>{money(activeQrModal.amount)}</strong></p>
            </div>
            <button className="btn-secondary" onClick={() => setActiveQrModal(null)}>Close</button>
          </div>

          {activeQrModal.status === 'SUCCESS' ? (
            <div style={{ padding: 24, textAlign: 'center', background: '#ecfdf5', borderRadius: 8, border: '1px solid #a7f3d0' }}>
              <h3 style={{ color: '#059669', marginBottom: 8 }}>Payment Successful!</h3>
              <p style={{ color: '#047857' }}>
                Payment for <strong>{activeQrModal.participantName}</strong> of <strong>{money(activeQrModal.amount)}</strong> has been verified and confirmed.
              </p>
            </div>
          ) : activeQrModal.type === 'SIMULATOR' ? (
            /* SIMULATOR QR VIEW */
            <div style={{ textAlign: 'center', padding: 16 }}>
              <div style={{ background: '#ffffff', padding: 20, borderRadius: 12, border: '1px solid #ddd6fe', maxWidth: '380px', margin: '0 auto', boxShadow: '0 4px 6px -1px rgba(124, 58, 237, 0.1)' }}>
                <strong style={{ display: 'block', marginBottom: 4, color: '#7c3aed', fontSize: 16 }}>
                  QR Simulator Test Code
                </strong>
                <p style={{ fontSize: 11, color: '#64748b', marginBottom: 14 }}>
                  Scan with mobile device or click URL to test customer approval / failure
                </p>
                <div style={{ display: 'inline-block', padding: 10, background: '#fff', borderRadius: 8, border: '1px solid #ede9fe' }}>
                  <QRCodeSVG value={activeQrModal.scanUrl} size={220} level="M" includeMargin />
                </div>
                <div style={{ marginTop: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <a
                    href={activeQrModal.scanUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="btn-primary"
                    style={{ background: '#7c3aed', borderColor: '#7c3aed', textAlign: 'center', textDecoration: 'none' }}
                  >
                    Open Customer Scan Page
                  </a>
                  <button
                    type="button"
                    className="btn-secondary"
                    style={{ fontSize: 11, padding: '6px 10px' }}
                    onClick={() => {
                      navigator.clipboard.writeText(activeQrModal.scanUrl)
                      alert('Simulator customer scan URL copied to clipboard!')
                    }}
                  >
                    Copy Customer URL
                  </button>
                </div>
                {activeQrModal.expiresAt && (
                  <p style={{ margin: '12px 0 0', fontSize: 11, color: '#94a3b8' }}>
                    Expires at: {new Date(activeQrModal.expiresAt).toLocaleTimeString()}
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div style={{ textAlign: 'center', padding: 10 }}>
              {/* SABBPE GATEWAY CHECKOUT QR */}
              {activeQrModal.paymentUrl ? (
                <div style={{ background: '#ffffff', padding: 16, borderRadius: 10, border: '1px solid #e2e8f0', maxWidth: '380px', margin: '0 auto' }}>
                  <strong style={{ display: 'block', marginBottom: 6, color: '#2563eb' }}>
                    SabbPe Hosted Checkout QR
                  </strong>
                  <p style={{ fontSize: 11, color: '#64748b', marginBottom: 12 }}>
                    Scan with mobile camera or open to pay via Cards, UPI, or Net Banking
                  </p>
                  <div style={{ display: 'inline-block', padding: 8, background: '#fff', borderRadius: 8 }}>
                    <QRCodeSVG value={activeQrModal.paymentUrl} size={200} level="M" includeMargin />
                  </div>
                  <div style={{ marginTop: 10, display: 'flex', gap: 8, justifyContent: 'center' }}>
                    <a
                      href={activeQrModal.paymentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="btn-primary"
                      style={{ fontSize: 11, padding: '4px 8px' }}
                    >
                      Open Checkout
                    </a>
                  </div>
                </div>
              ) : (
                <div style={{ background: '#ffffff', padding: 16, borderRadius: 10, border: '1px solid #e2e8f0', maxWidth: '380px', margin: '0 auto' }}>
                  <strong style={{ display: 'block', marginBottom: 6, color: '#2563eb' }}>
                    SabbPe UPI Payment
                  </strong>
                  <p style={{ fontSize: 11, color: '#64748b', marginBottom: 12 }}>
                    VPA: <strong>sabbpeuat@fblpay</strong>
                  </p>
                  {activeQrModal.staticUpiQr && (
                    <div style={{ display: 'inline-block', padding: 8, background: '#fff', borderRadius: 8 }}>
                      <QRCodeSVG value={activeQrModal.staticUpiQr} size={200} level="M" includeMargin />
                    </div>
                  )}
                  {activeQrModal.staticUpiQr && (
                    <div style={{ marginTop: 10 }}>
                      <button
                        className="btn-secondary"
                        style={{ fontSize: 11, padding: '4px 8px' }}
                        onClick={() => {
                          navigator.clipboard.writeText(activeQrModal.staticUpiQr)
                          alert('UPI link copied!')
                        }}
                      >
                        Copy UPI URI
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* RECENT MASTER SPLITS */}
      <div className="card">
        <div className="card-header">
          <div>
            <h3>Split History</h3>
            <p className="card-subtitle">Previous master split transactions</p>
          </div>
          <button className="btn-secondary" onClick={fetchHistory} disabled={historyLoading}>
            {historyLoading ? 'Refreshing...' : 'Refresh History'}
          </button>
        </div>

        <div style={{ overflowX: 'auto' }}>
          <table className="data-table" style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ background: '#f8fafc', borderBottom: '1px solid #e2e8f0' }}>
                <th style={{ padding: 10, textAlign: 'left' }}>Split ID</th>
                <th style={{ padding: 10, textAlign: 'left' }}>Sector</th>
                <th style={{ padding: 10, textAlign: 'left' }}>Mode</th>
                <th style={{ padding: 10, textAlign: 'left' }}>Total Amount</th>
                <th style={{ padding: 10, textAlign: 'left' }}>Status</th>
                <th style={{ padding: 10, textAlign: 'right' }}>Action</th>
              </tr>
            </thead>
            <tbody>
              {history.map(item => (
                <tr key={item.id} style={{ borderBottom: '1px solid #e2e8f0' }}>
                  <td style={{ padding: 10 }}>#{item.id}</td>
                  <td style={{ padding: 10 }}>{item.splitType || 'SECTOR_SPLIT'}</td>
                  <td style={{ padding: 10 }}>{item.paymentMode}</td>
                  <td style={{ padding: 10 }}><strong>{money(item.totalAmount)}</strong></td>
                  <td style={{ padding: 10 }}>
                    <span className={`badge ${item.status === 'SUCCESS' ? 'status-success' : 'status-pending'}`}>
                      {item.status}
                    </span>
                  </td>
                  <td style={{ padding: 10, textAlign: 'right' }}>
                    <button
                      className="btn-secondary"
                      style={{ fontSize: 11, padding: '4px 8px' }}
                      onClick={() => openSplit(item)}
                    >
                      View Allocations
                    </button>
                  </td>
                </tr>
              ))}
              {history.length === 0 && (
                <tr>
                  <td colSpan="6" style={{ padding: 16, textAlign: 'center', color: '#64748b' }}>
                    No split history recorded yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
