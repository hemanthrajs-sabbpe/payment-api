const API_BASE_URL = (typeof window !== 'undefined' && window.location.hostname && window.location.hostname !== 'localhost' && window.location.hostname !== '127.0.0.1') ? ('http://' + window.location.hostname + ':8080/api') : 'http://localhost:8080/api'

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem('jwtToken')

  const headers = {
    ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...options.headers,
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  const text = await response.text()

  if (!response.ok) {
    let message = text
    try {
      const data = text ? JSON.parse(text) : null
      message = data?.message || data?.error || text
    } catch {
      // Preserve plain-text API errors.
    }
    throw new Error(message || `Request failed with status ${response.status}`)
  }

  if (!text) return null

  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

// ==========================================
// PAYMENT APIs
// ==========================================

// Fetch all payments for the dashboard
export function getPayments() {
  return apiRequest('/payments')
}

// Fetch one payment by its database ID
export function getPaymentById(id) {
  return apiRequest(`/payments/${encodeURIComponent(id)}`)
}

// Initiate a payment using the Easebuzz sandbox
export function initiateSandboxPayment(paymentDetails) {
  return apiRequest('/payments/easebuzz/initiate', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Initiate an Easebuzz payment and return checkout details
export function initiateEasebuzzPayment(paymentDetails) {
  return apiRequest('/payments/easebuzz/initiate', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Regenerate an Easebuzz payment checkout link (only permitted after verified failure)
export function regenerateEasebuzzPayment(paymentId, paymentDetails) {
  return apiRequest(`/payments/easebuzz/regenerate/${encodeURIComponent(paymentId)}`, {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Generate genuine Easebuzz hosted checkout link for a split participant allocation
export function createEasebuzzAllocationPayment(splitId, allocationId, contact = {}) {
  return apiRequest(`/payment-splits/${encodeURIComponent(splitId)}/allocations/${encodeURIComponent(allocationId)}/easebuzz/pay`, {
    method: 'POST',
    body: JSON.stringify(contact),
  })
}

// Create a SabbPe Pay by Link payment

// Create a SabbPe Payment Gateway (Dynamic QR) payment
export function createSabbPeGatewayPayment(paymentDetails) {
  return apiRequest('/sabbpe/gateway/payments', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Fetch status of a SabbPe Payment Gateway payment by ID
// Generate a SabbPe Payment Gateway QR code for an individual split allocation
export function createSplitAllocationGatewayPayment(splitId, allocationId, contact = {}) {
  return apiRequest(`/sabbpe/gateway/splits/${encodeURIComponent(splitId)}/allocations/${encodeURIComponent(allocationId)}/pay`, {
    method: 'POST',
    body: JSON.stringify(contact),
  })
}
export function getSabbPeGatewayPaymentStatus(paymentId) {
  return apiRequest(`/sabbpe/gateway/payments/${encodeURIComponent(paymentId)}`)
}

export function createSabbPePayment(paymentDetails) {
  return apiRequest('/sabbpe/payments', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Explicit regenerate endpoint for standalone or split-allocation PBL
export function regenerateSabbPePayment(paymentDetails) {
  return apiRequest('/sabbpe/payments/regenerate', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}

// Generate SabbPe native Pay By Link for an individual split participant allocation
export function createSplitParticipantSabbPePbl(splitId, allocationId, contact = {}) {
  return apiRequest(`/sabbpe/paybylink/splits/${encodeURIComponent(splitId)}/allocations/${encodeURIComponent(allocationId)}/payments`, {
    method: 'POST',
    body: JSON.stringify(contact),
  })
}

// Generate SabbPe native Pay By Link in bulk for participants in a split
export function createBulkSplitSabbPePbl(splitId, participants = []) {
  return apiRequest(`/sabbpe/paybylink/splits/${encodeURIComponent(splitId)}/payment-links`, {
    method: 'POST',
    body: JSON.stringify({ participants }),
  })
}

// ==========================================
// AUTHENTICATION API
// ==========================================

// Login using the existing Spring Boot authentication API
export async function loginUser(username, password) {
  const response = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({ username, password }),
  })

  const data = await response.json().catch(() => ({}))

  if (!response.ok) {
    throw new Error(data.message || 'Invalid username or password.')
  }

  if (!data.token) {
    throw new Error('Login response did not contain a JWT token.')
  }

  localStorage.setItem('jwtToken', data.token)

  return data
}

// ==========================================
// PAYMENT SPLIT APIs
// ==========================================

// Fetch all payment splits
export function getPaymentSplits() {
  return apiRequest('/payment-splits')
}

// Fetch one payment split by ID
export function getPaymentSplitById(splitId) {
  return apiRequest(`/payment-splits/${encodeURIComponent(splitId)}`)
}

// Create a payment split
export function createPaymentSplit(splitDetails) {
  return apiRequest('/payment-splits', {
    method: 'POST',
    body: JSON.stringify(splitDetails),
  })
}

// Fetch allocations for a split
export function getSplitAllocations(splitId) {
  return apiRequest(
    `/payment-splits/${encodeURIComponent(splitId)}/allocations`
  )
}

// Add an allocation to a split
export function addSplitAllocation(splitId, allocationDetails) {
  return apiRequest(
    `/payment-splits/${encodeURIComponent(splitId)}/allocations`,
    {
      method: 'POST',
      body: JSON.stringify(allocationDetails),
    }
  )
}

// Validate a payment split
export function validatePaymentSplit(splitId) {
  return apiRequest(
    `/payment-splits/${encodeURIComponent(splitId)}/validate`
  )
}

// ==========================================
// BUSINESS CATEGORY & ORGANIZATION APIs
// ==========================================

// Fetch the 12 active business sectors from the backend
export function getBusinessCategories() {
  return apiRequest('/payment-splits/business-categories')
}

// Fetch all available organizations
export function getOrganizations() {
  return apiRequest('/organizations')
}

// ==========================================
// SPLIT TRANSACTION DASHBOARD APIs
// ==========================================

// Fetch master transactions
export async function getMasterTransactions() {
  return getPaymentSplits()
}

// Fetch master transaction details
export function getMasterTransactionDetails(splitId) {
  return getPaymentSplitById(splitId)
}

// Fetch individual split transactions
export async function getIndividualSplitTransactions(splitId) {
  return getPaymentSplitById(splitId)
}

// Generate payment links for split participants
export function generateSplitPaymentLinks(splitId, participants) {
  return apiRequest(
    `/payment-splits/${encodeURIComponent(splitId)}/payment-links`,
    {
      method: 'POST',
      body: JSON.stringify({ participants }),
    }
  )
}

// ==========================================
// PUBLIC API REQUEST
// ==========================================

// Public guest calls deliberately omit the merchant JWT.
async function publicApiRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      Accept: 'application/json',
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.headers || {}),
    },
  })

  const text = await response.text()

  let data = text

  try {
    data = text ? JSON.parse(text) : null
  } catch {
    // Preserve plain-text responses.
  }

  if (!response.ok) {
    const message =
      typeof data === 'string'
        ? data
        : (
            data?.message ||
            data?.error ||
            `Request failed with status ${response.status}`
          )

    throw new Error(message)
  }

  return data
}

// ==========================================
// QR GUEST SESSION APIs
// ==========================================

// Create a QR guest session for a payment split
export function createSplitSession(payload) {
  return apiRequest('/split-sessions', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// Fetch QR sessions for a particular split
export function getSplitSessions(splitId) {
  return apiRequest(
    `/split-sessions/split/${encodeURIComponent(splitId)}`
  )
}

// Regenerate QR session
export function regenerateSplitSessionQr(sessionId) {
  return apiRequest(
    `/split-sessions/${encodeURIComponent(sessionId)}/regenerate`,
    {
      method: 'POST',
    }
  )
}

// Close QR session
export function closeSplitSession(sessionId) {
  return apiRequest(
    `/split-sessions/${encodeURIComponent(sessionId)}/close`,
    {
      method: 'POST',
    }
  )
}

// ==========================================
// PUBLIC GUEST QR VALIDATION
// ==========================================

// Validate QR session using QR token
export function validateSplitSession(qrToken) {
  return publicApiRequest('/split-sessions/validate', {
    method: 'POST',
    body: JSON.stringify({ qrToken }),
  })
}

// ==========================================
// QR PAYMENT SIMULATOR APIs
// ==========================================

// Authenticated merchant endpoint to initiate/refresh allocation simulator QR
export function createSimulatorAllocationQr(splitId, allocationId) {
  return apiRequest(`/simulator/splits/${encodeURIComponent(splitId)}/allocations/${encodeURIComponent(allocationId)}/qr`, {
    method: 'POST',
  })
}

// Public customer endpoint to fetch scan details using QR token
export function getSimulatorQrDetails(token) {
  return publicApiRequest(`/simulator/qr/details?token=${encodeURIComponent(token)}`, {
    method: 'GET',
  })
}

// Public customer endpoint to submit simulated outcome (SUCCESS or FAILURE)
export function processSimulatorOutcome(payload) {
  return publicApiRequest('/simulator/qr/process', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// ==========================================
// RANDOM PAYMENT SIMULATOR APIs
// ==========================================

// Authenticated merchant endpoint to initiate random simulator QR
export function createRandomSimulatorQr(payload) {
  return apiRequest('/simulator/random/qr', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// Public customer endpoint to fetch random simulator details
export function getRandomSimulatorDetails(token) {
  return publicApiRequest(`/simulator/random/details?token=${encodeURIComponent(token)}`, {
    method: 'GET',
  })
}

// Public customer endpoint to submit random simulation outcome
export function processRandomSimulatorOutcome(payload) {
  return publicApiRequest('/simulator/random/process', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

// Reconciles payment status against SabbPe gateway with receiptToken authorization or JWT
export function reconcileSabbPePayment(paymentId, receiptToken = '') {
  const tokenQuery = receiptToken ? `?receiptToken=${encodeURIComponent(receiptToken)}` : ''
  const headers = receiptToken ? { 'X-Receipt-Token': receiptToken } : {}
  return apiRequest(`/sabbpe/reconciliation/payments/${paymentId}/reconcile${tokenQuery}`, {
    method: 'POST',
    headers,
  })
}

