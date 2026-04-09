package org.example.hrmsystem.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.example.hrmsystem.model.Employee;
import org.example.hrmsystem.model.Role;
import org.example.hrmsystem.model.UserAccount;
import org.example.hrmsystem.repository.EmployeeRepository;
import org.example.hrmsystem.repository.UserAccountRepository;
import org.example.hrmsystem.security.AppUserDetails;
import org.example.hrmsystem.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AiChatIntegrationTest {

    private static final JsonMapper MAPPER = JsonMapper.shared();

    @LocalServerPort
    int port;

    @Autowired
    UserAccountRepository userAccountRepository;
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtService jwtService;
    @Autowired
    AiChatAuditRepository auditRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String employeeJwt;
    private Long employeeUserId;

    @BeforeEach
    void setUp() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setFullName("AI Integration");
        e.setEmployeeCode("AI-INT-" + id);
        e.setEmail("ai-int-" + id + "@hrm.test");
        e = employeeRepository.save(e);

        UserAccount u = new UserAccount();
        u.setUsername("ai_int_" + id);
        u.setPassword(passwordEncoder.encode("pw"));
        u.setRole(Role.EMPLOYEE);
        u.setEmployeeId(e.getId());
        u.setActive(true);
        u = userAccountRepository.save(u);
        employeeUserId = u.getId();
        employeeJwt = jwtService.generateToken(new AppUserDetails(u));
    }

    @Test
    void geminiDisabled_returnsOkWithFallbackAndIntent() throws Exception {
        long before = auditRepository.countByUserId(employeeUserId);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + employeeJwt)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"message\":\"quy định nghỉ phép\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(res.body());
        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("intent").asText()).isEqualTo("FAQ");
        assertThat(body.path("reply").asText()).isNotBlank();
        assertThat(body.path("dataSnapshot").isObject()).isTrue();

        assertThat(auditRepository.countByUserId(employeeUserId)).isEqualTo(before + 1);
        AiChatAudit row = auditRepository.findFirstByUserIdOrderByIdDesc(employeeUserId).orElseThrow();
        assertThat(row.getOutcome()).isEqualTo(AiChatAuditOutcome.SUCCESS);
        assertThat(row.isFallbackFlag()).isTrue();
        assertThat(row.getIntent()).isEqualTo("FAQ");
    }

    @Test
    void myLeave_mapsSelfLeave_notFaq() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + employeeJwt)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"my leave\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(res.body());
        assertThat(body.path("intent").asText()).isEqualTo("SELF_LEAVE");
        assertThat(body.path("fallback").asBoolean()).isTrue();

        AiChatAudit row = auditRepository.findFirstByUserIdOrderByIdDesc(employeeUserId).orElseThrow();
        assertThat(row.getIntent()).isEqualTo("SELF_LEAVE");
        assertThat(row.getOutcome()).isEqualTo(AiChatAuditOutcome.SUCCESS);
    }

    @Test
    void employeeAsksManagerQueue_returns403_andAudit() throws Exception {
        long before = auditRepository.countByUserId(employeeUserId);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + employeeJwt)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"approval queue\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(403);
        JsonNode body = MAPPER.readTree(res.body());
        JsonNode msg = body.path("message");
        assertThat(msg.isMissingNode() || msg.isNull() ? "" : msg.asString()).isNotBlank();

        assertThat(auditRepository.countByUserId(employeeUserId)).isEqualTo(before + 1);
        AiChatAudit row = auditRepository.findFirstByUserIdOrderByIdDesc(employeeUserId).orElseThrow();
        assertThat(row.getOutcome()).isEqualTo(AiChatAuditOutcome.FORBIDDEN);
        assertThat(row.getHttpStatus()).isEqualTo(403);
    }

    @Test
    void managerApprovalQueue_returns200_correctIntent_andAudit() throws Exception {
        String id = UUID.randomUUID().toString().substring(0, 8);
        Employee e = new Employee();
        e.setFullName("AI Manager");
        e.setEmployeeCode("AI-MGR-" + id);
        e.setEmail("ai-mgr-" + id + "@hrm.test");
        e = employeeRepository.save(e);

        UserAccount mgr = new UserAccount();
        mgr.setUsername("ai_mgr_" + id);
        mgr.setPassword(passwordEncoder.encode("pw"));
        mgr.setRole(Role.MANAGER);
        mgr.setEmployeeId(e.getId());
        mgr.setActive(true);
        mgr = userAccountRepository.save(mgr);
        String mgrJwt = jwtService.generateToken(new AppUserDetails(mgr));

        long before = auditRepository.countByUserId(mgr.getId());
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + mgrJwt)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"approval queue\"}", StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(res.body());
        assertThat(body.path("intent").asText()).isEqualTo("MGR_PENDING_LEAVE");

        assertThat(auditRepository.countByUserId(mgr.getId())).isEqualTo(before + 1);
        AiChatAudit row = auditRepository.findFirstByUserIdOrderByIdDesc(mgr.getId()).orElseThrow();
        assertThat(row.getIntent()).isEqualTo("MGR_PENDING_LEAVE");
        assertThat(row.getOutcome()).isEqualTo(AiChatAuditOutcome.SUCCESS);
    }
}
