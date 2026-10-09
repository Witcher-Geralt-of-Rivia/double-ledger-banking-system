package com.bank.repository;

import com.bank.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /**
   * Same lookup, holding a row lock until the transaction ends so that two
   * concurrent rotations of one token cannot both succeed.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from RefreshToken r where r.tokenHash = :tokenHash")
  Optional<RefreshToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  Optional<RefreshToken> findByJti(String jti);

  List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

  @Modifying
  @Query(
      "update RefreshToken r set r.revoked = true, r.revokedAt = :now where r.userId = :userId and"
          + " r.revoked = false")
  int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
