
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface SplitSessionRepository
        extends JpaRepository<SplitSession, Long> {

    Optional<SplitSession> findBySessionCode(String sessionCode);

    Optional<SplitSession> findByQrTokenHash(String qrTokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM SplitSession s
            WHERE s.qrTokenHash = :qrTokenHash
            """)
    Optional<SplitSession> findByQrTokenHashForUpdate(
            @Param("qrTokenHash") String qrTokenHash
    );

    List<SplitSession> findBySplitId(Long splitId);

    List<SplitSession> findByCreatedByUserId(Long userId);

    List<SplitSession> findBySplitIdAndSessionStatus(
            Long splitId,
            String sessionStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SplitSession s WHERE s.id = :sessionId")
    Optional<SplitSession> findByIdForUpdate(
            @Param("sessionId") Long sessionId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM SplitSession s
            WHERE s.splitId = :splitId
              AND s.sessionStatus = :sessionStatus
            """)
    List<SplitSession> findBySplitIdAndSessionStatusForUpdate(
            @Param("splitId") Long splitId,
            @Param("sessionStatus") String sessionStatus
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM SplitSession s
            WHERE s.splitId = :splitId
              AND s.sessionStatus = :sessionStatus
              AND s.joinPolicy IN ('PUBLIC', 'INVITE')
            """)
    List<SplitSession> findGroupSessionsBySplitIdAndStatusForUpdate(
            @Param("splitId") Long splitId,
            @Param("sessionStatus") String sessionStatus
    );
}
