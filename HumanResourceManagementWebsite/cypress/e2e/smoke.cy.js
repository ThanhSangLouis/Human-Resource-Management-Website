/// <reference types="cypress" />

describe('Smoke – sau đăng nhập', () => {
  beforeEach(() => {
    cy.loginByUi();
  });

  it('trang Tổng quan hiển thị tiêu đề và vùng dashboard', () => {
    cy.visit('/overview');
    cy.title().should('include', 'Tổng quan');
    cy.get('.page-heading').should('contain', 'Báo cáo vận hành');
    cy.get('#monthPicker').should('exist');
    cy.get('#dashContent', { timeout: 20000 }).should('be.visible');
  });

  it('nút Làm mới vẫn tương tác được', () => {
    cy.visit('/overview');
    cy.get('.btn-refresh').contains('Làm mới').click();
    cy.get('#dashContent').should('be.visible');
  });
});
