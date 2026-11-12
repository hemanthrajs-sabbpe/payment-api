
package com.example.payment_api;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SplitParticipantRepository
        extends JpaRepository<SplitParticipant, Long> {

    List<SplitParticipant> findBySplitId(Long splitId);

    List<SplitParticipant> findByUserId(Long userId);

    Optional<SplitParticipant> findByIdAndSplitId(
            Long id,
            Long splitId
    );

    long countBySplitId(Long splitId);

    boolean existsBySplitIdAndUserId(
            Long splitId,
            Long userId
    );

    Optional<SplitParticipant> findBySplitIdAndUserId(
            Long splitId,
            Long userId
    );

    long countBySplitIdAndJoinStatus(
            Long splitId,
            String joinStatus
    );

    // Guest participant duplicate checks
    boolean existsBySplitIdAndEmailIgnoreCase(
            Long splitId,
            String email
    );

    boolean existsBySplitIdAndMobile(
            Long splitId,
            String mobile
    );
}
