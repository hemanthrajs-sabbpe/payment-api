import React, { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom'
import './App.css'
import './index.css'

import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import CreatePaymentPage from './pages/CreatePaymentPage'
import PayByLinkPage from './pages/PayByLinkPage'
import QrPaymentsPage from './pages/QrPaymentsPage'
import SplitPaymentsPage from './pages/SplitPaymentsPage'
import TransactionsPage from './pages/TransactionsPage'
import ReceiptPage from './pages/ReceiptPage'
import ReconciliationPage from './pages/ReconciliationPage'
import IntegrationsPage from './pages/IntegrationsPage'
import JwtSessionPage from './pages/JwtSessionPage'
import QrPaymentSimulatorPage from './pages/QrPaymentSimulatorPage'

function ProtectedLayout({ onLogout }) {
  const token = localStorage.getItem('jwtToken')
  const location = useLocation()

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  const titles = {
    '/dashboard': 'Dashboard',
    '/create-payment': 'Create Payment',
    '/pay-by-link': 'Pay by Link',
    '/qr-payments': 'QR Payments',
    '/split-payments': 'Split Payments (Sector-Wise)',
    '/transactions': 'Transactions & History',
    '/receipt': 'Transaction Receipt',
    '/reconciliation': 'Reconciliation',
    '/integrations': 'Provider Integrations',
    '/jwt-session': 'JWT Session & Claims',
  }

  const pathname = location.pathname
  let currentTitle = 'SabbPe Merchant Portal'
  for (const [route, title] of Object.entries(titles)) {
    if (pathname.startsWith(route)) {
      currentTitle = title
      break
    }
  }

  return <Layout title={currentTitle} onLogout={onLogout} />
}

export default function App() {
  const [authKey, setAuthKey] = useState(0)

  const handleLogin = () => {
    setAuthKey((k) => k + 1)
  }

  const handleLogout = () => {
    localStorage.removeItem('jwtToken')
    setAuthKey((k) => k + 1)
  }

  return (
    <BrowserRouter>
      <Routes>
        {/* Public Routes */}
        <Route path="/login" element={<LoginPage onLogin={handleLogin} />} />
        <Route path="/qr-pay" element={<QrPaymentSimulatorPage />} />
        <Route path="/payment-result/:id" element={<ReceiptPage />} />
        <Route path="/payment-result" element={<ReceiptPage />} />

        {/* Protected Merchant Routes */}
        <Route element={<ProtectedLayout onLogout={handleLogout} />}>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/create-payment" element={<CreatePaymentPage />} />
          <Route path="/pay-by-link" element={<PayByLinkPage />} />
          <Route path="/qr-payments" element={<QrPaymentsPage />} />
          <Route path="/split-payments" element={<SplitPaymentsPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/receipt/:id" element={<ReceiptPage />} />
          <Route path="/reconciliation" element={<ReconciliationPage />} />
          <Route path="/integrations" element={<IntegrationsPage />} />
          <Route path="/jwt-session" element={<JwtSessionPage />} />
        </Route>

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
