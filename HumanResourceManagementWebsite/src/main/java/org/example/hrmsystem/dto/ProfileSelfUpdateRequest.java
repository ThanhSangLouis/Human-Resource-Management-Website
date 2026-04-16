package org.example.hrmsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Cập nhật hồ sơ cá nhân (NV tự sửa) — không gồm mã NV, phòng ban, lương, trạng thái. */
public class ProfileSelfUpdateRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    private String fullName;

    @Size(max = 150)
    private String email;

    @Size(max = 50)
    @Pattern(regexp = "^$|^[0-9]+$", message = "Số điện thoại chỉ được chứa chữ số (không âm)")
    private String phone;

    /** MALE / FEMALE / OTHER hoặc để trống */
    @Size(max = 20)
    private String gender;

    private LocalDate dateOfBirth;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }
}
