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
}
