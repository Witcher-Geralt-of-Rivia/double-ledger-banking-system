package com.bank.service.security;

import com.bank.dto.security.AccessLogEntryDTO;
import com.bank.dto.security.SessionInfoDTO;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;

public interface SecurityService {
  void recordSuccessfulLogin(
      Long userId, String username, String accessToken, HttpServletRequest request);

  void recordFailedLogin(String username, HttpServletRequest request);

  void recordPasswordChange(String username, HttpServletRequest request);

  void recordLogout(String username, String accessToken, HttpServletRequest request);

  List<SessionInfoDTO> getSessions();

  void terminateSession(String sessionId);

  void terminateAllSessions(boolean excludeCurrent, String currentAccessToken);

  List<AccessLogEntryDTO> getAccessLogs(
      LocalDateTime startDate, LocalDateTime endDate, String eventType);

  boolean isAccessTokenSessionActive(String accessToken);

  /**
   * Moves the active session currently held by the access token with id
   * {@code previousAccessTokenId} onto {@code newAccessToken}, so the new token
   * is accepted and the previous one no longer is.
   *
   * @return {@code false} when that user has no active session for the previous
   *     token (logged out, terminated, or never recorded); nothing is changed
   */
  boolean continueSession(Long userId, String previousAccessTokenId, String newAccessToken);

  /** Ends every active session of the given user. */
  void terminateSessionsForUser(Long userId);

  void touchSessionActivity(String accessToken);
}
