package io.sharpen.auth;

import io.sharpen.domain.Person;
import io.sharpen.domain.PersonIdentity;
import io.sharpen.repo.PersonIdentityRepository;
import io.sharpen.repo.PersonRepository;
import io.sharpen.service.PersonService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides which Sharpen account a Google or GitHub sign-in belongs to. The rules, in order:
 *
 * <ol>
 *   <li><b>Connecting from Settings</b> (the person is signed in and pressed <i>Connect</i>): the provider account
 *       is linked to them, whatever its email — unless it is already linked to someone else.</li>
 *   <li><b>A provider account seen before</b> signs in as the person it is linked to. Matching is by the
 *       provider's permanent user id, never by email.</li>
 *   <li><b>A new provider account</b> creates a new individual account — only if the provider vouches that the
 *       email is verified, and only if no Sharpen account uses that email yet.</li>
 * </ol>
 *
 * <p>Rule 3 deliberately does <i>not</i> merge into an existing account with the same email. Sharpen has never
 * verified the addresses people registered with, so whoever typed an email at sign-up may not own it; joining the
 * two automatically would let someone register a victim's address first and keep a password into the account the
 * victim later opens with Google. Instead the person signs in with their password and connects the provider in
 * Settings, which proves both sides.
 */
@Service
@Transactional
public class SignInService {

    private static final Logger log = LoggerFactory.getLogger(SignInService.class);

    /** What a provider told us about the person signing in. */
    public record ProviderUser(String provider, String subject, String email, boolean emailVerified, String name, String username) {}

    /** One row of Settings → Sign-in methods. {@code identity} is null when the provider is offered but not connected. */
    public record Method(String provider, String label, PersonIdentity identity) {
        public boolean connected() { return identity != null; }
    }

    private final PersonRepository people;
    private final PersonService personService;
    private final PersonIdentityRepository identities;
    private final SocialRegistrations registrations;

    public SignInService(PersonRepository people, PersonService personService, PersonIdentityRepository identities,
                         SocialRegistrations registrations) {
        this.people = people;
        this.personService = personService;
        this.identities = identities;
        this.registrations = registrations;
    }

    /** The person this sign-in is for, creating or linking as the rules above say; refuses with an error code otherwise. */
    public Person resolve(ProviderUser u, Long linkTo) {
        var known = identities.findByProviderAndSubject(u.provider(), u.subject());

        if (linkTo != null) {
            Person me = people.findById(linkTo).orElseThrow(() -> refuse("link_expired"));
            if (known.isPresent()) {
                if (!known.get().getPersonId().equals(me.getId())) throw refuse("identity_in_use");
                return me;                                                     // already connected: nothing to do
            }
            if (identities.findByPersonIdAndProvider(me.getId(), u.provider()).isPresent()) throw refuse("provider_already_linked");
            identities.save(new PersonIdentity(me.getId(), u.provider(), u.subject(), u.email(), u.username()));
            log.info("Sign-in connected: handle={} provider={}", me.getHandle(), u.provider());
            return me;
        }

        if (known.isPresent()) {
            return people.findById(known.get().getPersonId()).orElseThrow(() -> refuse("link_expired"));
        }

        if (u.email() == null || u.email().isBlank() || !u.emailVerified()) throw refuse("no_verified_email");
        if (people.findByEmailIgnoreCase(u.email()).isPresent()) throw refuse("email_in_use");
        Person created = personService.registerFromProvider(u.email(), u.name() != null && !u.name().isBlank() ? u.name() : u.username());
        identities.save(new PersonIdentity(created.getId(), u.provider(), u.subject(), u.email(), u.username()));
        log.info("Account created through {}: handle={}", u.provider(), created.getHandle());
        return created;
    }

    /** Settings → Sign-in methods: every provider this deployment offers, connected or not. */
    @Transactional(readOnly = true)
    public List<Method> methods(Person person) {
        List<Method> out = new ArrayList<>();
        for (var p : registrations.providers()) {
            out.add(new Method(p.id(), p.label(), identities.findByPersonIdAndProvider(person.getId(), p.id()).orElse(null)));
        }
        return out;
    }

    /** Why a disconnect is refused, or null once it is done. The last way in is never removed. */
    public String disconnect(Person person, String provider) {
        var identity = identities.findByPersonIdAndProvider(person.getId(), provider);
        if (identity.isEmpty()) return "it was not connected";
        if (!person.hasPassword() && identities.countByPersonId(person.getId()) <= 1) {
            return "it is the only way into this account — set a password or connect another sign-in first";
        }
        identities.delete(identity.get());
        log.info("Sign-in disconnected: handle={} provider={}", person.getHandle(), provider);
        return null;
    }

    private static OAuth2AuthenticationException refuse(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }

    /** The sentence shown for a refused or failed sign-in. */
    public static String message(String code, String provider) {
        if (code == null) return null;
        return switch (code) {
            case "access_denied" -> "Sign-in with " + provider + " was cancelled.";
            case "email_in_use" -> "A Sharpen account already uses the email address on your " + provider + " account. "
                    + "Sign in with your password, then connect " + provider + " under Settings → Sign-in methods.";
            case "no_verified_email" -> "Your " + provider + " account did not share a verified email address, which Sharpen needs "
                    + "to create an account." + ("GitHub".equals(provider) ? " Add and verify one in GitHub's email settings, then try again." : "");
            case "identity_in_use" -> "That " + provider + " account is already connected to a different Sharpen account.";
            case "provider_already_linked" -> "This account already has a different " + provider + " account connected. Disconnect it first.";
            case "link_expired" -> "That sign-in could not be completed. Please sign in again.";
            default -> "Sign-in with " + provider + " did not complete. Please try again.";
        };
    }
}
