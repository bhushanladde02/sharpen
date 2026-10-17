package io.sharpen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** An address (as a one-way hash) that asked never to be emailed by Sharpen again. Checked before every send. */
@Entity
@Table(name = "mail_opt_out")
public class MailOptOut {

    @Id
    @Column(name = "email_hash", length = 64)
    private String emailHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected MailOptOut() {}

    public MailOptOut(String emailHash) { this.emailHash = emailHash; }

    public String getEmailHash() { return emailHash; }
    public Instant getCreatedAt() { return createdAt; }
}
