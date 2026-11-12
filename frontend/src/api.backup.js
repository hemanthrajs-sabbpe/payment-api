const API_BASE_URL = 'http://localhost:8080/api'

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem('jwtToken')

  const headers = {
    ...(options.body
      ? { 'Content-Type': 'application/json' }
      : {}),
    ...(token
      ? { Authorization: `Bearer ${token}` }
      : {}),
    ...options.headers,
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  const text = await response.text()

  if (!response.ok) {
    throw new Error(
      text || `Request failed with status ${response.status}`
    )
  }

  if (!text) return null

  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

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

// Create a SabbPe Pay by Link payment
export function createSabbPePayment(paymentDetails) {
  return apiRequest('/sabbpe/payments', {
    method: 'POST',
    body: JSON.stringify(paymentDetails),
  })
}
