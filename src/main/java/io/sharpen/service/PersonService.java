package io.sharpen.service;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.repo.PersonRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class PersonService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonRepository people;
    private final PasswordEncoder passwordEncoder;
    private final CommunityService community;

    public PersonService(PersonRepository people, PasswordEncoder passwordEncoder, CommunityService community) {
        this.people = people;
        this.passwordEncoder = passwordEncoder;
        this.community = community;
    }

    public Person register(String email, String rawPassword, String displayName, AccountType type) {
        if (people.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("An account already exists for " + email);
        }
        Person p = new Person(email.trim().toLowerCase(Locale.ROOT), passwordEncoder.encode(rawPassword),
                displayName.trim(), uniqueHandle(displayName), type, newApiKey());
        Person saved = people.save(p);
        community.invalidate();
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<Person> byEmail(String email) { return people.findByEmailIgnoreCase(email); }

    @Transactional(readOnly = true)
    public Optional<Person> byHandle(String handle) { return people.findByHandle(handle); }

    @Transactional(readOnly = true)
    public Optional<Person> byApiKey(String apiKey) { return people.findByApiKey(apiKey); }

    /** The signed-in person, or empty for anonymous requests. Never returns a stale entity: loads per call. */
    @Transactional(readOnly = true)
    public Optional<Person> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null
                || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        return people.findByEmailIgnoreCase(auth.getName());
    }

    public Person requireCurrent() {
        return current().orElseThrow(() -> new IllegalStateException("Not signed in"));
    }

    public String rotateApiKey(Person person) {
        person.setApiKey(newApiKey());
        people.save(person);
        return person.getApiKey();
    }

    public Person save(Person person) { return people.save(person); }

    public String uniqueHandle(String displayName) {
        String base = slug(displayName);
        if (base.isBlank()) base = "member";
        String candidate = base;
        int n = 2;
        while (people.existsByHandle(candidate)) candidate = base + "-" + n++;
        return candidate;
    }

    static String slug(String s) {
        String ascii = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    static String newApiKey() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return "shp_" + HexFormat.of().formatHex(b);
    }
}
