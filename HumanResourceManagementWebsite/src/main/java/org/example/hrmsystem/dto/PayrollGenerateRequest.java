package org.example.hrmsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Generate bảng lương cho mọi nhân viên ACTIVE.
 * Thưởng mặc định áp dụng đồng loạt (có thể 0).
 */
public class PayrollGenerateRequest {

    @NotBlank
    @Pattern(regexp = "\\d{4}-\\d{2}", message = "month phải là yyyy-MM")
    private String month;

    /** Thưởng cố định cho mỗi nhân viên trong đợt generate (mặc định 0). */
    private BigDecimal defaultBonus;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public BigDecimal getDefaultBonus() {
        return defaultBonus;
    }

    public void setDefaultBonus(BigDecimal defaultBonus) {
        this.defaultBonus = defaultBonus;
    }
}
