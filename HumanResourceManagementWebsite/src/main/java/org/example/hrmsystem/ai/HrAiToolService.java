package org.example.hrmsystem.ai;

import tools.jackson.databind.json.JsonMapper;
import org.example.hrmsystem.dto.AttendanceResponse;
import org.example.hrmsystem.dto.NotificationResponse;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.LeaveType;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.AttendanceHistoryAccess;
import org.example.hrmsystem.service.AttendanceService;
import org.example.hrmsystem.service.LeaveRequestService;
import org.example.hrmsystem.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HrAiToolService {

    private final AttendanceService attendanceService;
    private final LeaveRequestService leaveRequestService;
    private final NotificationService notificationService;
    private final LeaveRequestRepository leaveRequestRepository;
    private final JsonMapper jsonMapper;
    private final int annualDefaultDays;

    public HrAiToolService(
            AttendanceService attendanceService,
            LeaveRequestService leaveRequestService,
            NotificationService notificationService,
            LeaveRequestRepository leaveRequestRepository,
            JsonMapper jsonMapper,
            @Value("${ai.leave.annual-days-default:12}") int annualDefaultDays
    ) {
        this.attendanceService = attendanceService;
        this.leaveRequestService = leaveRequestService;
        this.notificationService = notificationService;
        this.leaveRequestRepository = leaveRequestRepository;
        this.jsonMapper = jsonMapper;
        this.annualDefaultDays = Math.max(0, annualDefaultDays);
    }

    public Map<String, Object> runTools(AiIntent intent, AppUserDetails user, String rawMessage) {
        Long key = actorEmployeeKey(user);
        return switch (intent) {
            case FAQ -> Map.of("policyOnly", true);
            case SELF_ATTENDANCE -> selfAttendance(key);
            case SELF_LEAVE -> selfLeave(key);
            case SELF_NOTIFICATIONS -> selfNotifications(key);
            case MGR_PENDING_LEAVE -> mgrPending(user);
            case MGR_TEAM_ATTENDANCE -> mgrTeamLate(user);
            case NOTIF_SUMMARY -> notifSummary(key);
        };
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

    private Map<String, Object> notifSummary(Long employeeKey) {
        return selfNotifications(employeeKey);
    }

    private static Long actorEmployeeKey(AppUserDetails user) {
        Long eid = user.getEmployeeId();
        return eid != null ? eid : user.getUserId();
    }
}
