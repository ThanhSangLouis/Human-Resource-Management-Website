// https://on.cypress.io/configuration

/**
 * Sau mỗi test PASS: chụp viewport vào cypress/screenshots/bao-cao/<tên-spec>/<tên-test>.png
 * để dán vào báo cáo (Word thường chỉ nhận ảnh, không nhúng video).
 * Test fail: Cypress vẫn tự chụp vào screenshots/... (failed).png (screenshotOnRunFailure).
 */
function safeScreenshotSegment(s) {
  return String(s)
    .replace(/[\\/:*?"<>|]/g, '_')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

afterEach(function () {
  if (this.currentTest?.state !== 'passed') {
    return;
  }
  const specBase = (Cypress.spec?.name || 'spec')
    .replace(/^.*[/\\]/, '')
    .replace(/\.cy\.js$/i, '')
    .replace(/\.js$/i, '');
  const label =
    (typeof this.currentTest?.fullTitle === 'function' && this.currentTest.fullTitle()) ||
    this.currentTest?.title ||
    'test';
  const fileStem = safeScreenshotSegment(label);
  cy.screenshot(`bao-cao/${specBase}/${fileStem}`, { capture: 'viewport' });
});

const defaultUser = Cypress.env('HRM_USERNAME') || 'admin';
const defaultPass = Cypress.env('HRM_PASSWORD') || '123456';

/**
 * Đăng nhập qua UI và cache session (cookie + localStorage).
 * Các spec CRUD phòng ban / nhân viên cần role ADMIN hoặc HR (mặc định: admin).
 */
Cypress.Commands.add('loginByUi', (username = defaultUser, password = defaultPass) => {
  cy.session(['hrm-ui', username], () => {
    cy.visit('/login.html');
    cy.get('#username').clear();
    cy.get('#username').type(username);
    cy.get('#password').clear();
    cy.get('#password').type(password, { log: false });
    cy.get('#btnLogin').click();
    cy.url({ timeout: 15000 }).should('satisfy', (href) =>
      href.includes('/overview')
    );
    cy.window().then((win) => {
      expect(win.localStorage.getItem('hrm_access_token'), 'JWT saved').to.be.a('string').and.not.be.empty;
    });
  });
});

/** Toast thành công / lỗi ở góc màn hình (#toast-container). */
Cypress.Commands.add('expectToastContains', (text) => {
  cy.get('#toast-container', { timeout: 10000 }).should('contain', text);
});
