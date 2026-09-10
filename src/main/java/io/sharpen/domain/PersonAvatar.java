package io.sharpen.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A person's profile picture, kept in its own table so the bytes are never loaded with the {@link Person}
 * row (which is read on every request). Always a 256×256 JPEG produced by {@code AvatarService}.
 */
@Entity
@Table(name = "person_avatar")
public class PersonAvatar {

    @Id
    @Column(name = "person_id")
    private Long personId;

    @Column(name = "content_type", nullable = false, length = 40)
    private String contentType;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false, length = 1_000_000)
    private byte[] bytes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected PersonAvatar() {}

    public PersonAvatar(Long personId, String contentType, byte[] bytes) {
        this.personId = personId;
        this.contentType = contentType;
        this.bytes = bytes;
    }

    public Long getPersonId() { return personId; }
    public String getContentType() { return contentType; }
    public byte[] getBytes() { return bytes; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void replace(String contentType, byte[] bytes) { this.contentType = contentType; this.bytes = bytes; this.updatedAt = Instant.now(); }
}
