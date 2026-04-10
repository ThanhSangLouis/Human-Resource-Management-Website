package org.example.hrmsystem.ai;

import tools.jackson.databind.json.JsonMapper;
import org.example.hrmsystem.dto.AttendanceResponse;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.service.AttendanceHistoryAccess;
import org.example.hrmsystem.service.AttendanceService;
import org.example.hrmsystem.service.DashboardService;
import org.example.hrmsystem.service.EmployeeService;
import org.example.hrmsystem.service.LeaveRequestService;
import org.example.hrmsystem.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrAiToolServiceTest {

    @Mock
    AttendanceService attendanceService;
    @Mock
    LeaveRequestService leaveRequestService;
    @Mock
    NotificationService notificationService;
    @Mock
    AttendanceRepository attendanceRepository;
    @Mock
    LeaveRequestRepository leaveRequestRepository;
    @Mock
    DashboardService dashboardService;
    @Mock
    EmployeeService employeeService;

    private final JsonMapper jsonMapper = JsonMapper.shared();
    private HrAiToolService hrAiToolService;

    @BeforeEach
    void setUp() {
        hrAiToolService = new HrAiToolService(
                attendanceService,
                leaveRequestService,
                notificationService,
                attendanceRepository,
                leaveRequestRepository,
                dashboardService,
                employeeService,
                jsonMapper,
                12
        );
    }

    @Test
    void selfAttendance_loadsTodayAndHistory() {
        AppUserDetails user = employee(7L);
        AttendanceResponse today = new AttendanceResponse(
                1L, 7L, LocalDate.now(), null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, "ok"
        );
        when(attendanceService.getTodayStatus(7L)).thenReturn(today);
        when(attendanceService.getHistory(
                eq(AttendanceHistoryAccess.OWN_EMPLOYEE),
                eq(7L),
                any(),
                eq(null),
                eq(null),
                eq(null),
                eq(0),
                eq(10)
        )).thenReturn(Map.of("content", "x"));

        Map<String, Object> out = hrAiToolService.runTools(AiIntent.SELF_ATTENDANCE, user, "chấm công");

        assertThat(out).containsKeys("today", "recentHistory");
        verify(attendanceService).getTodayStatus(7L);
    }

    @Test
    void selfLeave_includesAnnualSum() {
        AppUserDetails user = employee(3L);
        when(leaveRequestService.getMyLeaves(3L, 0, 15)).thenReturn(Map.of("content", "y"));
        when(leaveRequestRepository.sumApprovedAnnualDaysOverlappingRange(
                eq(3L), any(), any(), any(), any()
        )).thenReturn(5L);

        Map<String, Object> out = hrAiToolService.runTools(AiIntent.SELF_LEAVE, user, "phép");

        assertThat(out).containsKey("annualLeaveApprovedDaysThisYear");
        assertThat(out.get("annualLeaveRemainingEstimate")).isEqualTo(7);
    }

    @Test
    void selfNotifications_usesUnreadCount() {
        AppUserDetails user = employee(2L);
        when(notificationService.listForEmployee(eq(2L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(notificationService.countUnreadForEmployee(2L)).thenReturn(4L);

        Map<String, Object> out = hrAiToolService.runTools(AiIntent.SELF_NOTIFICATIONS, user, "thông báo");

        assertThat(out.get("unreadCount")).isEqualTo(4L);
    }

    private static AppUserDetails employee(Long employeeId) {
        UserAccount ua = new UserAccount();
        ua.setUsername("u");
        ua.setPassword("p");
        ua.setRole(Role.EMPLOYEE);
        ua.setEmployeeId(employeeId);
        ua.setActive(true);
        return new AppUserDetails(ua);
    }
}
