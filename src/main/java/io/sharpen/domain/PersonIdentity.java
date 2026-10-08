package io.sharpen.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * One way to sign in other than the password: "this Google account / this GitHub account is this person".
 * Keyed by the provider's own permanent user id ({@code subject}) — never by email, because an email address
 * can change hands. {@code email} and {@code username} are only what the provider showed at link time, kept so
 * Settings can say which account is connected. At most one identity per provider per person.
 */
@Entity
@Table(name = "person_identity")
public class PersonIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "person_id", nullable = false)
    private Long personId;

    /** Registration id: {@code google} or {@code github}. */
    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 190)
    private String subject;

    @Column(length = 190)
    private String email;

    @Column(length = 100)
    private String username;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected PersonIdentity() {}

    public PersonIdentity(Long personId, String provider, String subject, String email, String username) {
        this.personId = personId;
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.username = username;
    }

    public Long getId() { return id; }
    public Long getPersonId() { return personId; }
    public String getProvider() { return provider; }
    public String getSubject() { return subject; }
    public String getEmail() { return email; }
    public String getUsername() { return username; }
    public Instant getCreatedAt() { return createdAt; }

    /** What Settings shows next to the provider's name: the username if there is one, else the email. */
    public String getLabel() { return username != null && !username.isBlank() ? username : email; }
}
