package com.bank.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bank.entity.Role;
import com.bank.entity.User;
import com.bank.repository.RoleRepository;
import com.bank.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A caller whose role does not satisfy a method-level @PreAuthorize rule must be
 * answered with 403 Forbidden, not with the 500 of the catch-all handler. The
 * rules themselves are unchanged: the permitted roles still get through.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccessDeniedResponseTest {

  private static final String PASSWORD = "Access-denied-pass-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @Test
  void insufficientRoleIsAnsweredWithForbidden() throws Exception {
    String token = loginAs(Role.RoleName.ROLE_USER);

    mockMvc
        .perform(get("/audit/logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.statusCode").value(403))
        .andExpect(jsonPath("$.error.errorCode").value("ACCESS_DENIED"));
  }

  @Test
  void insufficientRoleIsForbiddenOnAnotherGuardedEndpoint() throws Exception {
    String token = loginAs(Role.RoleName.ROLE_MANAGER);

    mockMvc
        .perform(get("/audit/logs/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.errorCode").value("ACCESS_DENIED"));
  }

  @Test
  void permittedRolesAreStillAllowed() throws Exception {
    for (Role.RoleName permitted : new Role.RoleName[] {Role.RoleName.ROLE_ADMIN, Role.RoleName.ROLE_AUDITOR}) {
      String token = loginAs(permitted);

      mockMvc
          .perform(get("/audit/logs").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isOk());
    }
  }

  @Test
  void missingTokenIsStillRejectedBeforeTheController() throws Exception {
    mockMvc.perform(get("/audit/logs")).andExpect(status().isForbidden());
  }

  private String loginAs(Role.RoleName roleName) throws Exception {
    Role role =
        roleRepository
            .findByName(roleName)
            .orElseGet(
                () -> {
                  Role created = new Role();
                  created.setName(roleName);
                  return roleRepository.save(created);
                });

    String username = "denied-" + UUID.randomUUID().toString().substring(0, 8);
    User created = new User();
    created.setUsername(username);
    created.setEmail(username + "@example.com");
    created.setPassword(passwordEncoder.encode(PASSWORD));
    created.setFullName("Access Denied");
    created.setPhoneNumber("+10000000000");
    created.setActive(true);
    created.setLocked(false);
    created.setLastLogin(LocalDateTime.now());
    Set<Role> roles = new HashSet<>();
    roles.add(role);
    created.setRoles(roles);
    userRepository.save(created);

    String response =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            Map.of("username", username, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("accessToken").asText();
  }
}
