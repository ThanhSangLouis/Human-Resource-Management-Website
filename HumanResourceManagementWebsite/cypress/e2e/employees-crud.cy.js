/// <reference types="cypress" />

/**
 * CRUD Nhân viên — UI employees.html
 * "Xóa" trên UI là đánh dấu nghỉ việc (RESIGNED), không xóa cứng.
 * Nên dùng ADMIN (đủ quyền tạo/sửa + xuất Excel nếu cần mở rộng sau).
 *
 * Mọi thao tác form nằm trong #form-overlay.show + scrollIntoView để tránh
 * che phủ (viewport headless / layout modal).
 */
describe('Nhân viên — CRUD, tìm kiếm & lọc', () => {
  beforeEach(() => {
    cy.loginByUi();
  });

  function uniqueEmp() {
    const n = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
    return {
      code: `CYP${n}`,
      name: `Nhân viên Cypress ${n}`,
      email: `cypress.e2e.${n}@test.local`,
    };
  }

  /** Điền form trong modal nhân viên (đang mở). */
  function fillNewEmployee(e, opts = {}) {
    cy.get('#form-overlay.show').should('be.visible').within(() => {
      cy.get('#f-code').scrollIntoView().clear().type(e.code, { force: true });
      cy.get('#f-name').scrollIntoView().clear().type(e.name, { force: true });
      if (e.email) cy.get('#f-email').clear().type(e.email, { force: true });
      if (opts.position) cy.get('#f-position').clear().type(opts.position, { force: true });
      if (opts.hire) cy.get('#f-hire').clear().type(opts.hire, { force: true });
    });
    cy.get('#form-overlay.show #f-dept option').then(($opts) => {
      if ($opts.length > 1) {
        const v = $opts.eq(1).attr('value');
        if (v) cy.get('#form-overlay.show #f-dept').select(v);
      }
    });
  }

  function saveEmployeeModal() {
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });
  }

  /** Sau khi tạo, NV có thể không nằm trang 1 (sort theo tên) — lọc theo mã để chắc chắn thấy dòng. */
  function findEmployeeRowByCode(code) {
    cy.get('#search-input').clear().type(code);
    cy.wait(500);
    cy.get('#emp-tbody', { timeout: 15000 }).contains('tr', code).should('exist');
  }

  it('Read: tải danh sách, toolbar và bảng nhân viên', () => {
    cy.visit('/employees.html');
    cy.contains('.page-title', 'Danh sách nhân viên').should('be.visible');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');
    cy.get('#pagination-info').should('not.have.text', '–');
    cy.get('#search-input').should('be.visible');
    cy.get('#filter-status').should('be.visible');
    cy.get('#filter-dept').should('be.visible');
    cy.contains('button', 'Thêm nhân viên').should('be.visible');
    cy.get('#emp-tbody tr').should('have.length.at.least', 1);
  });

  it('Create: thêm nhân viên với mã + họ tên + email', () => {
    const e = uniqueEmp();
    cy.visit('/employees.html');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Thêm nhân viên').click();
    cy.get('#form-overlay').should('have.class', 'show');
    cy.get('#modal-title').should('contain', 'Thêm nhân viên');
    fillNewEmployee(e, { position: 'Tester E2E', hire: '2022-06-01' });
    saveEmployeeModal();

    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Thêm nhân viên thành công');
    findEmployeeRowByCode(e.code);
    cy.get('#emp-tbody').contains('tr', e.code).should('contain', e.name);
  });

  it('Update: chỉnh sửa chức vụ / họ tên', () => {
    const e = uniqueEmp();
    cy.visit('/employees.html');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Thêm nhân viên').click();
    fillNewEmployee(e, { position: 'Dev' });
    saveEmployeeModal();
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Thêm nhân viên thành công');
    findEmployeeRowByCode(e.code);

    cy.get('#emp-tbody').contains('tr', e.code).find('button.edit').click();
    cy.get('#form-overlay').should('have.class', 'show');
    cy.get('#modal-title').should('contain', 'Chỉnh sửa');
    cy.get('#form-overlay.show').within(() => {
      cy.get('#f-name').scrollIntoView().clear().type(`${e.name} (đã sửa)`, { force: true });
      cy.get('#f-position').scrollIntoView().clear().type('Senior Tester', { force: true });
    });
    saveEmployeeModal();
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Cập nhật nhân viên thành công');
    findEmployeeRowByCode(e.code);
    cy.get('#emp-tbody').contains('tr', e.code).should('contain', 'đã sửa');
  });

  it('Search: ô tìm kiếm lọc theo mã hoặc tên', () => {
    const e = uniqueEmp();
    cy.visit('/employees.html');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Thêm nhân viên').click();
    fillNewEmployee(e);
    saveEmployeeModal();
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Thêm nhân viên thành công');

    cy.get('#search-input').clear().type(e.code);
    cy.wait(500);
    cy.get('#emp-tbody', { timeout: 15000 }).contains('tr', e.code).should('exist');
  });

  it('Filter: lọc theo trạng thái sau khi đánh dấu nghỉ việc', () => {
    const e = uniqueEmp();
    cy.visit('/employees.html');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Thêm nhân viên').click();
    fillNewEmployee(e);
    saveEmployeeModal();
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Thêm nhân viên thành công');
    findEmployeeRowByCode(e.code);

    cy.get('#emp-tbody').contains('tr', e.code).find('button.del').click();
    cy.get('#confirm-overlay').should('have.class', 'show');
    cy.get('#confirm-emp-name').should('contain', e.name);
    cy.get('#confirm-overlay.show').within(() => {
      cy.contains('button', 'Xác nhận').click();
    });
    cy.get('#confirm-overlay').should('not.have.class', 'show');
    cy.expectToastContains('đánh dấu nghỉ việc');

    cy.get('#filter-status').select('RESIGNED');
    cy.wait(400);
    findEmployeeRowByCode(e.code);
    cy.get('#emp-tbody').contains('tr', e.code).should('contain', 'Đã nghỉ việc');
  });

  it('Sort: bấm tiêu đề cột Họ tên để đổi thứ tự', () => {
    cy.visit('/employees.html');
    cy.get('#emp-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');
    cy.contains('th.sortable', 'Họ tên').click();
    cy.get('#emp-tbody', { timeout: 15000 }).should('not.contain', 'Đang tải');
    cy.contains('th.sortable', 'Họ tên').click();
    cy.get('#emp-tbody', { timeout: 15000 }).should('not.contain', 'Đang tải');
  });
});
