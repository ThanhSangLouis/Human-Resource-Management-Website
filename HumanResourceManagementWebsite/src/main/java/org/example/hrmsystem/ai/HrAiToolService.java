package org.example.hrmsystem.ai;

import tools.jackson.databind.json.JsonMapper;
import org.example.hrmsystem.dto.AttendanceResponse;
import org.example.hrmsystem.dto.EmployeeResponse;
import org.example.hrmsystem.dto.NotificationResponse;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.AttendanceHistoryAccess;
import org.example.hrmsystem.service.AttendanceService;
import org.example.hrmsystem.service.DashboardService;
import org.example.hrmsystem.service.EmployeeService;
import org.example.hrmsystem.service.LeaveRequestService;
import org.example.hrmsystem.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HrAiToolService {

    private static final Logger log = LoggerFactory.getLogger(HrAiToolService.class);

    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final LeaveRequestService leaveRequestService;
    private final NotificationService notificationService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final DashboardService dashboardService;
    private final EmployeeService employeeService;
    private final JsonMapper jsonMapper;
    private final int annualDefaultDays;

    public HrAiToolService(
            AttendanceService attendanceService,
            LeaveRequestService leaveRequestService,
            NotificationService notificationService,
            AttendanceRepository attendanceRepository,
            LeaveRequestRepository leaveRequestRepository,
            DashboardService dashboardService,
            EmployeeService employeeService,
            JsonMapper jsonMapper,
            @Value("${ai.leave.annual-days-default:12}") int annualDefaultDays
    ) {
        this.attendanceService = attendanceService;
        this.leaveRequestService = leaveRequestService;
        this.notificationService = notificationService;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.dashboardService = dashboardService;
        this.employeeService = employeeService;
        this.jsonMapper = jsonMapper;
        this.annualDefaultDays = Math.max(0, annualDefaultDays);
    }

    public Map<String, Object> runTools(AiIntent intent, AppUserDetails user, String rawMessage) {
        Long key = actorEmployeeKey(user);
        return switch (intent) {
            case CHITCHAT -> Map.of();
            case FAQ -> Map.of("policyOnly", true);
            case SELF_ATTENDANCE -> selfAttendance(key);
            case SELF_LEAVE -> selfLeave(key);
            case SELF_NOTIFICATIONS -> selfNotifications(key);
            case MGR_PENDING_LEAVE -> mgrPending(user);
            case MGR_TEAM_ATTENDANCE -> mgrTeamLate(user);
            case NOTIF_SUMMARY -> notifSummary(key);
            case DASHBOARD_STATS -> dashboardStats(user);
            case HR_EMPLOYEE_LOOKUP -> hrEmployeeLookup(user, rawMessage);
            case HR_EMPLOYEE_DETAILS -> Map.of("error", "Thiếu ngữ cảnh nhân viên. Hãy tra cứu nhân viên trước, rồi hỏi 'chi tiết hơn'.");
        };
    }

    /**
     * Chi tiết theo một nhân viên cụ thể (RBAC check bằng EmployeeService).
     * Mặc định tính theo tháng hiện tại (attendance) và năm hiện tại (leave).
     */
    public Map<String, Object> employeeDetails(AppUserDetails actor, Long employeeId) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (employeeId == null) {
            return Map.of("error", "Thiếu employeeId để tra cứu chi tiết.");
        }
        try {
            // RBAC: HR/Admin full, Manager trong team, Employee chỉ self (dùng chung rule)
            EmployeeResponse emp = employeeService.getByIdForActor(employeeId, actor);
            snap.put("employee", jsonMapper.convertValue(emp, Map.class));
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return Map.of("error", "Bạn không có quyền xem chi tiết nhân viên này.");
        } catch (Exception ex) {
            return Map.of("error", "Không tìm thấy nhân viên hoặc không đọc được hồ sơ: " + ex.getMessage());
        }

        java.time.YearMonth ym = java.time.YearMonth.now();
        java.time.LocalDate from = ym.atDay(1);
        java.time.LocalDate to = ym.atEndOfMonth();

        long lateDays = attendanceRepository.countByEmployeeIdAndAttendanceDateBetweenAndStatus(
                employeeId, from, to, AttendanceStatus.LATE);
        java.math.BigDecimal workHours = attendanceRepository.sumWorkHoursInRangeForEmployee(employeeId, from, to);
        java.math.BigDecimal overtimeHours = attendanceRepository.sumOvertimeHoursInRangeForEmployee(employeeId, from, to);

        Map<String, Object> att = new LinkedHashMap<>();
        att.put("month", ym.toString());
        att.put("lateDays", lateDays);
        att.put("workHours", workHours);
        att.put("overtimeHours", overtimeHours);
        snap.put("attendanceThisMonth", att);

        int year = java.time.LocalDate.now().getYear();
        java.time.LocalDate yStart = java.time.LocalDate.of(year, 1, 1);
        java.time.LocalDate yEnd = java.time.LocalDate.of(year, 12, 31);

        int annualUsed = safeInt(leaveRequestRepository.sumApprovedAnnualDaysOverlappingRange(
                employeeId, LeaveStatus.APPROVED, LeaveType.ANNUAL, yStart, yEnd));
        int unpaidUsed = safeInt(leaveRequestRepository.sumApprovedDaysOverlappingRangeByType(
                employeeId, LeaveStatus.APPROVED, LeaveType.UNPAID, yStart, yEnd));
        int sickUsed = safeInt(leaveRequestRepository.sumApprovedDaysOverlappingRangeByType(
                employeeId, LeaveStatus.APPROVED, LeaveType.SICK, yStart, yEnd));

        Map<String, Object> leave = new LinkedHashMap<>();
        leave.put("year", year);
        leave.put("annualLeaveQuotaDefault", annualDefaultDays);
        leave.put("annualLeaveApprovedDays", annualUsed);
        leave.put("annualLeaveRemainingEstimate", Math.max(0, annualDefaultDays - annualUsed));
        leave.put("unpaidLeaveApprovedDays", unpaidUsed);
        leave.put("sickLeaveApprovedDays", sickUsed);
        snap.put("leaveThisYear", leave);

        return snap;
    }

    private static int safeInt(Long v) {
        if (v == null) return 0;
        if (v > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (v < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return v.intValue();
    }

    private Map<String, Object> selfAttendance(Long employeeKey) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (employeeKey == null) {
            snap.put("error", "Không gắn employeeId — không đọc được chấm công cá nhân.");
            return snap;
        }
        AttendanceResponse today = attendanceService.getTodayStatus(employeeKey);
        snap.put("today", jsonMapper.convertValue(today, Map.class));
        YearMonth ym = YearMonth.now();
        Map<String, Object> history = attendanceService.getHistory(
                AttendanceHistoryAccess.OWN_EMPLOYEE,
                employeeKey,
                ym.toString(),
                null,
                null,
                null,
                0,
                10
        );
        snap.put("recentHistory", history);
        return snap;
    }

    private Map<String, Object> selfLeave(Long employeeKey) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (employeeKey == null) {
            snap.put("error", "Không gắn employeeId — không đọc được đơn phép.");
            return snap;
        }
        snap.put("myRequestsPage", leaveRequestService.getMyLeaves(employeeKey, 0, 15));
        int year = LocalDate.now().getYear();
        LocalDate yStart = LocalDate.of(year, 1, 1);
        LocalDate yEnd = LocalDate.of(year, 12, 31);
        Long used = leaveRequestRepository.sumApprovedAnnualDaysOverlappingRange(
                employeeKey, LeaveStatus.APPROVED, LeaveType.ANNUAL, yStart, yEnd);
        int usedDays = used != null ? used.intValue() : 0;
        snap.put("annualLeaveApprovedDaysThisYear", usedDays);
        snap.put("annualLeaveQuotaDefault", annualDefaultDays);
        snap.put("annualLeaveRemainingEstimate", Math.max(0, annualDefaultDays - usedDays));
        snap.put("note", "remaining là ước tính: quota mặc định từ cấu hình trừ tổng ngày ANNUAL đã duyệt trong năm.");
        return snap;
    }

    private Map<String, Object> selfNotifications(Long employeeKey) {
        Map<String, Object> snap = new LinkedHashMap<>();
        if (employeeKey == null) {
            snap.put("error", "Không gắn employeeId — không đọc được thông báo.");
            return snap;
        }
        List<NotificationResponse> content = notificationService
                .listForEmployee(employeeKey, PageRequest.of(0, 20))
                .getContent();
        snap.put("recent", jsonMapper.convertValue(content, List.class));
        snap.put("unreadCount", notificationService.countUnreadForEmployee(employeeKey));
        return snap;
    }

    private Map<String, Object> mgrPending(AppUserDetails user) {
        return leaveRequestService.listPending(user, 0, 25);
    }

    private Map<String, Object> mgrTeamLate(AppUserDetails user) {
        Role role = Role.valueOf(user.getRole());
        Long actorKey = actorEmployeeKey(user);
        log.debug("mgrTeamLate: userId={}, employeeId={}, actorKey={}, role={}",
                user.getUserId(), user.getEmployeeId(), actorKey, role);
        AttendanceHistoryAccess access = switch (role) {
            case MANAGER -> AttendanceHistoryAccess.MANAGED_DEPARTMENTS;
            case ADMIN, HR -> AttendanceHistoryAccess.ALL;
            default -> AttendanceHistoryAccess.OWN_EMPLOYEE;
        };
        YearMonth ym = YearMonth.now();
        return attendanceService.getHistory(
                access,
                actorKey,
                ym.toString(),
                null,
                null,
                AttendanceStatus.LATE,
                0,
                50
        );
    }

    /** Thống kê tổng hợp: số nhân viên, chấm công tháng, phân bổ phòng ban. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> dashboardStats(AppUserDetails user) {
        try {
            Object stats = dashboardService.getStats(null, user);
            Map<String, Object> snap = jsonMapper.convertValue(stats, Map.class);
            if (snap == null) {
                return Map.of("error", "Không lấy được dữ liệu thống kê.");
            }
            return snap;
        } catch (org.springframework.security.access.AccessDeniedException e) {
            return Map.of("error", "Tài khoản không có quyền xem thống kê tổng hợp.");
        } catch (Exception e) {
            log.warn("dashboardStats error: {}", e.getMessage());
            return Map.of("error", "Lỗi khi lấy thống kê: " + e.getMessage());
        }
    }

    private Map<String, Object> notifSummary(Long employeeKey) {
        return selfNotifications(employeeKey);
    }

    /**
     * Tra cứu thông tin nhân viên theo từ khóa trong message.
     * <ul>
     *   <li>HR / Admin: tìm toàn công ty</li>
     *   <li>Manager: tìm trong phạm vi team (dùng {@link EmployeeService#findAllForActor})</li>
     *   <li>Employee: không có được gọi intent này (bị chặn tại Orchestrator)</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> hrEmployeeLookup(AppUserDetails user, String rawMessage) {
        // Trích keyword từ message: bỏ các cụm trigger, lấy phần còn lại làm keyword
        String keyword = extractLookupKeyword(rawMessage);
        try {
            Page<EmployeeResponse> page = employeeService.findAllForActor(
                    user, keyword, null, null, PageRequest.of(0, 10));
            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("keyword", keyword);
            snap.put("totalFound", page.getTotalElements());
            List<Map<String, Object>> rows = page.getContent().stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("mãNV", e.getEmployeeCode());
                m.put("họ Tên", e.getFullName());
                m.put("phòng Ban", e.getDepartmentName());
                m.put("chức Vụ", e.getPosition());
                m.put("email", e.getEmail());
                m.put("sĐT", e.getPhone());
                m.put("ngàySinh", e.getDateOfBirth());
                m.put("ngàyVàoCtyTy", e.getHireDate());
                m.put("trạngThái", e.getStatus());
                return m;
            }).toList();
            snap.put("employees", rows);
            if (page.getTotalElements() == 0) {
                snap.put("notice", "Không tìm thấy nhân viên nào khớp với '" + keyword + "'.");
            }
            return snap;
        } catch (org.springframework.security.access.AccessDeniedException ex) {
            return Map.of("error", "Bạn không có quyền tra cứu thông tin nhân viên này.");
        } catch (Exception ex) {
            log.warn("hrEmployeeLookup error: {}", ex.getMessage());
            return Map.of("error", "Lỗi khi tra cứu: " + ex.getMessage());
        }
    }

    /**
     * Lấy phần từ khóa tìm kiếm từ message thô.
     * Bỏ các cụm trigger như "thông tin nhân viên", "hồ sơ của",
     * giữ lại phần còn lại (thường là tên người / mã NV).
     */
    private static String extractLookupKeyword(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = AiGuardrailService.normalizeWhitespace(raw);
        // Danh s\u00e1ch c\u00e1c c\u1ee5m trigger c\u1ea7n lo\u1ea1i b\u1ecf — \u0111\u1eb7t t\u1eeb d\u00e0i nh\u1ea5t tr\u01b0\u1edbc
        String[] stopPhrases = {
                // c\u00f3 "nh\u00e2n vi\u00ean"
                "th\u00f4ng tin nh\u00e2n vi\u00ean", "thong tin nhan vien",
                "h\u1ed3 s\u01a1 nh\u00e2n vi\u00ean", "ho so nhan vien",
                "t\u00ecm ki\u1ebfm nh\u00e2n vi\u00ean", "tim kiem nhan vien",
                "t\u00ecm nh\u00e2n vi\u00ean", "tim nhan vien",
                "tra c\u1ee9u nh\u00e2n vi\u00ean", "tra cuu nhan vien",
                // m\u1eabu t\u1ef1 nhi\u00ean (prefix tr\u01b0\u1edbc t\u00ean)
                "cho t\u00f4i th\u00f4ng tin v\u1ec1", "cho toi thong tin ve",
                "cho t\u00f4i th\u00f4ng tin c\u1ee7a", "cho toi thong tin cua",
                "cho t\u00f4i th\u00f4ng tin", "cho toi thong tin",
                "xem th\u00f4ng tin v\u1ec1", "xem thong tin ve",
                "xem th\u00f4ng tin c\u1ee7a", "xem thong tin cua",
                "xem th\u00f4ng tin", "xem thong tin",
                "th\u00f4ng tin v\u1ec1 nh\u00e2n vi\u00ean", "thong tin ve nhan vien",
                "th\u00f4ng tin v\u1ec1", "thong tin ve",
                "th\u00f4ng tin c\u1ee7a", "thong tin cua",
                "h\u1ed3 s\u01a1 c\u1ee7a", "ho so cua",
                // thu\u1ed9c t\u00ednh c\u1ee5 th\u1ec3
                "lookup employee", "find employee", "search employee",
                "m\u00e3 nh\u00e2n vi\u00ean", "ma nhan vien",
                "employee code"
        };
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        for (String phrase : stopPhrases) {
            int idx = lower.indexOf(phrase.toLowerCase(java.util.Locale.ROOT));
            if (idx >= 0) {
                String after = s.substring(idx + phrase.length()).trim();
                // X\u00f3a d\u1ea5u ":", "-", kho\u1ea3ng tr\u1eafng th\u1eeba
                after = after.replaceAll("^\\s*[:\\-]\\s*", "").replaceAll("\\?$", "").trim();
                if (!after.isBlank()) return after;
            }
        }
        // Fallback: tr\u1ea3 nguy\u00ean message \u2014 Hibernate d\u00f9ng LIKE % match
        return s;
    }

    private static Long actorEmployeeKey(AppUserDetails user) {
        Long eid = user.getEmployeeId();
        return eid != null ? eid : user.getUserId();
    }
}
