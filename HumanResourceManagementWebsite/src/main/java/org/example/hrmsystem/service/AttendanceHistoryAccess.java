package org.example.hrmsystem.service;

/**
 * Quy tắc xem lịch sử chấm công theo vai trò:
 * <ul>
 *   <li>{@link #ALL} — ADMIN, HR: toàn công ty (nhân sự / quản trị)</li>
 *   <li>{@link #OWN_EMPLOYEE} — EMPLOYEE: chỉ bản thân</li>
 *   <li>{@link #MANAGED_DEPARTMENTS} — MANAGER: bản thân + nhân viên EMPLOYEE (hoặc chưa có user) trong phòng quản lý; ẩn bản ghi gắn user ADMIN</li>
 * </ul>
 */
public enum AttendanceHistoryAccess {
    ALL,
    OWN_EMPLOYEE,
    MANAGED_DEPARTMENTS
}
