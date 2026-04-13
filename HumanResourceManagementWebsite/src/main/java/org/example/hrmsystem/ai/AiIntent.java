package org.example.hrmsystem.ai;

public enum AiIntent {
    /** Trò chuyện xã giao, câu hỏi tổng quát không liên quan đến nghiệp vụ HR cụ thể. */
    CHITCHAT,
    FAQ,
    SELF_ATTENDANCE,
    SELF_LEAVE,
    SELF_NOTIFICATIONS,
    MGR_PENDING_LEAVE,
    MGR_TEAM_ATTENDANCE,
    NOTIF_SUMMARY,
    /** Thống kê tổng hợp (số nhân viên, chấm công tháng, phân bổ phòng ban...) — MANAGER, HR, ADMIN */
    DASHBOARD_STATS,
    /** Tra cứu hồ sơ nhân viên theo tên / mã — HR, ADMIN, MANAGER (trong phạm vi team) */
    HR_EMPLOYEE_LOOKUP,
    /** Chi tiết theo nhân viên vừa tra (chấm công/phép/đi muộn...) — HR, ADMIN, MANAGER (trong phạm vi team) */
    HR_EMPLOYEE_DETAILS
}
