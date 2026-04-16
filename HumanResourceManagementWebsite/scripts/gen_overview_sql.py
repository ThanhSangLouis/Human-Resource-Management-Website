"""Generate hrm_db_overview_data_update.sql (UTF-8, no BOM).

Chạy SAU: hrm_db.sql -> seed_data.sql
- Không INSERT phòng ban / nhân viên (tránh trùng EMP006+ với seed_data).
- Chấm công tháng 3 & 4/2026 cho toàn bộ nhân viên ACTIVE (id 1..19).
- leave_requests / notifications dùng subquery theo email (đồng bộ seed_data).
- Đánh giá hiệu suất (performance_reviews): chỉnh tay trong file SQL — giữ khớp BƯỚC 5 của seed_data.sql
  (script này không sinh block đó).
"""
import datetime
import io
import sys
from pathlib import Path


def row_att(emp_id: int, d: datetime.date, status: str) -> str:
    ds = d.isoformat()
    if status == "PRESENT":
        return (
            f"({emp_id}, '{ds} 09:00:00', '{ds} 17:30:00', 8.50, 0.50, '{ds}', 'PRESENT', NULL)"
        )
    if status == "LATE":
        return (
            f"({emp_id}, '{ds} 09:25:00', '{ds} 17:00:00', 7.58, 0.00, '{ds}', 'LATE', 'Trễ buổi sáng')"
        )
    if status == "HALF_DAY":
        return (
            f"({emp_id}, '{ds} 09:00:00', '{ds} 12:30:00', 3.50, 0.00, '{ds}', 'HALF_DAY', 'Nghỉ chiều')"
        )
    if status == "ON_LEAVE":
        return (
            f"({emp_id}, NULL, NULL, 0.00, 0.00, '{ds}', 'ON_LEAVE', 'Nghỉ phép có duyệt')"
        )
    if status == "ABSENT":
        return (
            f"({emp_id}, NULL, NULL, 0.00, 0.00, '{ds}', 'ABSENT', 'Không có mặt')"
        )
    raise ValueError(status)


def pick_status(emp_id: int, d: datetime.date) -> str:
    x = (emp_id * 31 + d.year * 13 + d.month * 7 + d.day) % 100
    if x < 52:
        return "PRESENT"
    if x < 68:
        return "LATE"
    if x < 78:
        return "HALF_DAY"
    if x < 88:
        return "ON_LEAVE"
    return "ABSENT"


def weekdays(start: datetime.date, end: datetime.date):
    out = []
    d = start
    while d <= end:
        if d.weekday() < 5:
            out.append(d)
        d += datetime.timedelta(days=1)
    return out


def emit_inserts(out: io.TextIOBase, rows, chunk: int = 100):
    for i in range(0, len(rows), chunk):
        part = rows[i : i + chunk]
        out.write("INSERT INTO attendance\n")
        out.write(
            "    (employee_id, check_in, check_out, work_hours, overtime_hours, attendance_date, status, note)\n"
        )
        out.write("VALUES\n")
        out.write("    " + ",\n    ".join(part) + ";\n\n")


def main():
    # hrm_db: 5 NV (id 1-5) + seed_data: EMP006-EMP019 (id 6-19) => 19 nhân viên, đều ACTIVE
    active_emp = list(range(1, 20))

    out_path = Path(__file__).resolve().parent.parent / "hrm_db_overview_data_update.sql"
    out = out_path.open("w", encoding="utf-8", newline="\n")

    header = r"""-- ============================================================
--  BỔ SUNG DỮ LIỆU DEMO CHO TRANG OVERVIEW / DASHBOARD
--
--  Thứ tự chạy (đồng bộ với seed_data.sql):
--    1) hrm_db.sql
--    2) seed_data.sql
--    3) file này
--
--  Nội dung:
--    + Chấm công toàn bộ ngày làm việc tháng 3 & 4/2026
--      cho 19 nhân viên ACTIVE (id 1..19 sau hai bước trên).
--    + Đơn nghỉ phép / thông báo mẫu (employee_id qua email, khớp seed_data).
--
--  Không thêm phòng ban hay EMP mới — tránh trùng mã với seed_data.
--  Chạy một lần; chạy lại có thể trùng (employee_id, attendance_date).
-- ============================================================

USE hrm_db;
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- Chấm công: tháng 3 & 4/2026 (mọi nhân viên ACTIVE hiện có)
-- ------------------------------------------------------------
"""
    out.write(header)

    all_rows = []
    for start, end in [
        (datetime.date(2026, 3, 1), datetime.date(2026, 3, 31)),
        (datetime.date(2026, 4, 1), datetime.date(2026, 4, 30)),
    ]:
        for d in weekdays(start, end):
            for e in active_emp:
                all_rows.append(row_att(e, d, pick_status(e, d)))

    emit_inserts(out, all_rows)

    footer = r"""
-- ------------------------------------------------------------
-- Đơn nghỉ phép & thông báo (subquery theo email — khớp seed_data)
-- ------------------------------------------------------------
INSERT INTO leave_requests
    (employee_id, leave_type, start_date, end_date, total_days, reason, status, approved_by, approved_at)
VALUES
    ((SELECT id FROM employees WHERE email = 'duc.tran@hrm.com'),
     'SICK',   '2026-04-08', '2026-04-09', 2, 'Cúm - có giấy bác sĩ', 'APPROVED',
     (SELECT id FROM users WHERE username = 'admin'), '2026-04-07 08:30:00'),

    ((SELECT id FROM employees WHERE email = 'thu.dang@hrm.com'),
     'ANNUAL', '2026-03-12', '2026-03-13', 2, 'Du lịch gia đình', 'APPROVED',
     (SELECT id FROM users WHERE username = 'manager1'), '2026-03-10 14:00:00'),

    ((SELECT id FROM employees WHERE email = 'bich.nguyen@hrm.com'),
     'OTHER',  '2026-04-21', '2026-04-21', 1, 'Việc cá nhân', 'PENDING', NULL, NULL);

INSERT INTO notifications
    (employee_id, title, content, receiver_email, notification_type, status, is_read)
VALUES
    (NULL,
     'Nhắc họp Q2',
     'Lịch họp review quý 2/2026 — các phòng ban.',
     NULL,
     'GENERAL', 'SENT', FALSE),

    ((SELECT id FROM employees WHERE email = 'long.phan@hrm.com'),
     'Chỉ tiêu Q2',
     'Cập nhật KPI Marketing quý 2.',
     'long.phan@hrm.com',
     'GENERAL', 'SENT', FALSE);

-- ============================================================
-- Kiểm tra nhanh (tùy chọn)
-- ============================================================
-- SELECT COUNT(*) FROM employees WHERE status = 'ACTIVE';
-- SELECT status, COUNT(*) FROM attendance WHERE attendance_date BETWEEN '2026-04-01' AND '2026-04-30' GROUP BY status;
"""
    out.write(footer)
    out.close()
    print(f"Wrote {out_path}", file=sys.stderr)


if __name__ == "__main__":
    main()
