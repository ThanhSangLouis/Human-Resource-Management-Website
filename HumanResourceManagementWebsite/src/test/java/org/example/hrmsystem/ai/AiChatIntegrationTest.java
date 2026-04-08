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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String employeeJwt;

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

        employeeJwt = jwtService.generateToken(new AppUserDetails(u));
    }

    @Test
    void geminiDisabled_returnsOkWithFallbackAndIntent() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + employeeJwt)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"quy định nghỉ phép\"}"))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(res.body());
        assertThat(body.path("fallback").asBoolean()).isTrue();
        assertThat(body.path("intent").asText()).isEqualTo("FAQ");
        assertThat(body.path("reply").asText()).isNotBlank();
        assertThat(body.path("dataSnapshot").isObject()).isTrue();
    }

    @Test
    void employeeAsksManagerQueue_returns403() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/ai/chat"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + employeeJwt)
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"approval queue\"}"))
                .build();

        HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

        assertThat(res.statusCode()).isEqualTo(403);
        JsonNode body = MAPPER.readTree(res.body());
        JsonNode msg = body.path("message");
        assertThat(msg.isMissingNode() || msg.isNull() ? "" : msg.asString()).isNotBlank();
    }
}
