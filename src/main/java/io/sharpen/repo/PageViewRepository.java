package io.sharpen.repo;

import io.sharpen.domain.PageView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface PageViewRepository extends JpaRepository<PageView, Long> {

    /** [day, views, visitors] per day in the range, oldest first. */
    @Query("select v.viewDay, count(v), count(distinct v.visitor) from PageView v where v.viewDay between :from and :to group by v.viewDay order by v.viewDay")
    List<Object[]> daily(LocalDate from, LocalDate to);

    /** [path, views, visitors] most viewed first. */
    @Query("select v.path, count(v), count(distinct v.visitor) from PageView v where v.viewDay between :from and :to group by v.path order by count(v) desc")
    List<Object[]> topPaths(LocalDate from, LocalDate to);

    /** [referrer, views] most common first; direct visits are the null row. */
    @Query("select v.referrer, count(v) from PageView v where v.viewDay between :from and :to group by v.referrer order by count(v) desc")
    List<Object[]> topReferrers(LocalDate from, LocalDate to);

    /** [lang, views] most common first. */
    @Query("select v.lang, count(v) from PageView v where v.viewDay between :from and :to group by v.lang order by count(v) desc")
    List<Object[]> topLanguages(LocalDate from, LocalDate to);

    /** [country, views, visitors] most visitors first; null is "not recorded" (before countries were), ZZ "unknown". */
    @Query("select v.country, count(v), count(distinct v.visitor) from PageView v where v.viewDay between :from and :to group by v.country order by count(distinct v.visitor) desc, count(v) desc")
    List<Object[]> countries(LocalDate from, LocalDate to);

    @Query("select count(v), count(distinct v.visitor) from PageView v where v.viewDay between :from and :to")
    List<Object[]> totals(LocalDate from, LocalDate to);

    /** [page views, visits] since counting began; a visit is one browser on one day (the hash includes the day). */
    @Query("select count(v), count(distinct v.visitor) from PageView v")
    List<Object[]> allTime();

    /** Countries visitors have come from (unknown and not-recorded left out). */
    @Query("select count(distinct v.country) from PageView v where v.country is not null and v.country <> 'ZZ'")
    long countriesReached();

    @Query("select min(v.viewDay) from PageView v")
    LocalDate firstDay();
}
