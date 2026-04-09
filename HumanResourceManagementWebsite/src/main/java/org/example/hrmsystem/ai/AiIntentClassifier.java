package org.example.hrmsystem.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Phân loại intent theo rule keyword (MVP). Self-service được ưu tiên trước FAQ để tránh rơi FAQ với câu cá nhân.
 */
@Component
public class AiIntentClassifier {

    private static final Set<AiIntent> MANAGER_INTENTS = Set.of(
            AiIntent.MGR_PENDING_LEAVE,
            AiIntent.MGR_TEAM_ATTENDANCE
    );

    private static final String[] KW_MGR_PENDING = {
            "pending leave", "approval queue", "danh sách đơn chờ", "danh sach don cho",
            "đơn chờ của team", "don cho cua team", "duyệt đơn nhân viên", "duyet don nhan vien",
            "hàng đợi duyệt", "hang doi duyet", "manager pending", "ds đơn nghỉ", "ds don nghi"
    };
    private static final String[] KW_MGR_LATE = {
            "team late", "đi muộn team", "di muon team", "nhân viên muộn", "nhan vien muon",
            "late employees", "chấm công muộn nhóm"
    };
    private static final String[] KW_SUMMARY = {
            "tóm tắt", "tom tat", "summary", "unread", "chưa đọc", "chua doc", "bullet"
    };
    /** FAQ / policy — sau self-service để câu cá nhân không bị nuốt bởi "quy định" chung */
    private static final String[] KW_FAQ = {
            "quy định", "quy dinh", "policy", "faq", "luật", "luat", "hướng dẫn", "huong dan",
            "annual rule", "ngày phép năm", "ngay phep nam", "công ty cho", "cong ty cho"
    };
    private static final String[] KW_SELF_LEAVE = {
            "đơn nghỉ", "don nghi", "leave request", "ngày phép", "ngay phep", "phép còn", "phep con",
            "annual leave", "xin nghỉ", "xin nghi",
            "my leave", "my annual", "leave balance", "remaining leave", "days off",
            "số ngày phép", "so ngay phep", "phép của tôi", "phep cua toi", "đơn của tôi", "don cua toi",
            "nghỉ phép của tôi", "nghi phep cua toi", "quota phép", "quota phep"
    };
    private static final String[] KW_SELF_ATT = {
            "chấm công", "cham cong", "check-in", "check in", "checkout", "check-out", "đi muộn hôm nay",
            "di muon hom nay", "attendance", "ca làm", "ca lam", "vào ca", "vao ca"
    };
    private static final String[] KW_SELF_NOTIF = {
            "thông báo", "thong bao", "notification", "tin nhắn hệ thống", "tin nhan he thong"
    };

    private final AiGuardrailService guardrail;

    public AiIntentClassifier(AiGuardrailService guardrail) {
        this.guardrail = guardrail;
    }

    public Set<AiIntent> managerIntents() {
        return MANAGER_INTENTS;
    }

    public AiIntent classify(String message) {
        String n = guardrail.normalizeForMatch(message);
        if (containsAny(n, KW_MGR_PENDING)) {
            return AiIntent.MGR_PENDING_LEAVE;
        }
        if (containsAny(n, KW_MGR_LATE)) {
            return AiIntent.MGR_TEAM_ATTENDANCE;
        }
        if (containsAny(n, KW_SUMMARY)) {
            return AiIntent.NOTIF_SUMMARY;
        }
        /*
         * Câu hỏi quy định / policy / hướng dẫn chung phải lấy FAQ (policy .md) trước,
         * kẻo "quy định chấm công" bị nuốt bởi intent chấm công cá nhân.
         * Nếu user nói rõ "của tôi" + dữ liệu cá nhân → vẫn self-service.
         */
        if (looksLikePolicyOrRulesQuestion(n) && !clearlyPersonalDataScope(n)) {
            return AiIntent.FAQ;
        }
        // Self-service trước FAQ mặc định
        if (containsAny(n, KW_SELF_LEAVE) || looksLikePersonalLeaveQuery(n)) {
            return AiIntent.SELF_LEAVE;
        }
        if (containsAny(n, KW_SELF_ATT)) {
            return AiIntent.SELF_ATTENDANCE;
        }
        if (containsAny(n, KW_SELF_NOTIF)) {
            return AiIntent.SELF_NOTIFICATIONS;
        }
        if (containsAny(n, KW_FAQ)) {
            return AiIntent.FAQ;
        }
        return AiIntent.FAQ;
    }

    private static boolean looksLikePolicyOrRulesQuestion(String n) {
        return containsAny(n, KW_FAQ)
                || n.contains("policy")
                || n.contains("handbook")
                || n.contains("compliance")
                || n.contains("nội quy")
                || n.contains("noi quy");
    }

    /**
     * Có chỉ rõ dữ liệu cá nhân (đơn/phép/chấm công/thông báo của tôi) → không ép FAQ.
     */
    private static boolean clearlyPersonalDataScope(String n) {
        if (looksLikePersonalLeaveQuery(n)) {
            return true;
        }
        boolean mine = n.contains("của tôi") || n.contains("cua toi")
                || n.contains("của em") || n.contains("cua em");
        if (!mine) {
            return false;
        }
        return n.contains("phép") || n.contains("phep") || n.contains("nghỉ") || n.contains("nghi")
                || n.contains("chấm") || n.contains("cham") || n.contains("đơn") || n.contains("don")
                || n.contains("thông báo") || n.contains("thong bao")
                || n.contains("leave") || n.contains("attendance") || n.contains("notification");
    }

    /**
     * Câu mang tính “của tôi” + từ khóa phép/nghỉ → self leave, tránh FAQ.
     */
    private static boolean looksLikePersonalLeaveQuery(String n) {
        boolean possessive = n.contains("cua toi") || n.contains("của tôi") || n.contains("my ");
        if (!possessive) {
            return false;
        }
        return n.contains("phep") || n.contains("phép") || n.contains("nghi") || n.contains("nghỉ")
                || n.contains("leave") || n.contains("don ") || n.contains("đơn");
    }

    private static boolean containsAny(String normalized, String[] keys) {
        for (String k : keys) {
            if (normalized.contains(k.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
