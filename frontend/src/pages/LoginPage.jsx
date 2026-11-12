import React, { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { loginUser } from '../api'
import { ShieldCheck, ArrowRight, Lock, User, Eye, EyeOff } from 'lucide-react'

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('hemanth')
  const [password, setPassword] = useState('Hemanth@123')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const navigate = useNavigate()

  async function submit(event) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      await loginUser(username.trim(), password)
      if (onLogin) onLogin()
      navigate('/dashboard')
    } catch (err) {
      setError(err.message || 'Unable to sign in. Check your credentials and try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="login-page-container">
      <section className="login-hero-section">
        <div className="login-brand-lockup">
          <div className="login-official-logo-frame">
            <img
              src="https://www.sabbpe.com/videos/sabbpe_logo.png"
              alt="SabbPe Logo"
              onError={(e) => {
                e.currentTarget.style.display = 'none'
                const fb = e.currentTarget.parentElement?.querySelector('.login-logo-fallback')
                if (fb) fb.style.display = 'block'
              }}
            />
            <span className="login-logo-fallback" style={{ display: 'none', fontWeight: 900, fontSize: 24 }}>SabbPe</span>
          </div>
          <div className="login-project-badge">UAT ENVIRONMENT</div>
        </div>

        <div className="login-hero-copy">
          <div className="login-eyebrow">ENTERPRISE PAYMENT ECOSYSTEM</div>
          <h1>SabbPe Merchant Portal</h1>
          <p>
            Unified dashboard for Instant Collections, Real-time Dynamic QR, Sector-Specific Split Settlements,
            and Automated Webhook Reconciliations.
          </p>
          <div className="login-feature-pills">
            <span><ShieldCheck size={14} style={{ display: 'inline', verticalAlign: -2 }} /> Bank-Grade Security</span>
            <span>Easebuzz & SabbPe Gateways</span>
            <span>12 Industry Sectors</span>
            <span>UAT MariaDB</span>
          </div>
        </div>

        <div className="login-company-footer">
          <strong>One78 SabbPe Technology Solutions India Pvt. Ltd.</strong>
          <br />Secure Merchant Access · UAT Server 192.168.1.10
        </div>
      </section>

      <section className="login-form-section">
        <form onSubmit={submit} className="login-card">
          <div className="login-card-header">
            <span className="login-subtitle">MERCHANT PORTAL LOGIN</span>
            <h2>Sign In</h2>
            <p>Access your SabbPe merchant terminal and transactions.</p>
          </div>

          <div className="form-group">
            <label htmlFor="login-username">Merchant Username</label>
            <div className="input-with-icon">
              <User size={18} className="input-icon" />
              <input
                id="login-username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                required
                disabled={loading}
                placeholder="Enter username"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="login-password">Password</label>
            <div className="input-with-icon">
              <Lock size={18} className="input-icon" />
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                required
                disabled={loading}
                placeholder="Enter password"
              />
              <button
                type="button"
                className="toggle-password"
                onClick={() => setShowPassword((v) => !v)}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
          </div>

          {error && <div className="alert-error" role="alert">{error}</div>}

          <button type="submit" disabled={loading} className="login-submit-button">
            {loading ? 'Authenticating...' : (
              <>
                <span>Sign In to Terminal</span>
                <ArrowRight size={18} />
              </>
            )}
          </button>

          <div className="login-demo-hint">
            <span>Configured for SabbPe UAT merchant account <strong>hemanth</strong>.</span>
          </div>
        </form>
      </section>
    </main>
  )
}
