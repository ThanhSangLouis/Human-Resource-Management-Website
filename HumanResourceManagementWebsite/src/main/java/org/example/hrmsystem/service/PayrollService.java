package org.example.hrmsystem.service;

import org.example.hrmsystem.dto.PayrollGenerateRequest;
import org.example.hrmsystem.dto.PayrollGenerateResult;
import org.example.hrmsystem.dto.SalaryHistoryResponse;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.EmployeeStatus;
import org.example.hrmsystem.model.PaymentStatus;
import org.example.hrmsystem.model.SalaryHistory;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.SalaryHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PayrollService {

    private static final Logger log = LoggerFactory.getLogger(PayrollService.class);

    private final SalaryHistoryRepository salaryHistoryRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    public PayrollService(
            SalaryHistoryRepository salaryHistoryRepository,
            EmployeeRepository employeeRepository,
            NotificationService notificationService
    ) {
        this.salaryHistoryRepository = salaryHistoryRepository;
        this.employeeRepository = employeeRepository;
        this.notificationService = notificationService;
    }

    /**
     * Công thức (demo, đồng bộ schema):
     * {@code final_salary = salary_base + bonus - tax - insurance - other_deduction}
     * <ul>
     *   <li>Thuế TNCN (demo): 10% × (salary_base + bonus)</li>
     *   <li>BHXH/BHYT/BHTN (demo gộp): 2.5% × salary_base</li>
     *   <li>other_deduction = 0</li>
     * </ul>
     */
    public static BigDecimal[] computeComponents(BigDecimal salaryBase, BigDecimal bonus) {
        BigDecimal base = salaryBase != null ? salaryBase : BigDecimal.ZERO;
        BigDecimal b = bonus != null ? bonus : BigDecimal.ZERO;
        BigDecimal taxable = base.add(b);
        BigDecimal tax = taxable.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal insurance = base.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal other = BigDecimal.ZERO;
        BigDecimal fin = base.add(b).subtract(tax).subtract(insurance).subtract(other)
                .setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[]{base, b, tax, insurance, other, fin};
    }

    public Page<SalaryHistoryResponse> listByMonth(String monthParam, Pageable pageable) {
        YearMonth ym = YearMonth.parse(monthParam.trim());
        LocalDate monthStart = ym.atDay(1);
        Page<SalaryHistory> page = salaryHistoryRepository.findBySalaryMonth(monthStart, pageable);
        Map<Long, Employee> empMap = loadEmployees(page.getContent().stream()
                .map(SalaryHistory::getEmployeeId)
                .collect(Collectors.toSet()));
        return page.map(sh -> toResponse(sh, empMap.get(sh.getEmployeeId())));
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
            BigDecimal[] parts = computeComponents(base, defaultBonus);
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
            sh.setNote("Sinh tự động — demo thuế 10% (base+bonus), BHXH gộp 2.5% base");
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

    private Map<Long, Employee> loadEmployees(java.util.Set<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return employeeRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, x -> x));
    }

    private SalaryHistoryResponse toResponse(SalaryHistory sh, Employee emp) {
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
                sh.getFinalSalary(),
                sh.getSalaryMonth(),
                sh.getPaymentStatus(),
                sh.getNote()
        );
    }
}
