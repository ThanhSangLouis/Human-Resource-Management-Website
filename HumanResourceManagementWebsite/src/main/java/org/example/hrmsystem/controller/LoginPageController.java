package org.example.hrmsystem.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    @GetMapping("/login")
    public String loginPage() {
        return "forward:/login.html";
    }

    /**
     * Redirect tránh forward nội bộ bị Security xử lý sai → 403. Link trong app dùng /overview.
     */
    @GetMapping("/overview")
    public String overviewPage() {
        return "redirect:/overview.html";
    }

    @GetMapping("/performance")
    public String performancePage() {
        return "redirect:/performance.html";
    }

    /** Tương thích link cũ */
    @GetMapping("/dashboard")
    public String legacyDashboard() {
        return "redirect:/overview";
    }
}
