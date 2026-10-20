package io.sharpen.repo;

import io.sharpen.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByPersonIdOrderByCreatedAtDesc(Long personId);
    Optional<Notification> findByIdAndPersonId(Long id, Long personId);
    long countByPersonIdAndReadAtIsNull(Long personId);
    boolean existsByPersonIdAndKindAndLink(Long personId, String kind, String link);

    @Modifying @Transactional
    @Query("update Notification n set n.readAt = :now where n.personId = :personId and n.readAt is null")
    int markAllRead(Long personId, Instant now);

    @Transactional
    long deleteByPersonId(Long personId);
}
