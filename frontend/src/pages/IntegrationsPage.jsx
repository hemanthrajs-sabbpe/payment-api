import React from 'react'
import { ShieldCheck, Cpu, Building2, CheckCircle2 } from 'lucide-react'

export default function IntegrationsPage() {
  return (
    <div className="page-wrapper">
      <div className="card">
        <div className="card-header">
          <div>
            <h3>Connected Payment Providers</h3>
            <p className="card-subtitle">Active gateway routing and provider configurations for SabbPe UAT</p>
          </div>
          <Cpu size={24} className="text-primary" />
        </div>

        <div className="integrations-list" style={{ display: 'grid', gap: 16, marginTop: 16 }}>
          <div className="provider-integration-card">
            <div className="provider-icon-badge" style={{ background: '#eff6ff', color: '#2563eb' }}>
              <ShieldCheck size={28} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <h4 style={{ margin: 0, fontSize: 16 }}>SabbPe Payment Gateway (Dynamic QR)</h4>
                <span className="badge status-success">Active Provider</span>
              </div>
              <p style={{ margin: '0 0 8px', color: '#64748b', fontSize: 13 }}>
                Primary gateway providing dynamic QR code payments, split-allocation settlements, and webhook notifications.
              </p>
              <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#475569' }}>
                <span><strong>Merchant ID:</strong> SBPBLmer01</span>
                <span><strong>Endpoint:</strong> https://ecosystemuat.sabbpe.com</span>
                <span><strong>Webhook Target:</strong> /api/sabbpe/gateway/callback</span>
              </div>
            </div>
          </div>

          <div className="provider-integration-card">
            <div className="provider-icon-badge" style={{ background: '#f5f3ff', color: '#7c3aed' }}>
              <Building2 size={28} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <h4 style={{ margin: 0, fontSize: 16 }}>SabbPe Pay by Link (PBL Service)</h4>
                <span className="badge status-success">Active Provider</span>
              </div>
              <p style={{ margin: '0 0 8px', color: '#64748b', fontSize: 13 }}>
                Hosted payment link generator delivering customer checkout links and real-time status inquiry.
              </p>
              <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#475569' }}>
                <span><strong>Merchant Code:</strong> SBPBLmer01</span>
                <span><strong>Callback Forwarded:</strong> Enabled (1)</span>
                <span><strong>Auth:</strong> Bearer Token Verified</span>
              </div>
            </div>
          </div>

          <div className="provider-integration-card">
            <div className="provider-icon-badge" style={{ background: '#fff7ed', color: '#ea580c' }}>
              <Cpu size={28} />
            </div>
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                <h4 style={{ margin: 0, fontSize: 16 }}>Easebuzz Payment Gateway</h4>
                <span className="badge status-pending">Sandbox Connected</span>
              </div>
              <p style={{ margin: '0 0 8px', color: '#64748b', fontSize: 13 }}>
                Easebuzz sandbox integration for fallback checkout processing and response handling.
              </p>
              <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#475569' }}>
                <span><strong>Checkout Environment:</strong> testpay.easebuzz.in</span>
                <span><strong>Response Endpoint:</strong> /api/payments/easebuzz/response</span>
              </div>
            </div>
          </div>
        </div>

        <div className="alert-box alert-info" style={{ marginTop: 24 }}>
          <CheckCircle2 size={18} />
          <span>
            All provider transactions are recorded idempotently into UAT MariaDB <code>payment_db</code> and cross-linked
            with SabbPe ecosystem records.
          </span>
        </div>
      </div>
    </div>
  )
}
