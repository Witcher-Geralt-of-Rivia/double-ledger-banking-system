package com.bank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.entity.RefreshToken;
import com.bank.entity.Role;
import com.bank.entity.User;
import com.bank.entity.UserSession;
import com.bank.repository.RefreshTokenRepository;
import com.bank.repository.RoleRepository;
import com.bank.repository.UserRepository;
import com.bank.repository.UserSessionRepository;
import com.bank.security.JwtUtil;
import com.bank.service.security.SecurityService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Drives the real authentication endpoints through the security filter chain to
 * cover the refresh flow end to end: a refreshed access token must be usable,
 * and a refresh must never outlive, multiply or revive the session it belongs
 * to.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthRefreshFlowTest {

  private static final String PASSWORD = "Refresh-flow-pass-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private UserSessionRepository userSessionRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SecurityService securityService;
  @Autowired private JwtUtil jwtUtil;

  @Value("${jwt.secret}")
  private String jwtSecret;

  private User user;

  @BeforeEach
  void createUser() {
    Role role =
        roleRepository
            .findByName(Role.RoleName.ROLE_USER)
            .orElseGet(
                () -> {
                  Role created = new Role();
                  created.setName(Role.RoleName.ROLE_USER);
                  return roleRepository.save(created);
                });

    String username = "refresh-" + UUID.randomUUID().toString().substring(0, 8);
    User created = new User();
    created.setUsername(username);
    created.setEmail(username + "@example.com");
    created.setPassword(passwordEncoder.encode(PASSWORD));
    created.setFullName("Refresh Flow");
    created.setPhoneNumber("+10000000000");
    created.setActive(true);
    created.setLocked(false);
    created.setLastLogin(LocalDateTime.now());
    Set<Role> roles = new HashSet<>();
    roles.add(role);
    created.setRoles(roles);
    user = userRepository.save(created);
  }

  @Test
  void refreshedAccessTokenIsAcceptedByProtectedEndpoints() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();
    me(access).andExpect(status().isOk());

    JsonNode refreshed = body(refresh(refresh).andExpect(status().isOk()));
    String newAccess = refreshed.get("accessToken").asText();
    String newRefresh = refreshed.get("refreshToken").asText();

    assertThat(newAccess).isNotBlank().isNotEqualTo(access);
    assertThat(newRefresh).isNotBlank().isNotEqualTo(refresh);
    me(newAccess)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(user.getUsername()));
  }

  @Test
  void refreshKeepsOneSessionAndRetiresThePreviousAccessToken() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String sessionId = sessionOf(access).getId();

    String newAccess =
        body(refresh(login.get("refreshToken").asText()).andExpect(status().isOk()))
            .get("accessToken")
            .asText();

    me(access).andExpect(status().isForbidden());
    List<UserSession> active = userSessionRepository.findByUserIdAndActiveTrue(user.getId());
    assertThat(active).hasSize(1);
    assertThat(active.get(0).getId()).isEqualTo(sessionId);
    assertThat(active.get(0).getTokenId()).isEqualTo(jwtUtil.extractTokenId(newAccess));
  }

  @Test
  void refreshCanBeRepeatedAlongTheRotationChain() throws Exception {
    JsonNode current = login();
    for (int i = 0; i < 3; i++) {
      current = body(refresh(current.get("refreshToken").asText()).andExpect(status().isOk()));
      me(current.get("accessToken").asText()).andExpect(status().isOk());
    }
    assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())).hasSize(1);
  }

  @Test
  void reusingARotatedRefreshTokenIsRejectedAndRevokesTokensAndSessions() throws Exception {
    JsonNode login = login();
    String firstRefresh = login.get("refreshToken").asText();
    JsonNode rotated = body(refresh(firstRefresh).andExpect(status().isOk()));

    refresh(firstRefresh)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Refresh token has been revoked. Please log in again."));

    // The revocation has to survive the rejection: the successor token and the
    // session must both be gone.
    assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())).isEmpty();
    refresh(rotated.get("refreshToken").asText()).andExpect(status().isBadRequest());
    me(rotated.get("accessToken").asText()).andExpect(status().isForbidden());
    assertThat(userSessionRepository.findByUserIdAndActiveTrue(user.getId())).isEmpty();
  }

  @Test
  void refreshIsRejectedAfterLogout() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    mockMvc
        .perform(
            post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refresh))))
        .andExpect(status().isNoContent());

    refresh(refresh).andExpect(status().isBadRequest());
    me(access).andExpect(status().isForbidden());
  }

  @Test
  void refreshCannotReviveASessionLoggedOutWithoutPresentingTheRefreshToken() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    mockMvc
        .perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + access))
        .andExpect(status().isNoContent());

    refresh(refresh)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Session is no longer active. Please log in again."));
    assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())).isEmpty();
    assertThat(userSessionRepository.findByUserIdAndActiveTrue(user.getId())).isEmpty();
  }

  @Test
  void refreshCannotReviveASessionTerminatedByAnAdministrator() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    securityService.terminateSession(sessionOf(access).getId());

    me(access).andExpect(status().isForbidden());
    refresh(refresh).andExpect(status().isBadRequest());
    assertThat(refreshTokenRepository.findByUserIdAndRevokedFalse(user.getId())).isEmpty();
    assertThat(userSessionRepository.findByUserIdAndActiveTrue(user.getId())).isEmpty();
  }

  @Test
  void expiredRefreshTokenIsRejectedAndLeavesTheSessionAlone() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();
    String refresh = login.get("refreshToken").asText();

    RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256Hex(refresh)).orElseThrow();
    stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
    refreshTokenRepository.save(stored);

    refresh(refresh)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Refresh token expired"));
    me(access).andExpect(status().isOk());
  }

  @Test
  void malformedAndUnknownRefreshTokensAreRejected() throws Exception {
    JsonNode login = login();
    String access = login.get("accessToken").asText();

    refresh("not-a-jwt").andExpect(status().isBadRequest());
    refresh(" ").andExpect(status().isBadRequest());

    // Correctly signed and correctly bound, but never issued by the service.
    String neverIssued = jwtUtil.generateRefreshToken(user, access);
    refresh(neverIssued)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Unknown refresh token"));

    me(access).andExpect(status().isOk());
  }

  @Test
  void refreshTokenWithoutSessionBindingIsRejected() throws Exception {
    login();
    String unbound = signedRefreshTokenWithoutBinding();
    refreshTokenRepository.save(
        RefreshToken.builder()
            .jti(jwtUtil.extractTokenId(unbound))
            .tokenHash(sha256Hex(unbound))
            .userId(user.getId())
            .username(user.getUsername())
            .issuedAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusDays(1))
            .revoked(false)
            .build());

    refresh(unbound).andExpect(status().isBadRequest());

    assertThat(refreshTokenRepository.findByTokenHash(sha256Hex(unbound)).orElseThrow().isRevoked())
        .isTrue();
    assertThat(userSessionRepository.findByUserIdAndActiveTrue(user.getId())).hasSize(1);
  }

  @Test
  void refreshTokenIsNotAcceptedAsAnAccessToken() throws Exception {
    JsonNode login = login();

    me(login.get("refreshToken").asText()).andExpect(status().isForbidden());
  }

  private JsonNode login() throws Exception {
    return body(
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("username", user.getUsername(), "password", PASSWORD))))
            .andExpect(status().isOk()));
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(
        post("/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))));
  }

  private ResultActions me(String bearerToken) throws Exception {
    return mockMvc.perform(
        get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
  }

  private JsonNode body(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
  }

  private UserSession sessionOf(String accessToken) {
    return userSessionRepository.findByTokenId(jwtUtil.extractTokenId(accessToken)).orElseThrow();
  }

  /** A refresh token as issued before tokens were bound to a session. */
  private String signedRefreshTokenWithoutBinding() {
    return Jwts.builder()
        .setId(UUID.randomUUID().toString())
        .setSubject(user.getUsername())
        .setIssuedAt(new Date())
        .setExpiration(new Date(System.currentTimeMillis() + 3_600_000))
        .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)), SignatureAlgorithm.HS256)
        .compact();
  }

  private static String sha256Hex(String input) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
  }
}
