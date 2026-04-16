package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.PayrollGenerateRequest;
import org.example.hrmsystem.dto.PayrollGenerateResult;
import org.example.hrmsystem.dto.SalaryHistoryResponse;
import org.example.hrmsystem.model.Attendance;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.model.PaymentStatus;
import org.example.hrmsystem.model.SalaryHistory;
import org.example.hrmsystem.repository.AttendanceRepository;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.SalaryHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final NotificationService notificationService;

    /** Số công chuẩn / tháng: đủ công này thì phần lương từ HĐ = 100%; 1 công = lương HĐ ÷ giá trị này. */
    @Value("${hrm.payroll.standard-work-days:26}")
    private BigDecimal standardWorkDaysPerMonth;

    public PayrollService(
            SalaryHistoryRepository salaryHistoryRepository,
            EmployeeRepository employeeRepository,
            AttendanceRepository attendanceRepository,
            NotificationService notificationService
    ) {
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.attendanceRepository = attendanceRepository;
        this.notificationService = notificationService;
    }

    /**
     * Công thức thực nhận (demo):
     * <ul>
     *   <li>Phần lương theo công = {@code (lương_HĐ / chuẩn) × công_QĐ} = {@code lương_HĐ × min(công_QĐ, chuẩn) / chuẩn},
     *       chuẩn = {@link #standardWorkDaysPerMonth} (mặc định 26).</li>
     *   <li>Thưởng = toàn bộ thưởng mặc định tháng (không nhân tỷ lệ công).</li>
     *   <li>Khác (khấu trừ khác): 0 khi sinh tự động.</li>
     *   <li>Thuế (demo): 10% × (phần lương theo công + thưởng).</li>
     *   <li>BHXH gộp (demo): 2.5% × phần lương theo công.</li>
     *   <li>{@code thực nhận = phần lương theo công + thưởng − khác − thuế − BHXH}</li>
     * </ul>
     * Cột lương lưu trong DB là <strong>phần lương theo công</strong> (không phải trọn HĐ).
     */
    private BigDecimal[] computePayrollParts(BigDecimal contractSalaryBase, BigDecimal monthBonus, BigDecimal workDayUnits) {
        BigDecimal contract = contractSalaryBase != null ? contractSalaryBase : BigDecimal.ZERO;
        BigDecimal bonusIn = monthBonus != null ? monthBonus : BigDecimal.ZERO;
        BigDecimal units = workDayUnits != null ? workDayUnits : BigDecimal.ZERO;
        BigDecimal std = standardWorkDaysPerMonth != null && standardWorkDaysPerMonth.compareTo(BigDecimal.ZERO) > 0
                ? standardWorkDaysPerMonth
                : new BigDecimal("26");
        BigDecimal effectiveUnits = units.min(std);
        if (effectiveUnits.compareTo(BigDecimal.ZERO) < 0) {
            effectiveUnits = BigDecimal.ZERO;
        }
        BigDecimal ratio = effectiveUnits.divide(std, 10, RoundingMode.HALF_UP);
        BigDecimal earnedBase = contract.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxable = earnedBase.add(bonusIn);
        BigDecimal tax = taxable.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal insurance = earnedBase.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal fin = earnedBase.add(bonusIn).subtract(other).subtract(tax).subtract(insurance)
                .setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[]{earnedBase, bonusIn, tax, insurance, other, fin};
    }

    private BigDecimal workUnitsForEmployee(Long employeeId, YearMonth ym) {
        return workDayUnitsForMonth(Collections.singleton(employeeId), ym).getOrDefault(employeeId, BigDecimal.ZERO);
    }

    public Page<SalaryHistoryResponse> listByMonth(String monthParam, Pageable pageable) {
        YearMonth ym = YearMonth.parse(monthParam.trim());
        LocalDate monthStart = ym.atDay(1);
        Page<SalaryHistory> page = salaryHistoryRepository.findBySalaryMonth(monthStart, pageable);
        Set<Long> ids = page.getContent().stream()
                .map(SalaryHistory::getEmployeeId)
                .collect(Collectors.toSet());
        Map<Long, Employee> empMap = loadEmployees(ids);
        Map<Long, BigDecimal> workUnits = workDayUnitsForMonth(ids, ym);
        return page.map(sh -> toResponse(sh, empMap.get(sh.getEmployeeId()),
                workUnits.getOrDefault(sh.getEmployeeId(), BigDecimal.ZERO)));
    }

    /**
     * Tổng công quy đổi trong tháng theo chấm công (xem {@link PayrollWorkDayHelper}).
     */
    public Map<Long, BigDecimal> workDayUnitsForMonth(Set<Long> employeeIds, YearMonth ym) {
        Map<Long, BigDecimal> out = new HashMap<>();
        if (employeeIds == null || employeeIds.isEmpty()) {
            return out;
        }
        for (Long id : employeeIds) {
            out.put(id, BigDecimal.ZERO);
        }
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<Attendance> rows = attendanceRepository.findByEmployeeIdInAndAttendanceDateBetween(
                employeeIds, from, to);
        for (Attendance a : rows) {
            out.merge(a.getEmployeeId(), PayrollWorkDayHelper.unitForStatus(a.getStatus()), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : out.entrySet()) {
            e.setValue(PayrollWorkDayHelper.sumUnitsRounded(e.getValue()));
        }
        return out;
    }

    @Transactional
    public PayrollGenerateResult generate(PayrollGenerateRequest request) {
        YearMonth ym = YearMonth.parse(request.getMonth().trim());
        LocalDate salaryMonth = ym.atDay(1);
        BigDecimal defaultBonus = request.getDefaultBonus() != null
                ? request.getDefaultBonus().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<Employee> active = employeeRepository.findByStatus(EmployeeStatus.ACTIVE);
        int created = 0;
        int skipped = 0;
        List<String> messages = new ArrayList<>();

        for (Employee e : active) {
            if (salaryHistoryRepository.existsByEmployeeIdAndSalaryMonth(e.getId(), salaryMonth)) {
                skipped++;
                continue;
            }
            BigDecimal base = e.getSalaryBase() != null ? e.getSalaryBase() : BigDecimal.ZERO;
            BigDecimal workUnits = workUnitsForEmployee(e.getId(), ym);
            BigDecimal[] parts = computePayrollParts(base, defaultBonus, workUnits);
            SalaryHistory sh = new SalaryHistory();
            sh.setEmployeeId(e.getId());
            sh.setSalaryBase(parts[0]);
            sh.setBonus(parts[1]);
            sh.setTax(parts[2]);
            sh.setInsurance(parts[3]);
            sh.setOtherDeduction(parts[4]);
            sh.setFinalSalary(parts[5]);
            sh.setSalaryMonth(salaryMonth);
            sh.setPaymentStatus(PaymentStatus.PENDING);
            BigDecimal std = standardWorkDaysPerMonth != null && standardWorkDaysPerMonth.compareTo(BigDecimal.ZERO) > 0
                    ? standardWorkDaysPerMonth
                    : new BigDecimal("26");
            BigDecimal perCong = std.compareTo(BigDecimal.ZERO) > 0
                    ? base.divide(std, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            sh.setNote(String.format(
                    "HĐ: %s; công: %s/%s; 1 công ≈ %s (HĐ÷%s). Thực nhận = (HĐ÷%s×công)+thưởng−khác−thuế−BHXH. Thuế 10%%×(lương theo công+thưởng), BHXH 2.5%%×lương theo công.",
                    base.toPlainString(),
                    workUnits.stripTrailingZeros().toPlainString(),
                    std.toPlainString(),
                    perCong.toPlainString(),
                    std.toPlainString(),
                    std.toPlainString()
            ));
            salaryHistoryRepository.save(sh);
            created++;

            notificationService.notifySalaryGenerated(
                    e.getId(),
                    e.getEmail(),
                    e.getFullName(),
                    ym.toString(),
                    parts[5]
            );
        }

        messages.add("Tháng " + ym + ": tạo mới " + created + ", bỏ qua (đã có) " + skipped);
        if (created == 0) {
            log.warn("Sinh bảng lương {}: không có bản ghi mới (tất cả đã tồn tại hoặc không có NV ACTIVE) — không gửi mail.", ym);
        } else {
            log.info("Sinh bảng lương {}: đã tạo {} bản ghi; mail gửi tới email từng nhân viên trong hồ sơ (employees.email).", ym, created);
        }
        return new PayrollGenerateResult(ym.toString(), created, skipped, messages);
    }

    /**
     * Xóa toàn bộ bản ghi lương của tháng (để sinh lại bảng lương).
     *
     * @return số dòng đã xóa
     */
    @Transactional
    public long deleteMonth(String monthParam) {
        if (monthParam == null || monthParam.isBlank()) {
            throw new IllegalArgumentException("Thiếu tham số tháng (định dạng yyyy-MM).");
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(monthParam.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Tháng không hợp lệ, dùng định dạng yyyy-MM.");
        }
        LocalDate monthStart = ym.atDay(1);
        int n = salaryHistoryRepository.deleteBySalaryMonth(monthStart);
        log.info("Đã xóa {} bản ghi salary_history tháng {}", n, ym);
        return n;
    }

    private Map<Long, Employee> loadEmployees(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, x -> x));
    }

    private SalaryHistoryResponse toResponse(SalaryHistory sh, Employee emp, BigDecimal workDayUnits) {
        String name = emp != null ? emp.getFullName() : "—";
        String code = emp != null ? emp.getEmployeeCode() : null;
        return new SalaryHistoryResponse(
                sh.getId(),
                sh.getEmployeeId(),
                name,
                code,
                sh.getSalaryBase(),
                sh.getBonus(),
                sh.getTax(),
                sh.getInsurance(),
                sh.getOtherDeduction(),
                workDayUnits != null ? workDayUnits : BigDecimal.ZERO,
                sh.getFinalSalary(),
                sh.getSalaryMonth(),
                sh.getPaymentStatus(),
                sh.getNote()
        );
    }
}
