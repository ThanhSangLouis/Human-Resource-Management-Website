-- ============================================================
--  HRM SEED DATA v2 — An toàn, chạy được nhiều lần
--  INSERT IGNORE: bỏ qua nếu bản ghi đã tồn tại
--  Dùng subquery theo email thay vì hardcode ID
--
--  Thứ tự khuyến nghị (local):
--    1) hrm_db.sql
--    2) seed_data.sql   (file này — gồm đánh giá hiệu suất BƯỚC 5)
--    3) hrm_db_overview_data_update.sql  — chấm công demo Overview + cùng block đánh giá (INSERT IGNORE)
-- ============================================================

USE hrm_db;

-- ============================================================
-- BƯỚC 1: Thêm nhân viên (bỏ qua nếu employee_code đã có)
-- ============================================================
INSERT IGNORE INTO employees
    (employee_code, full_name, email, phone, gender, date_of_birth,
     `position`, department_id, salary_base, hire_date, status)
VALUES
    -- ── IT (dept 1) — thêm 3 nhân viên ──
    ('EMP006', 'Nguyen Thi Lan',   'lan.nguyen@hrm.com',    '0906111111', 'FEMALE', '1999-04-10', 'Frontend Developer',     1, 18000000.00, '2023-02-01', 'ACTIVE'),
    ('EMP007', 'Tran Van Duc',     'duc.tran@hrm.com',      '0907111111', 'MALE',   '1998-11-25', 'Backend Developer',      1, 19000000.00, '2022-09-15', 'ACTIVE'),
    ('EMP008', 'Pham Thi Hoa',     'hoa.pham@hrm.com',      '0908111111', 'FEMALE', '2000-06-18', 'QA Engineer',            1, 16000000.00, '2024-03-01', 'ACTIVE'),

    -- ── HR (dept 2) — thêm 3 nhân viên ──
    ('EMP009', 'Le Thi Mai',       'mai.le@hrm.com',        '0909111111', 'FEMALE', '1997-03-22', 'HR Recruiter',           2, 14000000.00, '2023-05-01', 'ACTIVE'),
    ('EMP010', 'Vo Van Hung',      'hung.vo@hrm.com',       '0910111111', 'MALE',   '1996-07-14', 'Payroll Specialist',     2, 15000000.00, '2022-11-01', 'ACTIVE'),
    ('EMP011', 'Dang Thi Thu',     'thu.dang@hrm.com',      '0911111111', 'FEMALE', '2001-01-30', 'HR Assistant',           2, 12000000.00, '2024-06-01', 'ACTIVE'),

    -- ── Finance (dept 3) — manager + 3 nhân viên ──
    ('EMP012', 'Hoang Van Minh',   'minh.hoang@hrm.com',    '0912111111', 'MALE',   '1990-09-05', 'Finance Manager',        3, 28000000.00, '2020-04-01', 'ACTIVE'),
    ('EMP013', 'Nguyen Thi Bich',  'bich.nguyen@hrm.com',   '0913111111', 'FEMALE', '1995-12-20', 'Senior Accountant',      3, 20000000.00, '2021-08-01', 'ACTIVE'),
    ('EMP014', 'Tran Van Khanh',   'khanh.tran@hrm.com',    '0914111111', 'MALE',   '1998-05-08', 'Accountant',             3, 17000000.00, '2023-01-15', 'ACTIVE'),
    ('EMP015', 'Ly Thi Kim',       'kim.ly@hrm.com',        '0915111111', 'FEMALE', '2000-08-25', 'Finance Analyst',        3, 16000000.00, '2024-02-01', 'ACTIVE'),

    -- ── Marketing (dept 4) — manager + 3 nhân viên ──
    ('EMP016', 'Phan Van Long',    'long.phan@hrm.com',     '0916111111', 'MALE',   '1992-02-14', 'Marketing Manager',      4, 27000000.00, '2019-07-01', 'ACTIVE'),
    ('EMP017', 'Nguyen Thi Ngoc',  'ngoc.nguyen@hrm.com',   '0917111111', 'FEMALE', '1997-10-11', 'Digital Marketing Spec', 4, 16000000.00, '2022-03-01', 'ACTIVE'),
    ('EMP018', 'Bui Van Tuan',     'tuan.bui@hrm.com',      '0918111111', 'MALE',   '1999-07-19', 'Content Creator',        4, 15000000.00, '2023-04-01', 'ACTIVE'),
    ('EMP019', 'Dinh Thi Yen',     'yen.dinh@hrm.com',      '0919111111', 'FEMALE', '2001-03-27', 'Marketing Assistant',    4, 13000000.00, '2024-01-15', 'ACTIVE');

-- ============================================================
-- BƯỚC 2: Gán manager cho Finance và Marketing
-- ============================================================
UPDATE departments
SET manager_id = (SELECT id FROM employees WHERE email = 'minh.hoang@hrm.com')
WHERE name = 'Finance';

UPDATE departments
SET manager_id = (SELECT id FROM employees WHERE email = 'long.phan@hrm.com')
WHERE name = 'Marketing';

-- ============================================================
-- BƯỚC 3: Tạo user accounts (bỏ qua nếu username đã tồn tại)
--   Password = 123456 (BCrypt)
-- ============================================================
INSERT IGNORE INTO users (username, password, role, employee_id, is_active)
VALUES
    ('lan.nguyen@hrm.com',  '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'lan.nguyen@hrm.com'), TRUE),

    ('duc.tran@hrm.com',    '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'duc.tran@hrm.com'), TRUE),

    ('hoa.pham@hrm.com',    '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'hoa.pham@hrm.com'), TRUE),

    ('mai.le@hrm.com',      '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'mai.le@hrm.com'), TRUE),

    ('hung.vo@hrm.com',     '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'hung.vo@hrm.com'), TRUE),

    ('thu.dang@hrm.com',    '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'thu.dang@hrm.com'), TRUE),

    ('minh.hoang@hrm.com',  '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'MANAGER',
        (SELECT id FROM employees WHERE email = 'minh.hoang@hrm.com'), TRUE),

    ('bich.nguyen@hrm.com', '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'bich.nguyen@hrm.com'), TRUE),

    ('khanh.tran@hrm.com',  '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'khanh.tran@hrm.com'), TRUE),

    ('kim.ly@hrm.com',      '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'kim.ly@hrm.com'), TRUE),

    ('long.phan@hrm.com',   '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'MANAGER',
        (SELECT id FROM employees WHERE email = 'long.phan@hrm.com'), TRUE),

    ('ngoc.nguyen@hrm.com', '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'ngoc.nguyen@hrm.com'), TRUE),

    ('tuan.bui@hrm.com',    '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'tuan.bui@hrm.com'), TRUE),

    ('yen.dinh@hrm.com',    '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE',
        (SELECT id FROM employees WHERE email = 'yen.dinh@hrm.com'), TRUE);

-- ============================================================
-- BƯỚC 4: Salary history tháng 3/2025 (bỏ qua nếu đã có)
-- ============================================================
INSERT IGNORE INTO salary_history
    (employee_id, salary_base, bonus, tax, insurance, other_deduction, final_salary, salary_month, payment_status)
VALUES
    ((SELECT id FROM employees WHERE email='lan.nguyen@hrm.com'),  18000000, 500000, 1800000, 900000, 0, 15800000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='duc.tran@hrm.com'),    19000000, 700000, 1900000, 950000, 0, 16850000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='hoa.pham@hrm.com'),    16000000, 300000, 1600000, 800000, 0, 13900000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='mai.le@hrm.com'),      14000000, 200000, 1400000, 700000, 0, 12100000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='hung.vo@hrm.com'),     15000000, 400000, 1500000, 750000, 0, 13150000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='thu.dang@hrm.com'),    12000000, 100000, 1200000, 600000, 0, 10300000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='minh.hoang@hrm.com'),  28000000,2000000, 2800000,1400000, 0, 25800000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='bich.nguyen@hrm.com'), 20000000, 800000, 2000000,1000000, 0, 17800000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='khanh.tran@hrm.com'),  17000000, 500000, 1700000, 850000, 0, 14950000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='kim.ly@hrm.com'),      16000000, 300000, 1600000, 800000, 0, 13900000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='long.phan@hrm.com'),   27000000,1800000, 2700000,1350000, 0, 24750000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='ngoc.nguyen@hrm.com'), 16000000, 600000, 1600000, 800000, 0, 14200000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='tuan.bui@hrm.com'),    15000000, 400000, 1500000, 750000, 0, 13150000, '2025-03-01', 'PAID'),
    ((SELECT id FROM employees WHERE email='yen.dinh@hrm.com'),    13000000, 200000, 1300000, 650000, 0, 11250000, '2025-03-01', 'PAID');

-- ============================================================
-- BƯỚC 5: Đánh giá hiệu suất (năm 2026 — khớp Overview / attendance demo)
--   INSERT IGNORE: trùng (employee_id, review_year, review_quarter) thì bỏ qua
--   reviewer_id → users (admin, hr1, manager1, hoặc email trưởng phòng seed)
-- ============================================================
INSERT IGNORE INTO performance_reviews
    (employee_id, reviewer_id, review_year, review_quarter, score, review_comment, status, review_date)
VALUES
    ((SELECT id FROM employees WHERE email = 'lan.nguyen@hrm.com'),   (SELECT id FROM users WHERE username = 'manager1'),          2026, 1, 86, 'Chủ động, giao tiếp tốt với team.', 'APPROVED', '2026-03-28'),
    ((SELECT id FROM employees WHERE email = 'duc.tran@hrm.com'),   (SELECT id FROM users WHERE username = 'manager1'),          2026, 1, 91, 'Kỹ năng backend vượt kỳ vọng.', 'APPROVED', '2026-03-28'),
    ((SELECT id FROM employees WHERE email = 'hoa.pham@hrm.com'),    (SELECT id FROM users WHERE username = 'manager1'),          2026, 1, 84, 'Test coverage cải thiện rõ rệt.', 'SUBMITTED', '2026-03-30'),
    ((SELECT id FROM employees WHERE email = 'mai.le@hrm.com'),      (SELECT id FROM users WHERE username = 'hr1'),               2026, 1, 88, 'Tuyển dụng đủ chỉ tiêu Q1.', 'APPROVED', '2026-03-25'),
    ((SELECT id FROM employees WHERE email = 'hung.vo@hrm.com'),     (SELECT id FROM users WHERE username = 'hr1'),               2026, 1, 87, 'Bảng lương chính xác, đúng hạn.', 'APPROVED', '2026-03-25'),
    ((SELECT id FROM employees WHERE email = 'thu.dang@hrm.com'),    (SELECT id FROM users WHERE username = 'hr1'),               2026, 1, 79, 'Cần cải thiện tốc độ phản hồi ticket.', 'APPROVED', '2026-03-25'),
    ((SELECT id FROM employees WHERE email = 'minh.hoang@hrm.com'),  (SELECT id FROM users WHERE username = 'admin'),             2026, 1, 90, 'Lãnh đạo ổn định báo cáo tài chính.', 'APPROVED', '2026-03-20'),
    ((SELECT id FROM employees WHERE email = 'bich.nguyen@hrm.com'), (SELECT id FROM users WHERE username = 'minh.hoang@hrm.com'), 2026, 1, 85, 'Báo cáo quyết toán chất lượng.', 'APPROVED', '2026-03-20'),
    ((SELECT id FROM employees WHERE email = 'khanh.tran@hrm.com'),  (SELECT id FROM users WHERE username = 'minh.hoang@hrm.com'), 2026, 1, 82, 'Cần rút ngắn thời gian đối chiếu.', 'SUBMITTED', '2026-03-22'),
    ((SELECT id FROM employees WHERE email = 'kim.ly@hrm.com'),      (SELECT id FROM users WHERE username = 'minh.hoang@hrm.com'), 2026, 1, 80, 'Phân tích KPI chi tiết.', 'APPROVED', '2026-03-20'),
    ((SELECT id FROM employees WHERE email = 'long.phan@hrm.com'),   (SELECT id FROM users WHERE username = 'admin'),             2026, 1, 89, 'Chiến dịch Q1 đạt chỉ tiêu doanh số.', 'APPROVED', '2026-03-18'),
    ((SELECT id FROM employees WHERE email = 'ngoc.nguyen@hrm.com'), (SELECT id FROM users WHERE username = 'long.phan@hrm.com'), 2026, 1, 83, 'Content social ổn định.', 'APPROVED', '2026-03-18'),
    ((SELECT id FROM employees WHERE email = 'tuan.bui@hrm.com'),    (SELECT id FROM users WHERE username = 'long.phan@hrm.com'), 2026, 1, 86, 'Video viral đạt reach tốt.', 'APPROVED', '2026-03-18'),
    ((SELECT id FROM employees WHERE email = 'yen.dinh@hrm.com'),    (SELECT id FROM users WHERE username = 'long.phan@hrm.com'), 2026, 1, 78, 'Khởi đầu tốt, cần học thêm analytics.', 'APPROVED', '2026-04-01'),
    ((SELECT id FROM employees WHERE email = 'duc.tran@hrm.com'),    (SELECT id FROM users WHERE username = 'manager1'),          2026, 2, NULL, 'Nháp đánh giá Q2 — chờ buổi họp mid-year.', 'DRAFT', NULL),
    ((SELECT id FROM employees WHERE email = 'mai.le@hrm.com'),      (SELECT id FROM users WHERE username = 'hr1'),               2026, 2, NULL, 'Nháp đánh giá Q2.', 'DRAFT', NULL),
    ((SELECT id FROM employees WHERE email = 'yen.dinh@hrm.com'),    (SELECT id FROM users WHERE username = 'long.phan@hrm.com'), 2026, 2, NULL, 'Nháp KPI Q2.', 'DRAFT', NULL);

-- ============================================================
-- VERIFY: Kết quả mỗi phòng ban
-- ============================================================
SELECT
    d.id,
    d.name                        AS phong_ban,
    mgr.full_name                 AS truong_phong,
    mgr.`position`                AS chuc_vu_truong_phong,
    COUNT(e.id)                   AS tong_nhan_vien
FROM departments d
LEFT JOIN employees mgr ON mgr.id = d.manager_id
LEFT JOIN employees e   ON e.department_id = d.id AND e.status = 'ACTIVE'
GROUP BY d.id, d.name, mgr.full_name, mgr.`position`
ORDER BY d.id;
