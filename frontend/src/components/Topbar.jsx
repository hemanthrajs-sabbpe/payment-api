import React from 'react'
import { useNavigate } from 'react-router-dom'
import { ShieldCheck, UserCheck, Bell } from 'lucide-react'

export default function Topbar({ title, onLogout }) {
  const navigate = useNavigate()
  const token = localStorage.getItem('jwtToken')
  
  let username = 'hemanth'
  try {
    if (token) {
      const payload = JSON.parse(atob(token.split('.')[1]))
      username = payload.sub || payload.username || 'hemanth'
    }
  } catch (e) {
    // fallback
  }

  return (
    <header className="topbar">
      <div className="topbar-title-wrap">
        <h1 className="topbar-title">{title}</h1>
        <div className="topbar-badge">
          <ShieldCheck size={14} color="#10b981" />
          <span>SabbPe UAT Connected</span>
        </div>
      </div>

      <div className="topbar-actions">
        <div className="user-profile-badge">
          <UserCheck size={16} color="#6366f1" />
          <span className="user-name">{username}</span>
          <span className="user-role">MERCHANT ADMIN</span>
        </div>
      </div>
    </header>
  )
}
