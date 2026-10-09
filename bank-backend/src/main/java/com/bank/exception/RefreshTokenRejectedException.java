package com.bank.exception;

/**
 * A refresh token was refused and, as a consequence, tokens or sessions were
 * revoked. Unlike a plain {@link InvalidDataException} it must not roll the
 * transaction back, otherwise the revocation would be undone.
 */
public class RefreshTokenRejectedException extends InvalidDataException {

  public RefreshTokenRejectedException(String message) {
    super(message, "refreshToken", null);
  }
}
