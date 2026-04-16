package org.example.hrmsystem.dto;

import org.example.hrmsystem.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public class SalaryHistoryResponse {

    private Long id;
    private Long employeeId;
    private String employeeName;
    private String employeeCode;
    private BigDecimal salaryBase;
    private BigDecimal bonus;
    private BigDecimal tax;
    private BigDecimal insurance;
    private BigDecimal otherDeduction;
    /** Công quy đổi trong tháng lương (từ chấm công; minh họa, không nhân trực tiếp vào công thức demo). */
    private BigDecimal workDayUnits;
    private BigDecimal finalSalary;
    private LocalDate salaryMonth;
    private PaymentStatus paymentStatus;
    private String note;

    public SalaryHistoryResponse() {
    }

    public SalaryHistoryResponse(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            BigDecimal salaryBase,
            BigDecimal bonus,
            BigDecimal tax,
            BigDecimal insurance,
            BigDecimal otherDeduction,
            BigDecimal workDayUnits,
            BigDecimal finalSalary,
            LocalDate salaryMonth,
            PaymentStatus paymentStatus,
            String note
    ) {
        this.id = id;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.employeeCode = employeeCode;
        this.salaryBase = salaryBase;
        this.bonus = bonus;
        this.tax = tax;
        this.insurance = insurance;
        this.otherDeduction = otherDeduction;
        this.workDayUnits = workDayUnits;
        this.finalSalary = finalSalary;
        this.salaryMonth = salaryMonth;
        this.paymentStatus = paymentStatus;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public BigDecimal getSalaryBase() {
        return salaryBase;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public BigDecimal getInsurance() {
        return insurance;
    }

    public BigDecimal getOtherDeduction() {
        return otherDeduction;
    }

    public BigDecimal getWorkDayUnits() {
        return workDayUnits;
    }

    public void setWorkDayUnits(BigDecimal workDayUnits) {
        this.workDayUnits = workDayUnits;
    }

    public BigDecimal getFinalSalary() {
        return finalSalary;
    }

    public LocalDate getSalaryMonth() {
        return salaryMonth;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getNote() {
        return note;
    }
}
