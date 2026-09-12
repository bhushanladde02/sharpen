package io.sharpen.repo;

import io.sharpen.domain.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {

    Optional<MonthlyReport> findByPersonIdAndYearMonth(Long personId, String yearMonth);

    List<MonthlyReport> findByPersonIdOrderByYearMonthDesc(Long personId);

    List<MonthlyReport> findByGeneratedAtBetween(java.time.Instant from, java.time.Instant to);
}
