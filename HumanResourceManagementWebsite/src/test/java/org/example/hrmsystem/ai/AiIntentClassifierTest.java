package org.example.hrmsystem.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AiIntentClassifierTest {

    private AiIntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new AiIntentClassifier(new AiGuardrailService());
    }

    @ParameterizedTest
    @CsvSource({
            "my leave, SELF_LEAVE",
            "MY LEAVE, SELF_LEAVE",
            "leave balance, SELF_LEAVE",
            "remaining leave, SELF_LEAVE",
            "phép của tôi, SELF_LEAVE",
            "phep cua toi con bao nhieu, SELF_LEAVE",
            "don cua toi, SELF_LEAVE",
            "đơn của tôi, SELF_LEAVE",
            "xin xem don nghi cua toi, SELF_LEAVE",
    })
    void selfLeave_shortPhrases(String message, AiIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @Test
    void selfLeave_beforeFaq_whenPolicyKeywordsAndPersonalLeave() {
        assertThat(classifier.classify("quy định về phép của tôi")).isEqualTo(AiIntent.SELF_LEAVE);
    }

    @ParameterizedTest
    @CsvSource({
            "approval queue, MGR_PENDING_LEAVE",
            "pending leave, MGR_PENDING_LEAVE",
            "team late today, MGR_TEAM_ATTENDANCE",
            "đi muộn team, MGR_TEAM_ATTENDANCE",
    })
    void managerIntents(String message, AiIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "tóm tắt thông báo, NOTIF_SUMMARY",
            "unread notifications summary, NOTIF_SUMMARY",
    })
    void notifSummary(String message, AiIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "quy định công ty về đạo đức, FAQ",
            "hr policy handbook, FAQ",
            "huong dan su dung he thong, FAQ",
            "quy định chấm công, FAQ",
            "quy định nghỉ phép, FAQ",
    })
    void faq_whenNoStrongerSelfSignal(String message, AiIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "chấm công hôm nay, SELF_ATTENDANCE",
            "check-in today, SELF_ATTENDANCE",
            "thông báo của tôi, SELF_NOTIFICATIONS",
            "notifications, SELF_NOTIFICATIONS",
    })
    void selfAttendanceAndNotif(String message, AiIntent expected) {
        assertThat(classifier.classify(message)).isEqualTo(expected);
    }

    @Test
    void employeeLookup_whenNbspBetweenWords_notChitchat() {
        String nbsp = "\u00A0";
        String message = "cho" + nbsp + "tôi" + nbsp + "thông" + nbsp + "tin" + nbsp + "Dinh" + nbsp + "Thi" + nbsp + "Yen";
        assertThat(classifier.classify(message)).isEqualTo(AiIntent.HR_EMPLOYEE_LOOKUP);
    }

    @Test
    void employeeLookup_plainSpaces() {
        assertThat(classifier.classify("cho tôi thông tin Dinh Thi Yen")).isEqualTo(AiIntent.HR_EMPLOYEE_LOOKUP);
    }
}
