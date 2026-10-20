package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One in-app notification for one person: a short title, an optional line under it, and where clicking it goes.
 * Nothing leaves the site — no email, no push. {@code kind} names the event (welcome, friend-joined, report-ready,
 * new-member, new-feedback) so a repeat of the same event for the same link is not written twice.
 */
@Entity
@Table(name = "notification", indexes = @Index(name = "ix_notification_person", columnList = "person_id, created_at"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String body;

    @Column(length = 200)
    private String link;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {}

    public Notification(Long personId, String kind, String title, String body, String link) {
        this.personId = personId;
        this.kind = kind;
        this.title = title;
        this.body = body;
        this.link = link;
    }

    public Long getId() { return id; }
    public Long getPersonId() { return personId; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLink() { return link; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReadAt() { return readAt; }
    public boolean isUnread() { return readAt == null; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
