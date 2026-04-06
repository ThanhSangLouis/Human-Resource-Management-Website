-- ============================================================
--  HRM DATABASE SCHEMA v2.0
--  Human Resource Management System
--  Compatible with: MySQL 8.0+
--  Tool: MySQL Workbench
-- ============================================================
--
--  CHANGELOG (so với schema v1.0):
--  [departments]       + manager_id FK, + updated_at
--  [employees]         + position/job_title, avatar_url -> VARCHAR(500), + updated_at
--  [users]             + is_active, + last_login, + updated_at, password -> BCrypt
--  [refresh_tokens]    NEW TABLE - JWT refresh token management
--  [leave_requests]    NEW TABLE - Quản lý nghỉ phép
--  [attendance]        + overtime_hours, + note, + ON_LEAVE status, + updated_at
--  [performance_reviews] + status workflow, + review_year/quarter thay review_period,
--                        + UNIQUE per employee/period, + CHECattendanceK score constraint, + updated_at
--  [salary_history]    + tax, + insurance, + other_deduction, + payment_status,
--                        + note, + updated_at, + UNIQUE(employee_id, salary_month) CRITICAL FIX
--  [notifications]     + employee_id FK, + notification_type, + is_read
--  [indexes]           Bổ sung đầy đủ index cho Search & Pagination
-- ============================================================

-- Xóa DB cũ nếu có và tạo mới
DROP DATABASE IF EXISTS hrm_db;
CREATE DATABASE hrm_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE hrm_db;

-- ============================================================
-- 1. TABLE: departments
--    Tạo trước employees để employees.department_id có thể FK
--    manager_id sẽ thêm FK sau khi employees tồn tại (circular dependency)
-- ============================================================
CREATE TABLE departments (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    manager_id  BIGINT       DEFAULT NULL,   -- FK added later (after employees)
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. TABLE: employees
-- ============================================================
CREATE TABLE employees (
    id            BIGINT          AUTO_INCREMENT PRIMARY KEY,
    employee_code VARCHAR(50)     UNIQUE,
    full_name     VARCHAR(150)    NOT NULL,
    email         VARCHAR(150)    UNIQUE,
    phone         VARCHAR(50),

    gender        ENUM('MALE','FEMALE','OTHER'),
    date_of_birth DATE,

    -- Tăng từ 255 -> 500 để chứa URL cloud (S3/Cloudinary/Firebase)
    avatar_url    VARCHAR(500),

    -- Chức danh / vị trí công việc
    position      VARCHAR(100),

    department_id BIGINT,
    salary_base   DECIMAL(12,2),

    status        ENUM('ACTIVE','INACTIVE','RESIGNED') DEFAULT 'ACTIVE',
    hire_date     DATE,

    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_emp_dept
        FOREIGN KEY (department_id) REFERENCES departments(id)
        ON DELETE SET NULL
);

-- Thêm FK manager_id vào departments (sau khi employees đã tồn tại)
ALTER TABLE departments
    ADD CONSTRAINT fk_dept_manager
        FOREIGN KEY (manager_id) REFERENCES employees(id)
        ON DELETE SET NULL;

-- ============================================================
-- 3. TABLE: users
--    Tài khoản đăng nhập, liên kết 1-1 với employees
--    password: PHẢI là BCrypt hash, KHÔNG lưu plain text
-- ============================================================
CREATE TABLE users (
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100) UNIQUE NOT NULL,

    -- BCrypt hash (60 ký tự), KHÔNG BAO GIỜ lưu plain text
    password    VARCHAR(255) NOT NULL,

    role        ENUM('ADMIN','HR','MANAGER','EMPLOYEE') NOT NULL,

    employee_id BIGINT       UNIQUE,
    is_active   BOOLEAN      DEFAULT TRUE,
    last_login  DATETIME     DEFAULT NULL,

    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_user_employee
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE CASCADE
);

-- ============================================================
-- 4. TABLE: refresh_tokens
--    Lưu JWT refresh token để hỗ trợ logout và token rotation
-- ============================================================
CREATE TABLE refresh_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at DATETIME     NOT NULL,
    created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE
);

-- ============================================================
-- 5. TABLE: leave_requests
--    Quản lý nghỉ phép - cần thiết cho Attendance đầy đủ và Dashboard
-- ============================================================
CREATE TABLE leave_requests (
    id          BIGINT   AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT   NOT NULL,

    leave_type  ENUM('ANNUAL','SICK','UNPAID','MATERNITY','OTHER') NOT NULL,
    start_date  DATE     NOT NULL,
    end_date    DATE     NOT NULL,
    total_days  INT      DEFAULT 1,
    reason      TEXT,

    status      ENUM('PENDING','APPROVED','REJECTED') DEFAULT 'PENDING',
    approved_by BIGINT   DEFAULT NULL,
    approved_at DATETIME DEFAULT NULL,

    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_leave_emp
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_leave_approver
        FOREIGN KEY (approved_by) REFERENCES users(id)
        ON DELETE SET NULL
);

-- ============================================================
-- 6. TABLE: attendance
--    Chấm công hàng ngày
-- ============================================================
CREATE TABLE attendance (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    employee_id     BIGINT        NOT NULL,

    check_in        DATETIME,
    check_out       DATETIME,
    work_hours      DECIMAL(5,2)  DEFAULT 0.00,
    overtime_hours  DECIMAL(5,2)  DEFAULT 0.00,

    attendance_date DATE          NOT NULL,

    -- ON_LEAVE: nghỉ có phép (liên kết với leave_requests)
    status          ENUM('PRESENT','LATE','ABSENT','HALF_DAY','ON_LEAVE') NOT NULL,
    note            TEXT,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_att_emp
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE CASCADE,

    -- Mỗi nhân viên chỉ có 1 bản ghi chấm công mỗi ngày
    UNIQUE KEY uq_employee_date (employee_id, attendance_date)
);

-- ============================================================
-- 7. TABLE: performance_reviews
--    Đánh giá hiệu suất theo quý
-- ============================================================
CREATE TABLE performance_reviews (
    id             BIGINT    AUTO_INCREMENT PRIMARY KEY,
    employee_id    BIGINT    NOT NULL,
    reviewer_id    BIGINT,

    review_year    YEAR      NOT NULL,
    review_quarter TINYINT   NOT NULL,  -- 1 | 2 | 3 | 4

    -- Điểm: 0-100, ràng buộc bằng CHECK constraint
    score          INT,
    review_comment TEXT,

    -- Workflow: DRAFT -> SUBMITTED -> APPROVED
    status         ENUM('DRAFT','SUBMITTED','APPROVED') DEFAULT 'DRAFT',
    review_date    DATE,

    created_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_score
        CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    CONSTRAINT chk_quarter
        CHECK (review_quarter BETWEEN 1 AND 4),

    -- Mỗi nhân viên chỉ có 1 đánh giá mỗi quý mỗi năm
    UNIQUE KEY uq_review_emp_period (employee_id, review_year, review_quarter),

    CONSTRAINT fk_review_emp
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_review_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES users(id)
        ON DELETE SET NULL
);

-- ============================================================
-- 8. TABLE: salary_history
--    Lịch sử tính lương hàng tháng
-- ============================================================
CREATE TABLE salary_history (
    id              BIGINT        AUTO_INCREMENT PRIMARY KEY,
    employee_id     BIGINT        NOT NULL,

    salary_base     DECIMAL(12,2) DEFAULT 0.00,
    bonus           DECIMAL(12,2) DEFAULT 0.00,
    tax             DECIMAL(12,2) DEFAULT 0.00,       -- Thuế TNCN
    insurance       DECIMAL(12,2) DEFAULT 0.00,       -- BHXH / BHYT / BHTN
    other_deduction DECIMAL(12,2) DEFAULT 0.00,       -- Khấu trừ khác

    -- final_salary = salary_base + bonus - tax - insurance - other_deduction
    final_salary    DECIMAL(12,2) DEFAULT 0.00,

    -- Luôn lưu ngày đầu tháng: VD 2025-03-01 đại diện tháng 3/2025
    salary_month    DATE          NOT NULL,

    payment_status  ENUM('PENDING','PAID','CANCELLED') DEFAULT 'PENDING',
    note            TEXT,

    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_salary_emp
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE CASCADE,

    -- [CRITICAL FIX] Ngăn insert bảng lương trùng tháng cho cùng nhân viên
    UNIQUE KEY uq_emp_salary_month (employee_id, salary_month)
);

-- ============================================================
-- 9. TABLE: notifications
--    Thông báo hệ thống + email
-- ============================================================
CREATE TABLE notifications (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,

    -- FK tới employee (nullable: thông báo broadcast không cần gắn employee)
    employee_id       BIGINT       DEFAULT NULL,

    title             VARCHAR(255) NOT NULL,
    content           TEXT,
    receiver_email    VARCHAR(150),

    notification_type ENUM('SALARY','ATTENDANCE','PERFORMANCE','LEAVE','GENERAL')
                      DEFAULT 'GENERAL',

    -- Email sending status
    status            ENUM('PENDING','SENT','FAILED') DEFAULT 'PENDING',

    -- In-app notification read status
    is_read           BOOLEAN      DEFAULT FALSE,
    sent_at           DATETIME     DEFAULT NULL,

    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notif_emp
        FOREIGN KEY (employee_id) REFERENCES employees(id)
        ON DELETE SET NULL
);

-- ============================================================
-- 10. INDEXES
--     Tối ưu cho Search, Filter và Pagination
-- ============================================================

-- employees: tìm kiếm theo tên, trạng thái, phòng ban, ngày vào
CREATE INDEX idx_emp_dept      ON employees(department_id);
CREATE INDEX idx_emp_status    ON employees(status);
CREATE INDEX idx_emp_hire_date ON employees(hire_date);
CREATE INDEX idx_emp_fullname  ON employees(full_name);

-- attendance: lọc theo ngày, trạng thái
CREATE INDEX idx_att_emp    ON attendance(employee_id);
CREATE INDEX idx_att_date   ON attendance(attendance_date);
CREATE INDEX idx_att_status ON attendance(status);

-- salary_history: lọc theo tháng, trạng thái thanh toán
CREATE INDEX idx_salary_emp     ON salary_history(employee_id);
CREATE INDEX idx_salary_month   ON salary_history(salary_month);
CREATE INDEX idx_salary_payment ON salary_history(payment_status);

-- performance_reviews: lọc theo reviewer, năm, trạng thái
CREATE INDEX idx_review_emp      ON performance_reviews(employee_id);
CREATE INDEX idx_review_reviewer ON performance_reviews(reviewer_id);
CREATE INDEX idx_review_year     ON performance_reviews(review_year);

-- leave_requests: lọc theo trạng thái
CREATE INDEX idx_leave_emp    ON leave_requests(employee_id);
CREATE INDEX idx_leave_status ON leave_requests(status);
CREATE INDEX idx_leave_type   ON leave_requests(leave_type);

-- notifications: lọc theo employee, email, trạng thái
CREATE INDEX idx_notif_emp    ON notifications(employee_id);
CREATE INDEX idx_notif_email  ON notifications(receiver_email);
CREATE INDEX idx_notif_status ON notifications(status);
CREATE INDEX idx_notif_read   ON notifications(is_read);

-- refresh_tokens: lookup nhanh theo user
CREATE INDEX idx_rt_user ON refresh_tokens(user_id);

-- ============================================================
-- 11. SEED DATA
-- ============================================================

-- ------------------------------------------------------------
-- Departments (manager_id cập nhật sau khi có employees)
-- ------------------------------------------------------------
INSERT INTO departments (name, description) VALUES
    ('IT',        'Software Development Department'),
    ('HR',        'Human Resource Department'),
    ('Finance',   'Finance Department'),
    ('Marketing', 'Marketing Team');

-- ------------------------------------------------------------
-- Employees
-- ------------------------------------------------------------
INSERT INTO employees
    (employee_code, full_name, email, phone, gender, date_of_birth,
     position, department_id, salary_base, hire_date)
VALUES
    ('EMP001', 'Nguyen Van Admin',    'admin@hrm.com',   '0901111111', 'MALE',   '1995-05-10', 'System Administrator', 1, 2000.00, '2022-01-01'),
    ('EMP002', 'Tran Thi HR',         'hr@hrm.com',      '0902222222', 'FEMALE', '1997-02-15', 'HR Specialist',        2, 1500.00, '2023-03-01'),
    ('EMP003', 'Le Van Manager',      'manager@hrm.com', '0903333333', 'MALE',   '1994-08-20', 'IT Manager',           1, 1800.00, '2021-11-01'),
    ('EMP004', 'Pham Thi Employee',   'emp@hrm.com',     '0904444444', 'FEMALE', '1999-09-12', 'Marketing Executive',  4, 1000.00, '2024-01-01'),
    ('EMP005', 'Vo Van Dev',          'dev@hrm.com',     '0905555555', 'MALE',   '1998-03-22', 'Software Developer',   1, 1200.00, '2023-07-01');

-- Gán trưởng phòng cho departments
UPDATE departments SET manager_id = 3 WHERE name = 'IT';
UPDATE departments SET manager_id = 2 WHERE name = 'HR';

-- ------------------------------------------------------------
-- Users
-- PASSWORD: tất cả là BCrypt hash của chuỗi '123456'
-- Hash: $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
--
-- !! QUAN TRỌNG !!
-- Khi dùng Spring Security (BCryptPasswordEncoder), Spring sẽ
-- tự verify hash này. Nếu muốn tự generate hash mới, dùng:
--   new BCryptPasswordEncoder().encode("123456")
-- ------------------------------------------------------------
INSERT INTO users (username, password, role, employee_id, is_active) VALUES
    ('admin',     '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'ADMIN',    1, TRUE),
    ('hr1',       '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'HR',       2, TRUE),
    ('manager1',  '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'MANAGER',  3, TRUE),
    ('employee1', '$2a$10$bf4Hxqk.ybSYZVBbNXA.2e5qJjCWmSlm.7krYCMB8GT.pYODCymcS', 'EMPLOYEE', 4, TRUE);

-- ------------------------------------------------------------
-- Attendance (tháng 3/2025)
-- Ca làm việc: 09:00 – 17:00 (span-based, lunch included)
-- LATE        : check-in sau 09:00
-- HALF_DAY    : span < 4h
-- PRESENT     : check-in <= 09:00 và span >= 4h
-- overtime_hours = phút sau 17:00 / 60  (chỉ > 0 nếu check-out sau 17:00)
-- ------------------------------------------------------------
INSERT INTO attendance
    (employee_id, check_in, check_out, work_hours, overtime_hours, attendance_date, status, note)
VALUES
    -- emp 1: vào đúng giờ 09:00, về 18:00 → OT = 1h (sau 17:00)
    (1, '2025-03-01 09:00:00', '2025-03-01 18:00:00', 9.00, 1.00, '2025-03-01', 'PRESENT',  NULL),

    -- emp 2: vào trễ 09:20, về đúng 17:00 → LATE, span = 7.67h, OT = 0
    (2, '2025-03-01 09:20:00', '2025-03-01 17:00:00', 7.67, 0.00, '2025-03-01', 'LATE',     'Trễ 20 phút'),

    -- emp 3: vào đúng giờ 09:00, về 12:30 → HALF_DAY (span 3.5h < 4h), OT = 0
    (3, '2025-03-01 09:00:00', '2025-03-01 12:30:00', 3.50, 0.00, '2025-03-01', 'HALF_DAY', 'Nghỉ chiều khám bệnh'),

    -- emp 4: nghỉ phép cả ngày → ON_LEAVE, không có check-in/check-out
    (4, NULL,                  NULL,                  0.00, 0.00, '2025-03-01', 'ON_LEAVE',  'Nghỉ phép năm đã duyệt'),

    -- emp 5: vào đúng giờ 09:00, về 17:30 → PRESENT, span = 8.5h, OT = 0.5h
    (5, '2025-03-01 09:00:00', '2025-03-01 17:30:00', 8.50, 0.50, '2025-03-01', 'PRESENT',  NULL);

-- ------------------------------------------------------------
-- Leave Requests
-- ------------------------------------------------------------
INSERT INTO leave_requests
    (employee_id, leave_type, start_date, end_date, total_days, reason, status, approved_by, approved_at)
VALUES
    (4, 'ANNUAL', '2025-03-01', '2025-03-01', 1, 'Personal matters',  'APPROVED', 1, '2025-02-28 10:00:00'),
    (5, 'SICK',   '2025-02-20', '2025-02-21', 2, 'Flu - doctor note', 'APPROVED', 3, '2025-02-20 09:00:00'),
    (2, 'ANNUAL', '2025-04-10', '2025-04-11', 2, 'Family event',      'PENDING',  NULL, NULL);

-- ------------------------------------------------------------
-- Performance Reviews (Q1 - 2025)
-- ------------------------------------------------------------
INSERT INTO performance_reviews
    (employee_id, reviewer_id, review_year, review_quarter, score, review_comment, status, review_date)
VALUES
    (4, 1, 2025, 1, 85, 'Good working attitude, meets expectations',   'APPROVED', '2025-03-10'),
    (5, 3, 2025, 1, 92, 'Excellent coding skills, exceeds expectations','APPROVED', '2025-03-10'),
    (2, 1, 2025, 1, 88, 'Strong HR skills, good communication',         'SUBMITTED','2025-03-10');

-- ------------------------------------------------------------
-- Salary History (tháng 3/2025)
-- Công thức: final_salary = salary_base + bonus - tax - insurance - other_deduction
-- EMP001: 2000 + 300 - 200 - 50 - 0   = 2050
-- EMP002: 1500 + 100 - 150 - 30 - 0   = 1420
-- EMP003: 1800 + 200 - 180 - 40 - 0   = 1780
-- EMP004: 1000 +  50 - 100 - 20 - 0   =  930
-- EMP005: 1200 + 120 - 120 - 30 - 0   = 1170
-- ------------------------------------------------------------
INSERT INTO salary_history
    (employee_id, salary_base, bonus, tax, insurance, other_deduction, final_salary, salary_month, payment_status)
VALUES
    (1, 2000.00, 300.00, 200.00,  50.00, 0.00, 2050.00, '2025-03-01', 'PAID'),
    (2, 1500.00, 100.00, 150.00,  30.00, 0.00, 1420.00, '2025-03-01', 'PAID'),
    (3, 1800.00, 200.00, 180.00,  40.00, 0.00, 1780.00, '2025-03-01', 'PAID'),
    (4, 1000.00,  50.00, 100.00,  20.00, 0.00,  930.00, '2025-03-01', 'PAID'),
    (5, 1200.00, 120.00, 120.00,  30.00, 0.00, 1170.00, '2025-03-01', 'PAID');

-- ------------------------------------------------------------
-- Notifications
-- ------------------------------------------------------------
INSERT INTO notifications
    (employee_id, title, content, receiver_email, notification_type, status, is_read)
VALUES
    (4, 'Welcome to HRM',          'Welcome! Your account has been created.',              'emp@hrm.com',     'GENERAL',     'SENT', TRUE),
    (3, 'Performance Review Due',  'Please complete Q1-2025 performance review for team.', 'manager@hrm.com', 'PERFORMANCE', 'SENT', FALSE),
    (1, 'Salary Processed',        'Your salary for March 2025 has been processed.',       'admin@hrm.com',   'SALARY',      'SENT', FALSE),
    (2, 'Leave Request Approved',  'Leave request for EMP004 (2025-03-01) approved.',      'hr@hrm.com',      'LEAVE',       'SENT', TRUE),
    (5, 'Attendance Alert',        'You have been marked LATE on 2025-03-01.',             'dev@hrm.com',     'ATTENDANCE',  'SENT', FALSE);

-- ============================================================
-- VERIFY: Kiểm tra dữ liệu đã insert đúng
-- ============================================================
SELECT 'departments'       AS tbl, COUNT(*) AS total FROM departments
UNION ALL
SELECT 'employees'         AS tbl, COUNT(*) AS total FROM employees
UNION ALL
SELECT 'users'             AS tbl, COUNT(*) AS total FROM users
UNION ALL
SELECT 'attendance'        AS tbl, COUNT(*) AS total FROM attendance
UNION ALL
SELECT 'leave_requests'    AS tbl, COUNT(*) AS total FROM leave_requests
UNION ALL
SELECT 'performance_reviews' AS tbl, COUNT(*) AS total FROM performance_reviews
UNION ALL
SELECT 'salary_history'    AS tbl, COUNT(*) AS total FROM salary_history
UNION ALL
SELECT 'notifications'     AS tbl, COUNT(*) AS total FROM notifications;
