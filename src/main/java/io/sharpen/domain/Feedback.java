package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/** A message from a visitor or member. Kept deliberately simple: the point is to hear from early users. */
@Entity
@Table(name = "feedback", indexes = @Index(name = "ix_feedback_created", columnList = "created_at"))
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Null for anonymous visitors. */
    @Column(name = "person_id")
    private Long personId;

    @Column(length = 190)
    private String email;

    /** 1–5 "would you use this?" — optional. */
    private Integer rating;

    @Column(nullable = false, length = 2000)
    private String message;

    /** Where the person was when they wrote it, e.g. "/dashboard". */
    @Column(length = 200)
    private String page;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Feedback() {}

    public Feedback(Long personId, String email, Integer rating, String message, String page) {
        this.personId = personId;
        this.email = email;
        this.rating = rating;
        this.message = message;
        this.page = page;
    }

    public Long getId() { return id; }
    public Long getPersonId() { return personId; }
    public String getEmail() { return email; }
    public Integer getRating() { return rating; }
    public String getMessage() { return message; }
    public String getPage() { return page; }
    public Instant getCreatedAt() { return createdAt; }
    public java.time.LocalDate getCreatedOn() { return createdAt.atZone(java.time.ZoneOffset.UTC).toLocalDate(); }
}
