/// <reference types="cypress" />

describe('Auth – trang đăng nhập', () => {
  beforeEach(() => {
    cy.clearLocalStorage();
    cy.visit('/login.html');
  });

  it('hiển thị form đăng nhập', () => {
    cy.contains('h1', 'HRM Login');
    cy.get('#username').should('be.visible');
    cy.get('#password').should('be.visible');
    cy.get('#btnLogin').should('be.visible');
  });

  it('từ chối mật khẩu sai', () => {
    cy.get('#username').clear().type('admin');
    cy.get('#password').clear().type('wrong-password-not-real', { log: false });
    cy.get('#btnLogin').click();
    cy.get('#status', { timeout: 10000 }).should('have.class', 'bad');
    // Repo mới: AuthExceptionHandler trả message "Invalid username or password";
    // nếu không có body JSON thì UI dùng "Đăng nhập thất bại (status)"; repo cũ: "Login failed (401)".
    cy.get('#status')
      .invoke('text')
      .should(
        'match',
        /invalid username or password|login failed|đăng nhập thất bại/i
      );
  });

  it('đăng nhập thành công, lưu token và chuyển tổng quan', () => {
    const u = Cypress.env('HRM_USERNAME') || 'admin';
    const p = Cypress.env('HRM_PASSWORD') || '123456';
    cy.get('#username').clear().type(u);
    cy.get('#password').clear().type(p, { log: false });
    cy.get('#btnLogin').click();
    cy.get('#status', { timeout: 10000 }).should('have.class', 'ok');
    cy.url({ timeout: 15000 }).should('satisfy', (href) => href.includes('/overview'));
    cy.window().its('localStorage.hrm_access_token').should('exist');
  });
});
