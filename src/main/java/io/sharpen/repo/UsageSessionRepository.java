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

    /** Distinct tools a person has logged, most minutes first: [tool, minutes, sessions]. */
    @Query("select s.tool, sum(s.durationMinutes), count(s) from UsageSession s where s.person.id = :personId " +
           "group by s.tool order by sum(s.durationMinutes) desc, count(s) desc, s.tool asc")
    List<Object[]> toolTotals(Long personId);

    /** [createdAt (truncated to day is done by the caller), count] for sessions created in a window — small tables, so per-row is fine. */
    @Query("select s.createdAt, 1L from UsageSession s where s.createdAt >= :from and s.createdAt < :to")
    List<Object[]> createdPerDay(java.time.Instant from, java.time.Instant to);

    @Query("select min(s.occurredOn) from UsageSession s where s.person.id = :personId")
    Optional<LocalDate> firstSessionDate(Long personId);
}
