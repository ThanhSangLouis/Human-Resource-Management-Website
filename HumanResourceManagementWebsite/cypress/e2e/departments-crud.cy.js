/// <reference types="cypress" />

/**
 * CRUD Phòng ban — UI departments.html
 * Yêu cầu: tài khoản ADMIN hoặc HR (nút Sửa/Xóa chỉ hiện với role này).
 */
describe('Phòng ban — CRUD & danh sách', () => {
  beforeEach(() => {
    cy.loginByUi();
  });

  function uniqueDeptName() {
    return `Phòng Cypress ${Date.now()}${Math.floor(Math.random() * 1000)}`;
  }

  /** PB mới có thể không ở trang 1 (sort + phân trang) — lọc theo hậu tố số trong tên. */
  function findDepartmentRow(deptName) {
    const key = deptName.replace(/^Phòng Cypress /, '');
    cy.get('#search-input').clear().type(key);
    cy.wait(500);
    cy.get('#dept-tbody', { timeout: 15000 }).contains('tr', deptName).should('exist');
  }

  it('Read: tải danh sách, bảng và phân trang hiển thị', () => {
    cy.visit('/departments.html');
    cy.contains('.page-title', 'Danh sách phòng ban').should('be.visible');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');
    cy.get('#pagination-info').should('not.have.text', '–');
    cy.get('#dept-tbody tr').should('have.length.at.least', 1);
    cy.get('#search-input').should('be.visible');
    cy.contains('button', 'Tạo phòng ban').should('be.visible');
  });

  it('Create: mở modal, nhập tên + mô tả, lưu và thấy dòng mới', () => {
    const deptName = uniqueDeptName();
    cy.visit('/departments.html');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Tạo phòng ban').click();
    cy.get('#form-overlay').should('have.class', 'show');
    cy.get('#modal-title').should('contain', 'Tạo phòng ban');
    cy.get('#f-name').clear().type(deptName);
    cy.get('#f-desc').clear().type('Mô tả tạo bởi Cypress E2E');
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });

    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Tạo phòng ban thành công');
    findDepartmentRow(deptName);
    cy.get('#dept-tbody').contains('tr', deptName).should('be.visible');
  });

  it('Read + Search: lọc theo từ khóa tìm thấy phòng ban vừa tạo', () => {
    const deptName = uniqueDeptName();
    const token = deptName.replace(/^Phòng Cypress /, '');

    cy.visit('/departments.html');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Tạo phòng ban').click();
    cy.get('#f-name').clear().type(deptName);
    cy.get('#f-desc').clear().type('Cho test search');
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Tạo phòng ban thành công');

    cy.get('#search-input').clear().type(token);
    cy.wait(500);
    cy.get('#dept-tbody', { timeout: 15000 }).contains('tr', deptName).should('exist');
  });

  it('Update: sửa mô tả phòng ban', () => {
    const deptName = uniqueDeptName();
    cy.visit('/departments.html');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Tạo phòng ban').click();
    cy.get('#f-name').clear().type(deptName);
    cy.get('#f-desc').clear().type('Trước khi sửa');
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Tạo phòng ban thành công');
    findDepartmentRow(deptName);

    cy.get('#dept-tbody').contains('tr', deptName).find('button.edit').click();
    cy.get('#form-overlay').should('have.class', 'show');
    cy.get('#modal-title').should('contain', 'Chỉnh sửa');
    cy.get('#f-desc').clear().type('Đã cập nhật bởi Cypress');
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Cập nhật phòng ban thành công');
    findDepartmentRow(deptName);
    cy.get('#dept-tbody').contains('tr', deptName).should('contain', 'Đã cập nhật bởi Cypress');
  });

  it('Sort: bấm tiêu đề cột Tên phòng ban để đổi thứ tự', () => {
    cy.visit('/departments.html');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');
    cy.contains('th.sortable', 'Tên phòng ban').click();
    cy.get('#dept-tbody', { timeout: 15000 }).should('not.contain', 'Đang tải');
    cy.contains('th.sortable', 'Tên phòng ban').click();
    cy.get('#dept-tbody', { timeout: 15000 }).should('not.contain', 'Đang tải');
  });

  it('Delete: xóa phòng ban rỗng (vừa tạo, chưa gán nhân viên)', () => {
    const deptName = uniqueDeptName();
    cy.visit('/departments.html');
    cy.get('#dept-tbody', { timeout: 25000 }).should('not.contain', 'Đang tải');

    cy.contains('button', 'Tạo phòng ban').click();
    cy.get('#f-name').clear().type(deptName);
    cy.get('#f-desc').clear().type('Sẽ bị xóa');
    cy.get('#form-overlay.show').within(() => {
      cy.contains('button', 'Lưu').click();
    });
    cy.get('#form-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Tạo phòng ban thành công');
    findDepartmentRow(deptName);

    cy.get('#dept-tbody').contains('tr', deptName).find('button.del').click();
    cy.get('#confirm-overlay').should('have.class', 'show');
    cy.get('#confirm-dept-name').should('contain', deptName);
    cy.get('#confirm-overlay.show').within(() => {
      cy.contains('button', 'Xóa').click();
    });
    cy.get('#confirm-overlay').should('not.have.class', 'show');
    cy.expectToastContains('Xóa phòng ban thành công');
    cy.get('#dept-tbody', { timeout: 15000 }).should('not.contain', deptName);
  });
});
