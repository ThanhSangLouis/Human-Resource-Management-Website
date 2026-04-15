/// <reference types="cypress" />

/**
 * Dashboard / Tổng quan — overview.html
 * Kiểm tra API GET /api/dashboard, KPI, biểu đồ, bảng phòng ban, điều khiển tháng.
 */
describe('Dashboard — báo cáo vận hành chi tiết', () => {
  beforeEach(() => {
    cy.loginByUi();
  });

  it('API dashboard trả 200 và có cấu trúc dữ liệu', () => {
    cy.intercept('GET', '**/api/dashboard**').as('dashboardApi');
    cy.visit('/overview');
    cy.wait('@dashboardApi', { timeout: 25000 }).then((interception) => {
      expect(interception.response.statusCode).to.eq(200);
      const body = interception.response.body;
      expect(body).to.have.property('totalEmployees');
      expect(body).to.have.property('totalDepartments');
      expect(body).to.have.property('byStatus');
      expect(body.byStatus).to.have.keys('ACTIVE', 'INACTIVE', 'RESIGNED');
      expect(body).to.have.property('attendanceMonth');
    });
  });

  it('Header trang: eyebrow, tiêu đề, chọn tháng, nút Làm mới', () => {
    cy.visit('/overview');
    cy.get('.page-eyebrow').should('contain', 'Tổng quan');
    cy.get('.page-heading').should('contain', 'Báo cáo vận hành');
    cy.get('#pageSubtitle', { timeout: 25000 }).should('not.have.text', 'Đang tải dữ liệu…');
    cy.get('#monthPicker').should('be.visible').invoke('val').should('match', /^\d{4}-\d{2}$/);
    cy.get('.btn-refresh').should('contain', 'Làm mới').should('be.enabled');
  });

  it('Bốn thẻ KPI: tổng NV, đang làm, tạm nghỉ, đã nghỉ việc', () => {
    cy.visit('/overview');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');
    cy.get('.kpi-grid').should('be.visible');
    cy.get('.kpi-grid .kpi-card').should('have.length', 4);
    cy.contains('.kpi-label', 'Tổng nhân viên').should('be.visible');
    cy.contains('.kpi-label', 'Đang làm việc').should('be.visible');
    cy.contains('.kpi-label', 'Tạm nghỉ').should('be.visible');
    cy.contains('.kpi-label', 'Đã nghỉ việc').should('be.visible');
    cy.get('.kpi-grid .kpi-value').eq(0).should('be.visible').invoke('text').should('match', /^\d+$/);
  });

  it('Hai panel biểu đồ: phân bổ nhân viên & chấm công', () => {
    cy.visit('/overview');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');
    cy.get('.mid-row .panel').should('have.length', 2);
    cy.contains('.panel-title', 'Phân bổ nhân viên').should('be.visible');
    cy.contains('.panel-title', 'Chấm công').should('be.visible');
    cy.get('#chartStatus').should('exist');
    cy.get('#chartAttendance').should('exist');
  });

  it('Panel chi tiết chấm công: các pill trạng thái & thanh tỷ lệ', () => {
    cy.visit('/overview');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');
    cy.contains('.panel-title', 'Chi tiết chấm công').should('be.visible');
    cy.get('.att-row').should('be.visible');
    cy.contains('.ap-lbl', 'Đúng giờ').should('be.visible');
    cy.contains('.ap-lbl', 'Đi muộn').should('be.visible');
    cy.contains('.ap-lbl', 'Nửa ngày').should('be.visible');
    cy.contains('.ap-lbl', 'Nghỉ phép').should('be.visible');
    cy.contains('.ap-lbl', 'Vắng mặt').should('be.visible');
    cy.get('.rate-bar-track').should('be.visible');
    cy.get('.rate-bar-fill').should('be.visible');
  });

  it('Bảng nhân viên theo phòng ban', () => {
    cy.visit('/overview');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');
    cy.contains('.panel-title', 'Nhân viên theo phòng ban').should('be.visible');
    cy.get('table.dept-table thead').within(() => {
      cy.contains('th', 'Phòng ban').should('exist');
      cy.contains('th', 'Số nhân viên').should('exist');
    });
    cy.get('#deptTableBody tr').should('have.length.at.least', 1);
  });

  it('Đổi tháng + Làm mới: gọi lại API dashboard', () => {
    cy.visit('/overview');
    cy.intercept('GET', '**/api/dashboard**').as('dashRefresh');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');

    cy.get('#monthPicker').invoke('val', '2025-03').trigger('change', { force: true });
    cy.get('.btn-refresh').contains('Làm mới').click();
    cy.wait('@dashRefresh', { timeout: 25000 });
    cy.get('#dashContent').should('not.contain', 'Đang tải dữ liệu…');
    cy.get('.kpi-grid').should('be.visible');
  });

  it('Không hiển thị banner lỗi khi tải thành công', () => {
    cy.visit('/overview');
    cy.get('#dashContent', { timeout: 25000 }).should('not.contain', 'Đang tải dữ liệu…');
    cy.get('#errorMsg').should('not.be.visible');
  });
});
