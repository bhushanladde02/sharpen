package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One invitation email Sharpen sent on a member's behalf. The friend's address is kept only as a one-way hash —
 * enough to make sure the same address is never written to twice and to notice when they join, not enough to
 * read it back. {@code token} is the unsubscribe link's secret.
 */
@Entity
@Table(name = "invite", indexes = @Index(name = "ux_invite_email_hash", columnList = "email_hash", unique = true))
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inviter_id", nullable = false)
    private Long inviterId;

    @Column(name = "email_hash", nullable = false, length = 64)
    private String emailHash;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(nullable = false, length = 40, unique = true)
    private String token;

    protected Invite() {}

    public Invite(Long inviterId, String emailHash, String token) {
        this.inviterId = inviterId;
        this.emailHash = emailHash;
        this.token = token;
    }

    public Long getId() { return id; }
    public Long getInviterId() { return inviterId; }
    public String getEmailHash() { return emailHash; }
    public Instant getSentAt() { return sentAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }
    public String getToken() { return token; }
}
