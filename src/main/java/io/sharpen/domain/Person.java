package io.sharpen.domain;

import io.sharpen.domain.Enums.AccountType;
import jakarta.persistence.*;

import java.time.Instant;

/** An account. Individuals own usage sessions and an AI profile; companies browse public profiles. */
@Entity
@Table(name = "person", indexes = {
        @Index(name = "ux_person_email", columnList = "email", unique = true),
        @Index(name = "ux_person_handle", columnList = "handle", unique = true),
        @Index(name = "ux_person_api_key", columnList = "api_key", unique = true)
})
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 190)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Public URL slug, e.g. /p/bhushan-ladde. */
    @Column(nullable = false, length = 60)
    private String handle;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType = AccountType.INDIVIDUAL;

    @Column(length = 160)
    private String headline;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    @Column(length = 80)
    private String industry;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(length = 120)
    private String location;

    /** Comma-separated list the person maintains themselves, e.g. "Claude, ChatGPT, Copilot". */
    @Column(name = "primary_tools", length = 300)
    private String primaryTools;

    @Column(name = "public_profile", nullable = false)
    private boolean publicProfile = true;

    /** Used by the browser extension and usage importers. Sent as the X-Api-Key header. */
    @Column(name = "api_key", nullable = false, length = 64)
    private String apiKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** 0 = no picture; otherwise bumped on every upload so the picture URL changes and caches refresh. */
    @Column(name = "avatar_version", nullable = false)
    private int avatarVersion = 0;

    protected Person() {}

    public Person(String email, String passwordHash, String displayName, String handle, AccountType accountType, String apiKey) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.handle = handle;
        this.accountType = accountType;
        this.apiKey = apiKey;
    }

    public boolean isCompany() { return accountType == AccountType.COMPANY; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getHandle() { return handle; }
    public void setHandle(String handle) { this.handle = handle; }
    public AccountType getAccountType() { return accountType; }
    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }
    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public Integer getYearsExperience() { return yearsExperience; }
    public void setYearsExperience(Integer yearsExperience) { this.yearsExperience = yearsExperience; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getPrimaryTools() { return primaryTools; }
    public void setPrimaryTools(String primaryTools) { this.primaryTools = primaryTools; }
    public boolean isPublicProfile() { return publicProfile; }
    public void setPublicProfile(boolean publicProfile) { this.publicProfile = publicProfile; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Instant getCreatedAt() { return createdAt; }
    public int getAvatarVersion() { return avatarVersion; }
    public void setAvatarVersion(int avatarVersion) { this.avatarVersion = avatarVersion; }
    public boolean isHasAvatar() { return avatarVersion > 0; }

    /** Up to two initials for the fallback avatar: "Priya Natarajan" → "PN", "Acme" → "A". */
    public String getInitials() {
        String[] parts = displayName == null ? new String[0] : displayName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    /** A stable hue (0–359) derived from the handle, so each fallback avatar has its own colour. */
    public int getAvatarHue() {
        int h = handle == null ? 0 : handle.hashCode();
        return Math.floorMod(h * 31 + 7, 360);
    }
}
