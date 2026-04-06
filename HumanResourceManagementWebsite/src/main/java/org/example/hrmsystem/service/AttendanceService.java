package org.example.hrmsystem.service;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.example.hrmsystem.dto.AttendanceHistoryRow;
import org.example.hrmsystem.dto.AttendanceResponse;
import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.AttendanceStatus;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.LeaveStatus;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.LeaveRequestRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    // Span-based shift policy: 09:00 – 17:00 (8h span, lunch included)
    private static final LocalTime SHIFT_START    = LocalTime.of(9, 0);   // check-in deadline = late threshold
    private static final LocalTime SHIFT_END      = LocalTime.of(17, 0);  // OT starts after this
    private static final double    HALF_DAY_SPAN  = 4.0;                  // span < 4h → HALF_DAY

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserAccountRepository userAccountRepository;
    private final ManagerEmployeeScopeService managerEmployeeScopeService;
    private final LeaveRequestRepository leaveRequestRepository;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EmployeeRepository employeeRepository,
            UserAccountRepository userAccountRepository,
            ManagerEmployeeScopeService managerEmployeeScopeService,
            LeaveRequestRepository leaveRequestRepository
    ) {
        this.attendanceRepository = attendanceRepository;
        this.employeeRepository = employeeRepository;
        this.userAccountRepository = userAccountRepository;
        this.managerEmployeeScopeService = managerEmployeeScopeService;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional
    public AttendanceResponse checkIn(Long employeeId, String note) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        if (leaveRequestRepository.existsByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                employeeId, LeaveStatus.APPROVED, today, today)) {
            throw new IllegalStateException("Cannot check in: you have approved leave on this date");
        }

        Optional<Attendance> existing = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, today);
        if (existing.isPresent()) {
            Attendance att = existing.get();
            if (att.getCheckIn() != null) {
                throw new IllegalStateException("Already checked in today at " + att.getCheckIn().toLocalTime());
            }
            if (att.getStatus() == AttendanceStatus.ON_LEAVE) {
                throw new IllegalStateException("Cannot check in: attendance is marked as on leave for this date");
            }
        }

        Attendance attendance = existing.orElse(new Attendance());
        attendance.setEmployeeId(employeeId);
        attendance.setAttendanceDate(today);
        attendance.setCheckIn(now);
        attendance.setStatus(
            now.toLocalTime().isAfter(SHIFT_START) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT
        );
        if (note != null && !note.isBlank()) {
            attendance.setNote(note.trim());
        }

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Employee {} checked in at {}", employeeId, now);

        return toResponse(saved, "Check-in successful");
    }

    @Transactional
    public AttendanceResponse checkOut(Long employeeId, String note) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Attendance attendance = attendanceRepository
            .findByEmployeeIdAndAttendanceDate(employeeId, today)
            .orElseThrow(() -> new IllegalStateException("No check-in record found for today. Please check in first."));

        if (attendance.getCheckIn() == null) {
            throw new IllegalStateException("Check-in record is missing. Please check in first.");
        }
        if (attendance.getCheckOut() != null) {
            throw new IllegalStateException("Already checked out today at " + attendance.getCheckOut().toLocalTime());
        }

        attendance.setCheckOut(now);

        // work_hours = raw span check-in → check-out (lunch included in span, not deducted)
        long minutesSpan = ChronoUnit.MINUTES.between(attendance.getCheckIn(), now);
        double hoursSpan = minutesSpan / 60.0;

        // overtime = time after max(checkIn, SHIFT_END) — prevents counting OT before employee arrived
        LocalDateTime shiftEndToday = now.toLocalDate().atTime(SHIFT_END);
        LocalDateTime otStart = attendance.getCheckIn().isAfter(shiftEndToday)
            ? attendance.getCheckIn()
            : shiftEndToday;
        long overtimeMinutes = now.isAfter(otStart)
            ? ChronoUnit.MINUTES.between(otStart, now)
            : 0L;
        double overtime = overtimeMinutes / 60.0;

        attendance.setWorkHours(BigDecimal.valueOf(hoursSpan).setScale(2, RoundingMode.HALF_UP));
        attendance.setOvertimeHours(BigDecimal.valueOf(overtime).setScale(2, RoundingMode.HALF_UP));

        // Recalculate status based on span and check-in time
        if (hoursSpan < HALF_DAY_SPAN) {
            attendance.setStatus(AttendanceStatus.HALF_DAY);
        } else if (attendance.getCheckIn().toLocalTime().isAfter(SHIFT_START)) {
            attendance.setStatus(AttendanceStatus.LATE);
        } else {
            attendance.setStatus(AttendanceStatus.PRESENT);
        }

        if (note != null && !note.isBlank()) {
            String existingNote = attendance.getNote();
            attendance.setNote(
                existingNote != null ? existingNote + " | Checkout: " + note.trim() : note.trim()
            );
        }

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Employee {} checked out at {}, worked {}h", employeeId, now,
            saved.getWorkHours());

        return toResponse(saved, "Check-out successful. Span: " +
            saved.getWorkHours() + "h" +
            (overtime > 0 ? ", overtime: " + saved.getOvertimeHours() + "h" : ""));
    }

    public AttendanceResponse getTodayStatus(Long employeeId) {
        LocalDate today = LocalDate.now();
        Optional<Attendance> opt = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, today);
        if (opt.isEmpty()) {
            if (leaveRequestRepository.existsByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                    employeeId, LeaveStatus.APPROVED, today, today)) {
                return new AttendanceResponse(
                        null, employeeId, today, null, null,
                        BigDecimal.ZERO, BigDecimal.ZERO, AttendanceStatus.ON_LEAVE, null,
                        "Approved leave today"
                );
            }
            return new AttendanceResponse(
                null, employeeId, today, null, null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, "No attendance record for today"
            );
        }
        return toResponse(opt.get(), null);
    }

    public Map<String, Object> getHistory(
            AttendanceHistoryAccess access,
            Long actorEmployeeId,
            String monthParam,
            String dateParam,
            Long departmentId,
            AttendanceStatus statusFilter,
            int page,
            int size
    ) {
        LocalDate singleDay = null;
        YearMonth ym;
        LocalDate from;
        LocalDate to;

        if (dateParam != null && !dateParam.isBlank()) {
            singleDay = LocalDate.parse(dateParam.trim());
            from = singleDay;
            to = singleDay;
            ym = YearMonth.from(singleDay);
        } else {
            ym = (monthParam == null || monthParam.isBlank())
                    ? YearMonth.now()
                    : YearMonth.parse(monthParam.trim());
            from = ym.atDay(1);
            to = ym.atEndOfMonth();
        }

        final Long deptFilter = (departmentId != null && access == AttendanceHistoryAccess.ALL)
                ? departmentId
                : null;

        Set<Long> managerVisibleIds = null;
        if (access == AttendanceHistoryAccess.OWN_EMPLOYEE) {
            if (actorEmployeeId == null) {
                return emptyHistoryPage(page, ym, singleDay);
            }
        } else if (access == AttendanceHistoryAccess.MANAGED_DEPARTMENTS) {
            managerVisibleIds = managerEmployeeScopeService.attendanceVisibleEmployeeIdsForManager(actorEmployeeId);
            if (managerVisibleIds.isEmpty()) {
                return emptyHistoryPage(page, ym, singleDay);
            }
        }

        final Set<Long> inClause = managerVisibleIds;
        final Long selfId = access == AttendanceHistoryAccess.OWN_EMPLOYEE ? actorEmployeeId : null;
        final boolean hideAdminAttendance = access == AttendanceHistoryAccess.MANAGED_DEPARTMENTS;
        final Set<Long> excludeAdminKeys = hideAdminAttendance
                ? attendanceEmployeeKeysForAdminAccounts()
                : Set.of();

        Specification<Attendance> spec = (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            parts.add(cb.between(root.get("attendanceDate"), from, to));
            if (selfId != null) {
                parts.add(cb.equal(root.get("employeeId"), selfId));
            } else if (inClause != null) {
                parts.add(root.get("employeeId").in(inClause));
            }
            if (!excludeAdminKeys.isEmpty()) {
                parts.add(cb.not(root.get("employeeId").in(excludeAdminKeys)));
            }
            if (deptFilter != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<Employee> er = sq.from(Employee.class);
                sq.select(er.get("id"));
                sq.where(cb.equal(er.get("departmentId"), deptFilter));
                parts.add(root.get("employeeId").in(sq));
            }
            if (statusFilter != null) {
                parts.add(cb.equal(root.get("status"), statusFilter));
            }
            return cb.and(parts.toArray(Predicate[]::new));
        };

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "attendanceDate"));
        Page<Attendance> result = attendanceRepository.findAll(spec, pageable);

        Set<Long> ids = result.getContent().stream()
                .map(Attendance::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            employeeRepository.findAllById(ids).forEach(e -> names.put(e.getId(), e.getFullName()));
        }

        List<AttendanceHistoryRow> content = result.getContent().stream()
                .map(a -> new AttendanceHistoryRow(
                        a.getId(),
                        a.getEmployeeId(),
                        names.getOrDefault(a.getEmployeeId(), "—"),
                        a.getAttendanceDate(),
                        a.getCheckIn(),
                        a.getCheckOut(),
                        a.getWorkHours(),
                        a.getOvertimeHours(),
                        a.getStatus(),
                        a.getNote()
                ))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", content);
        out.put("totalElements", result.getTotalElements());
        out.put("totalPages", result.getTotalPages());
        out.put("page", result.getNumber());
        out.put("month", ym.toString());
        out.put("period", singleDay != null ? singleDay.toString() : ym.toString());
        return out;
    }

    /**
     * Toàn bộ dòng chấm công trong tháng (dùng cho export Excel HR), có lọc phòng ban/trạng thái.
     */
    public List<AttendanceHistoryRow> listForExport(
            String monthParam,
            Long departmentId,
            AttendanceStatus statusFilter
    ) {
        YearMonth ym = YearMonth.parse(monthParam.trim());
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        Specification<Attendance> spec = (root, query, cb) -> {
            List<Predicate> parts = new ArrayList<>();
            parts.add(cb.between(root.get("attendanceDate"), from, to));
            if (departmentId != null) {
                Subquery<Long> sq = query.subquery(Long.class);
                Root<Employee> er = sq.from(Employee.class);
                sq.select(er.get("id"));
                sq.where(cb.equal(er.get("departmentId"), departmentId));
                parts.add(root.get("employeeId").in(sq));
            }
            if (statusFilter != null) {
                parts.add(cb.equal(root.get("status"), statusFilter));
            }
            return cb.and(parts.toArray(Predicate[]::new));
        };

        List<Attendance> all = attendanceRepository.findAll(spec,
                Sort.by(Sort.Direction.DESC, "attendanceDate").and(Sort.by(Sort.Direction.DESC, "id")));
        Set<Long> ids = all.stream().map(Attendance::getEmployeeId).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        if (!ids.isEmpty()) {
            employeeRepository.findAllById(ids).forEach(e -> names.put(e.getId(), e.getFullName()));
        }
        return all.stream()
                .map(a -> new AttendanceHistoryRow(
                        a.getId(),
                        a.getEmployeeId(),
                        names.getOrDefault(a.getEmployeeId(), "—"),
                        a.getAttendanceDate(),
                        a.getCheckIn(),
                        a.getCheckOut(),
                        a.getWorkHours(),
                        a.getOvertimeHours(),
                        a.getStatus(),
                        a.getNote()
                ))
                .toList();
    }

    /**
     * Các giá trị {@code attendance.employee_id} gắn với tài khoản ADMIN:
     * {@code users.employee_id} (nếu có) và {@code users.id} (fallback khi chấm công không có profile nhân viên).
     */
    private Set<Long> attendanceEmployeeKeysForAdminAccounts() {
        Set<Long> keys = new HashSet<>();
        for (UserAccount u : userAccountRepository.findByRole(Role.ADMIN)) {
            if (u.getEmployeeId() != null) {
                keys.add(u.getEmployeeId());
            }
            keys.add(u.getId());
        }
        return keys;
    }

    private static Map<String, Object> emptyHistoryPage(int page, YearMonth ym, LocalDate singleDay) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("content", List.of());
        out.put("totalElements", 0L);
        out.put("totalPages", 0);
        out.put("page", page);
        out.put("month", ym.toString());
        out.put("period", singleDay != null ? singleDay.toString() : ym.toString());
        return out;
    }

    private AttendanceResponse toResponse(Attendance a, String message) {
        return new AttendanceResponse(
            a.getId(),
            a.getEmployeeId(),
            a.getAttendanceDate(),
            a.getCheckIn(),
            a.getCheckOut(),
            a.getWorkHours(),
            a.getOvertimeHours(),
            a.getStatus(),
            a.getNote(),
            message
        );
    }
}
