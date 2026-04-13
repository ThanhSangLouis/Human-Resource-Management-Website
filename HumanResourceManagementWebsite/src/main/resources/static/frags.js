/**
 * frags.js – Shared fragment loader for HRM System
 *
 * Each page only needs:
 *   <header class="topbar" id="topbar-placeholder"></header>
 *   <aside  class="sidebar" id="navbar-placeholder"></aside>
 *   <script src="/frags.js"></script>
 *
 * Provides global HRMFrags object for auth utilities.
 */
(function () {
  'use strict';

  /* ── Auth helpers ──────────────────────────────────────────────────────── */
  const TOKEN_KEY   = 'hrm_access_token';
  const USER_KEY    = 'hrm_user';
  const REFRESH_KEY = 'hrm_refresh_token';

  const HRMFrags = {
    getToken() { return localStorage.getItem(TOKEN_KEY); },

    getUser() {
      try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); }
      catch (_) { return null; }
    },

    async logout() {
      try {
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
      } catch (_) { /* ignore */ }
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(REFRESH_KEY);
      window.location.href = '/login';
    },

    redirectLogin() {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
      localStorage.removeItem(REFRESH_KEY);
      window.location.href = '/login';
    }
  };

  /* expose globally so inline onclick="HRMFrags.logout()" works */
  window.HRMFrags = HRMFrags;

  /* also keep legacy logout() alias used by older pages */
  if (typeof window.logout !== 'function') {
    window.logout = HRMFrags.logout;
  }

  /* ── Active-page detection ─────────────────────────────────────────────── */
  function resolveCurrentNav() {
    const path = window.location.pathname;
    const hash = window.location.hash;
    if (path.includes('profile')) return 'profile';
    if (path === '/overview' || path === '/overview.html' ||
        path === '/dashboard' || path === '/dashboard.html') return 'overview';
    if (path.includes('employees'))   return 'employees';
    if (path.includes('admin-users')) return 'admin-users';
    if (path.includes('departments')) return 'departments';
    if (path.includes('payroll'))     return 'payroll';
    if (path.includes('performance')) return 'performance';
    if (path.includes('worktime') || path.includes('attendance')) {
      return hash === '#leave' ? 'leave' : 'attendance';
    }
    if (path.includes('assistant')) return 'assistant';
    return '';
  }

  /* ── Fragment injection ─────────────────────────────────────────────────── */
  async function fetchFragment(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error('Fragment load failed: ' + url);
    return res.text();
  }

  async function injectTopbar(user, role) {
    const el = document.getElementById('topbar-placeholder');
    if (!el) return;
    try {
      el.innerHTML = await fetchFragment('/fragments/topbar.html');
    } catch (err) {
      console.error('[HRM] Không tải được /fragments/topbar.html:', err);
      el.innerHTML = '<div style="padding:12px 20px;color:#b91c1c;font-size:13px;">Lỗi tải menu trên. Hãy chạy lại server và Ctrl+F5. (' + (err && err.message ? err.message : err) + ')</div>';
      return;
    }

    /* Fill user info */
    const username = user ? (user.username || '–') : '–';
    const usernameEl = el.querySelector('#topbar-username');
    const avatarEl   = el.querySelector('#topbar-avatar');
    if (usernameEl) usernameEl.textContent = username + (role ? ' (' + role + ')' : '');
    if (avatarEl)   avatarEl.textContent   = username.charAt(0).toUpperCase();
  }

  async function injectNavbar(user, role) {
    const el = document.getElementById('navbar-placeholder');
    if (!el) return;
    try {
      el.innerHTML = await fetchFragment('/fragments/navbar.html');
    } catch (err) {
      console.error('[HRM] Không tải được /fragments/navbar.html:', err);
      el.innerHTML = '<div style="padding:12px 16px;color:#b91c1c;font-size:12px;">Lỗi tải menu bên.</div>';
      return;
    }

    const currentNav = resolveCurrentNav();

    function roleAllowed(allowedAttr, userRole) {
      if (!allowedAttr || !String(allowedAttr).trim()) return true;
      if (!userRole) return false;
      const list = String(allowedAttr).split(',').map(function (r) { return r.trim().toUpperCase(); });
      return list.indexOf(userRole) >= 0;
    }

    el.querySelectorAll('[data-nav]').forEach(function (link) {
      if (link.dataset.nav === currentNav) link.classList.add('active');
      const allowed = link.dataset.roles;
      if (allowed && !roleAllowed(allowed, role)) {
        link.style.display = 'none';
      }
    });

    const changePasswordLink = el.querySelector('[data-action="change-password"]');
    if (changePasswordLink) {
      changePasswordLink.addEventListener('click', function (event) {
        if (window.location.pathname.includes('profile') && typeof window._openPwdModal === 'function') {
          event.preventDefault();
          window._openPwdModal();
          return;
        }
        window.location.href = '/profile.html#change-password';
      });
    }
  }

  /** EMPLOYEE: không vào dashboard / phòng ban / nhân viên / lương / đánh giá (chỉ chấm công & nghỉ phép & hồ sơ). */
  function redirectIfPageForbidden(pathname, roleUpper) {
    if (!HRMFrags.getToken() || !roleUpper) return true;
    var p = (pathname || '').toLowerCase();
    if (p.indexOf('login') >= 0) return true;
    if (p.indexOf('payroll') >= 0 && ['ADMIN', 'HR'].indexOf(roleUpper) < 0) {
      window.location.replace('/worktime.html#attendance');
      return false;
    }
    if ((p.indexOf('/overview') >= 0 || p.indexOf('overview.html') >= 0 || p.indexOf('/dashboard') >= 0)
        && ['ADMIN', 'HR', 'MANAGER'].indexOf(roleUpper) < 0) {
      window.location.replace('/worktime.html#attendance');
      return false;
    }
    if ((p.indexOf('departments') >= 0 || p.indexOf('employees') >= 0 || p.indexOf('performance') >= 0)
        && ['ADMIN', 'HR', 'MANAGER'].indexOf(roleUpper) < 0) {
      window.location.replace('/worktime.html#attendance');
      return false;
    }
    if (p.indexOf('admin-users') >= 0 && roleUpper !== 'ADMIN') {
      window.location.replace('/worktime.html#attendance');
      return false;
    }
    return true;
  }

  /* ── Boot ───────────────────────────────────────────────────────────────── */
  document.addEventListener('DOMContentLoaded', async function () {
    const user = HRMFrags.getUser();
    const role = user ? String(user.role || '').trim().toUpperCase() : '';

    await Promise.all([
      injectTopbar(user, role),
      injectNavbar(user, role)
    ]);

    /* Dispatch event so pages can hook in after fragments are ready */
    document.dispatchEvent(new CustomEvent('hrm:frags-ready', { detail: { user, role } }));
  });
}());
