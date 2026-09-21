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

    /** What is shown everywhere: "First [Middle] Last" for a person, the company name for a company account. */
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    /** Individuals only; null on company accounts. Kept separately so forms, exports and sorting can use them. */
    @Column(name = "first_name", length = 60)
    private String firstName;

    @Column(name = "middle_name", length = 60)
    private String middleName;

    @Column(name = "last_name", length = 60)
    private String lastName;

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
        this.handle = handle;
        this.accountType = accountType;
        this.apiKey = apiKey;
        setDisplayName(displayName);   // after accountType, so a person's name is split into its parts
    }

    public boolean isCompany() { return accountType == AccountType.COMPANY; }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    /** Company name, or a whole name for a person — the latter is split into first/middle/last. */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (!isCompany()) { String[] n = splitName(displayName); firstName = n[0]; middleName = n[1]; lastName = n[2]; }
    }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getLastName() { return lastName; }
    /** Sets the three parts and rebuilds the display name from them. Individuals only. */
    public void setNames(String first, String middle, String last) {
        this.firstName = clean(first); this.middleName = clean(middle); this.lastName = clean(last);
        this.displayName = joinName(firstName, middleName, lastName);
    }
    /** First name for greetings; falls back to the first word of the display name (company accounts). */
    public String getGivenName() {
        if (firstName != null) return firstName;
        return displayName == null ? "" : displayName.trim().split("\\s+")[0];
    }

    /** "Bhushan Arun Ladde" → [Bhushan, Arun, Ladde]; "Priya Natarajan" → [Priya, null, Natarajan]; "Cher" → [Cher, null, null]. */
    public static String[] splitName(String full) {
        String[] w = full == null ? new String[0] : full.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return new String[] {null, null, null};
        if (w.length == 1) return new String[] {w[0], null, null};
        String middle = w.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(w, 1, w.length - 1)) : null;
        return new String[] {w[0], middle, w[w.length - 1]};
    }
    public static String joinName(String first, String middle, String last) {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[] {first, middle, last}) {
            if (part != null && !part.isBlank()) { if (sb.length() > 0) sb.append(' '); sb.append(part.trim()); }
        }
        return sb.toString();
    }
    private static String clean(String s) { return s == null || s.isBlank() ? null : s.trim().replaceAll("\\s+", " "); }
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
        if (firstName != null && lastName != null) return initial(firstName) + initial(lastName);
        String[] parts = displayName == null ? new String[0] : displayName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty() && Character.isLetterOrDigit(part.charAt(0))) sb.append(Character.toUpperCase(part.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }
    private static String initial(String s) {
        return s.isEmpty() || !Character.isLetterOrDigit(s.charAt(0)) ? "" : String.valueOf(Character.toUpperCase(s.charAt(0)));
    }

    /** A stable hue (0–359) derived from the handle, so each fallback avatar has its own colour. */
    public int getAvatarHue() {
        int h = handle == null ? 0 : handle.hashCode();
        return Math.floorMod(h * 31 + 7, 360);
    }
}
