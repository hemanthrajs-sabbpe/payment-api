import React from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard,
  PlusCircle,
  Link2,
  QrCode,
  GitFork,
  History,
  FileCheck2,
  Cpu,
  KeyRound,
  LogOut,
  Layers
} from 'lucide-react'

export const navigationItems = [
  {
    section: 'MAIN MENU',
    items: [
      { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
      { path: '/create-payment', label: 'Create Payment', icon: PlusCircle },
      { path: '/pay-by-link', label: 'Pay by Link', icon: Link2 },
      { path: '/qr-payments', label: 'QR Payments', icon: QrCode },
      { path: '/split-payments', label: 'Split Payments', icon: GitFork },
      { path: '/transactions', label: 'Transactions & History', icon: History },
      { path: '/reconciliation', label: 'Reconciliation', icon: FileCheck2 },
    ],
  },
  {
    section: 'ECOSYSTEM & CONFIG',
    items: [
      { path: '/integrations', label: 'Provider Integrations', icon: Cpu },
      { path: '/jwt-session', label: 'JWT Session & Claims', icon: KeyRound },
    ],
  },
]

export default function Sidebar({ onLogout }) {
  const navigate = useNavigate()

  return (
    <aside className="sidebar">
      <div className="brand" onClick={() => navigate('/dashboard')} style={{ cursor: 'pointer' }}>
        <div className="brand-icon">
          <Layers size={22} color="#ffffff" />
        </div>
        <div className="brand-copy">
          <h2>SabbPe</h2>
          <span>Payment Platform</span>
        </div>
      </div>

      <div className="sidebar-scrollable">
        {navigationItems.map((group) => (
          <div key={group.section} className="nav-group">
            <p className="nav-label">{group.section}</p>
            <nav className="nav-list">
              {group.items.map((item) => {
                const Icon = item.icon
                return (
                  <NavLink
                    key={item.path}
                    to={item.path}
                    className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
                  >
                    <Icon size={18} className="nav-icon" />
                    <span>{item.label}</span>
                  </NavLink>
                )
              })}
            </nav>
          </div>
        ))}
      </div>

      <div className="sidebar-footer">
        <button
          type="button"
          className="logout-button"
          onClick={() => {
            if (onLogout) onLogout()
            else {
              localStorage.removeItem('jwtToken')
              navigate('/login')
            }
          }}
        >
          <LogOut size={16} />
          <span>Sign Out</span>
        </button>
      </div>
    </aside>
  )
}
