import React from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function Layout({ title, onLogout }) {
  return (
    <div className="app-shell">
      <Sidebar onLogout={onLogout} />
      <main className="main-content">
        <Topbar title={title} onLogout={onLogout} />
        <div className="content-container">
          <Outlet />
        </div>
        <footer className="footer">
          <span>SabbPe · Enterprise Payment Ecosystem</span>
          <span>One78 SabbPe Technology Solutions India Pvt. Ltd. · UAT Environment (192.168.1.10)</span>
        </footer>
      </main>
    </div>
  )
}
