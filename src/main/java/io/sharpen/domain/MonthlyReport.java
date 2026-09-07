package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A frozen monthly snapshot. The dashboard is always live; reports are what gets shared or compared month over month,
 * so they are stored once generated and regenerated only on request.
 */
@Entity
@Table(name = "monthly_report", indexes = {
        @Index(name = "ux_report_person_month", columnList = "person_id, year_month", unique = true)
})
public class MonthlyReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /** ISO year-month, e.g. 2026-08. */
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "ai_score", nullable = false)
    private int aiScore;

    @Column(nullable = false) private int independence;
    @Column(nullable = false) private int effectiveness;
    @Column(nullable = false) private int verification;
    @Column(nullable = false) private int growth;
    @Column(nullable = false) private int breadth;

    @Column(name = "session_count", nullable = false)
    private int sessionCount;

    @Column(name = "total_minutes", nullable = false)
    private int totalMinutes;

    /** The full report as JSON so the page and the PDF can be re-rendered without recomputation. */
    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt = Instant.now();

    protected MonthlyReport() {}

    public MonthlyReport(Person person, String yearMonth) {
        this.person = person;
        this.yearMonth = yearMonth;
    }

    public Long getId() { return id; }
    public Person getPerson() { return person; }
    public String getYearMonth() { return yearMonth; }
    public int getAiScore() { return aiScore; }
    public void setAiScore(int aiScore) { this.aiScore = aiScore; }
    public int getIndependence() { return independence; }
    public void setIndependence(int independence) { this.independence = independence; }
    public int getEffectiveness() { return effectiveness; }
    public void setEffectiveness(int effectiveness) { this.effectiveness = effectiveness; }
    public int getVerification() { return verification; }
    public void setVerification(int verification) { this.verification = verification; }
    public int getGrowth() { return growth; }
    public void setGrowth(int growth) { this.growth = growth; }
    public int getBreadth() { return breadth; }
    public void setBreadth(int breadth) { this.breadth = breadth; }
    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }
    public int getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(int totalMinutes) { this.totalMinutes = totalMinutes; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public Instant getGeneratedAt() { return generatedAt; }
    /** Calendar date of generation (UTC), for display. */
    public java.time.LocalDate getGeneratedOn() { return generatedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(); }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}
