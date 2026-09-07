package io.sharpen.repo;

import io.sharpen.domain.UsageSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UsageSessionRepository extends JpaRepository<UsageSession, Long> {

    List<UsageSession> findByPersonIdAndOccurredOnBetweenOrderByOccurredOnDesc(Long personId, LocalDate from, LocalDate to);

    Page<UsageSession> findByPersonIdOrderByOccurredOnDescIdDesc(Long personId, Pageable pageable);

    List<UsageSession> findByPersonIdAndSelfAssessedFalseOrderByOccurredOnDesc(Long personId, Pageable pageable);

    Optional<UsageSession> findByPersonIdAndExternalId(Long personId, String externalId);

    Optional<UsageSession> findByIdAndPersonId(Long id, Long personId);

    long countByPersonId(Long personId);

    long countByPersonIdAndSelfAssessedFalse(Long personId);

    @Query("select min(s.occurredOn) from UsageSession s where s.person.id = :personId")
    Optional<LocalDate> firstSessionDate(Long personId);
}
