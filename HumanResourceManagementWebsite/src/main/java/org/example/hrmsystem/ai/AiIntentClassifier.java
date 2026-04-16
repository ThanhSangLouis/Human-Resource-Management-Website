package org.example.hrmsystem.ai;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phân loại intent theo rule keyword (MVP). Self-service được ưu tiên trước FAQ để tránh rơi FAQ với câu cá nhân.
 */
@Component
public class AiIntentClassifier {

    private static final Set<AiIntent> MANAGER_INTENTS = Set.of(
            AiIntent.MGR_PENDING_LEAVE,
            AiIntent.MGR_TEAM_ATTENDANCE,
            AiIntent.DASHBOARD_STATS,
            AiIntent.HR_EMPLOYEE_LOOKUP,   // Manager chỉ xem trong phạm vi team
            AiIntent.HR_EMPLOYEE_DETAILS   // Follow-up chi tiết cũng nằm trong scope team
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
    private static final String[] KW_STATS = {
            // Tiếng Việt
            "thống kê", "thong ke", "tổng quan", "tong quan", "báo cáo", "bao cao",
            "số nhân viên", "so nhan vien", "tổng nhân viên", "tong nhan vien",
            "phòng ban có", "nhân sự", "nhan su",
            "tỷ lệ chấm công", "ty le cham cong", "tỷ lệ đi làm", "ty le di lam",
            "bao nhiêu nhân viên", "bao nhieu nhan vien",
            "số liệu", "so lieu", "dashboard", "overview", "statistic",
            // Hỏi tháng này cụ thể (mà không kèm cụm self-service)
            "tháng này có bao nhiêu", "thang nay co bao nhieu"
    };
    /**
     * {@code hi}/{@code hey} không dùng substring — tránh nhầm với tên tiếng Việt kiểu "Thi"
     * (chuỗi {@code thi } chứa {@code hi }).
     */
    private static final Pattern STANDALONE_HI_OR_HEY = Pattern.compile(
            "(?u)(?<![\\p{L}])hi(?![\\p{L}])|(?<![\\p{L}])hey(?![\\p{L}])");

    /** Chat xã giao, chào hỏi, cảm ơn — không load policy */
    private static final String[] KW_CHITCHAT = {
            // chào (hi/hey xử lý bằng STANDALONE_HI_OR_HEY)
            "xin chào", "xin chao", "chào bạn", "chao ban", "hello",
            "good morning", "good afternoon", "good evening", "chào buổi",
            // cảm ơn
            "cảm ơn", "cam on", "thanks", "thank you", "camon", "tks", "thx",
            // hỏi về bot
            "bạn là ai", "ban la ai", "giới thiệu bản thân", "bác có thể giúp",
            "bạn có thể giúp", "can you help", "what can you do", "bạn giúp được gì",
            "bạn có thể làm gì", "khả năng của bạn",
            // ok, được rồi
            "ok", "okay", "được rồi", "duoc roi", "hiểu rồi", "hieu roi", "rồi", "nice", "great",
            "tốt", "tuyệt", "tuyet"
    };
    /** Tra cứu hồ sơ nhân viên theo tên / mã (♥ HR, Admin, Manager) */
    private static final String[] KW_EMP_LOOKUP = {
            // Tiếng Việt — có cụm "nhân viên"
            "thông tin nhân viên", "thong tin nhan vien",
            "hồ sơ nhân viên", "ho so nhan vien",
            "tìm nhân viên", "tim nhan vien",
            "tra cứu nhân viên", "tra cuu nhan vien",
            "tìm kiếm nhân viên", "tim kiem nhan vien",
            // Mẫu tự nhiên: "cho tôi / xem / biết / hiển thị thông tin ..."
            "cho tôi thông tin", "cho toi thong tin",
            "xem thông tin", "xem thong tin",
            "biết thông tin", "biet thong tin",
            "thông tin về", "thong tin ve",
            "thông tin của", "thong tin cua",
            "hồ sơ của", "ho so cua",
            // Thuộc tính cụ thể
            "mã nhân viên", "ma nhan vien",
            "employee code", "employees named",
            "chức vụ của", "chuc vu cua",
            "phòng ban của", "phong ban cua",
            "email của", "email cua",
            "số điện thoại của", "so dien thoai cua",
            "ngày sinh của", "ngay sinh cua",
            "ngày vào công ty của", "ngay vao cong ty cua",
            "lương cơ bản của", "luong co ban cua",
            // Tiếng Anh
            "lookup employee", "find employee", "search employee"
    };

    /** Follow-up đòi "chi tiết hơn" sau khi đã tra cứu nhân viên */
    private static final String[] KW_EMP_DETAILS = {
            "chi tiết", "chi tiet", "cụ thể", "cu the", "chi tiết hơn", "chi tiet hon",
            "thêm chi tiết", "them chi tiet", "more details", "details please", "detail",
            "cần chi tiết", "can chi tiet", "xem chi tiết", "xem chi tiet"
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

        // CHITCHAT tr\u01b0\u1edbc ti\u00ean \u2014 ch\u00e0o h\u1ecfi / x\u00e3 giao kh\u00f4ng \u0111\u01b0\u1ee3c r\u01a1i v\u00e0o FAQ
        // Guard: câu hỏi policy/rules không được rơi vào CHITCHAT do trùng từ ngắn.
        if ((STANDALONE_HI_OR_HEY.matcher(n).find() || containsAny(n, KW_CHITCHAT))
                && !looksLikePolicyOrRulesQuestion(n)) {
            return AiIntent.CHITCHAT;
        }

        if (containsAny(n, KW_MGR_PENDING)) {
            return AiIntent.MGR_PENDING_LEAVE;
        }
        if (containsAny(n, KW_MGR_LATE)) {
            return AiIntent.MGR_TEAM_ATTENDANCE;
        }
        if (containsAny(n, KW_STATS)) {
            return AiIntent.DASHBOARD_STATS;
        }
        if (containsAny(n, KW_SUMMARY)) {
            return AiIntent.NOTIF_SUMMARY;
        }
        if (containsAny(n, KW_EMP_DETAILS)) {
            return AiIntent.HR_EMPLOYEE_DETAILS;
        }
        /*
         * C\u00e2u h\u1ecfi quy \u0111\u1ecbnh / policy / h\u01b0\u1edbng d\u1eabn chung ph\u1ea3i l\u1ea5y FAQ (policy .md) tr\u01b0\u1edbc,
         * k\u1ebb\u1edd "quy \u0111\u1ecbnh ch\u1ea5m c\u00f4ng" b\u1ecb nu\u1ed1t b\u1edfi intent ch\u1ea5m c\u00f4ng c\u00e1 nh\u00e2n.
         * N\u1ebfu user n\u00f3i r\u00f5 "\u1ee7a t\u00f4i" + d\u1eef li\u1ec7u c\u00e1 nh\u00e2n \u2192 v\u1eabn self-service.
         */
        if (looksLikePolicyOrRulesQuestion(n) && !clearlyPersonalDataScope(n)) {
            return AiIntent.FAQ;
        }
        // Self-service tr\u01b0\u1edbc FAQ m\u1eb7c \u0111\u1ecbnh
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
        // Tra cứu hồ sơ nhân viên — siết trước fallback
        if (containsAny(n, KW_EMP_LOOKUP)) {
            return AiIntent.HR_EMPLOYEE_LOOKUP;
        }
        // Message ngắn không khớp intent nghiệp vụ phía trên → xã giao (không đặt rule này trước MGR/SELF)
        long wordCount = n.isBlank() ? 0 : n.trim().split("\\s+").length;
        if (wordCount <= 5) {
            return AiIntent.CHITCHAT;
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
