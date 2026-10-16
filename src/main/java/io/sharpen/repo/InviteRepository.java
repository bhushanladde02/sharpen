package io.sharpen.repo;

import io.sharpen.domain.Invite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    Optional<Invite> findByEmailHash(String emailHash);
    Optional<Invite> findByToken(String token);
    long countByInviterIdAndSentAtAfter(Long inviterId, Instant after);
    long countByInviterId(Long inviterId);
    long countBySentAtAfter(Instant after);

    @Transactional
    long deleteByInviterId(Long inviterId);
}
