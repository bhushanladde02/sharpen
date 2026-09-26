package io.sharpen;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Register → log → import → API ingest → report → PDF → public profile → company view, against in-memory H2. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sharpen-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "sharpen.demo-data=false",
        "sharpen.admin-email=flow@example.com"
})
class WebFlowTest {

    @Autowired MockMvc mvc;
    @Autowired io.sharpen.service.TrafficService traffic;
    @Autowired PersonService people;

    private Person me;
    private Person company;

    @BeforeEach
    void accounts() {
        me = people.byEmail("flow@example.com").orElseGet(() ->
                people.register("flow@example.com", "password123", "Flow Tester", AccountType.INDIVIDUAL));
        company = people.byEmail("hr@example.com").orElseGet(() ->
                people.register("hr@example.com", "password123", "HR Co", AccountType.COMPANY));
    }

    @Test
    void publicPagesAreOpenAndPrivateOnesRedirect() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Sharpen")));
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void publicStatsFeedbackAndAdminInbox() throws Exception {
        mvc.perform(get("/api/v1/public/stats")).andExpect(status().isOk())
                .andExpect(jsonPath("$.members").isNumber());
        mvc.perform(get("/feedback")).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"t\"")))          // signed timestamp
                .andExpect(content().string(containsString("id=\"website\"")));     // honeypot
        // A person: the form was open a few seconds, the honeypot is empty → stored
        String ok = guard.tokenIssuedAgo(10);
        mvc.perform(post("/feedback").with(csrf()).param("message", "Nice idea").param("rating", "4").param("page", "/").param("t", ok))
                .andExpect(status().is3xxRedirection());
        // A bot: honeypot filled → looks accepted, nothing stored
        mvc.perform(post("/feedback").with(csrf()).param("message", "BOT ONE").param("t", ok).param("website", "http://x"))
                .andExpect(status().is3xxRedirection());
        // Too fast, expired or forged token → form again with a message, nothing stored
        mvc.perform(post("/feedback").with(csrf()).param("message", "BOT TWO").param("t", guard.tokenIssuedAgo(1)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("That was quick")));
        mvc.perform(post("/feedback").with(csrf()).param("message", "BOT THREE").param("t", guard.tokenIssuedAgo(90_000)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("open too long")));
        mvc.perform(post("/feedback").with(csrf()).param("message", "BOT FOUR").param("t", "1700000000.deadbeefdeadbeefdeadbeef"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("open too long")));
        // Rate limit: the sixth message from one address inside an hour is refused
        for (int i = 0; i < 5; i++)
            mvc.perform(post("/feedback").with(csrf()).with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                    .param("message", "burst " + i).param("t", ok)).andExpect(status().is3xxRedirection());
        mvc.perform(post("/feedback").with(csrf()).with(r -> { r.setRemoteAddr("203.0.113.9"); return r; })
                        .param("message", "burst 6").param("t", ok)).andExpect(status().isOk())
                .andExpect(content().string(containsString("a lot of messages")));
        // A vendor pitch that passes the checks is stored but folded under "Likely spam" in the inbox
        mvc.perform(post("/feedback").with(csrf()).with(r -> { r.setRemoteAddr("198.51.100.4"); return r; })
                .param("message", "Hi sharpenscore.com, we can place your website on Google 1st page. May I send you a quote & price list?")
                .param("email", "ananya@rocketseo.example").param("t", ok)).andExpect(status().is3xxRedirection());

        mvc.perform(get("/admin/feedback").with(user(company.getEmail()).roles("COMPANY"))).andExpect(status().isNotFound());
        String inbox = mvc.perform(get("/admin/feedback").with(user(me.getEmail()).roles("INDIVIDUAL"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nice idea")))
                .andExpect(content().string(not(containsString("BOT "))))
                .andExpect(content().string(containsString("Likely spam")))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(inbox.indexOf("Google 1st page") > inbox.indexOf("Likely spam"), "pitch is in the folded section");
    }

    @org.springframework.beans.factory.annotation.Autowired io.sharpen.service.SpamGuard guard;

    @Test
    void spamClassifierIsNarrow() {
        org.junit.jupiter.api.Assertions.assertTrue(guard.looksLikeSpam("Dear Sir/Madam, list sharpenscore.com in Google's Search Index! Add it here: searchindex.pro", "domains@search-sharpenscore.com"));
        org.junit.jupiter.api.Assertions.assertTrue(guard.looksLikeSpam("Our team provides Website Design & Development, SEO & Search Engine Optimization, Digital Marketing. Would you like a brief proposal?", "edward@example.com"));
        org.junit.jupiter.api.Assertions.assertTrue(guard.looksLikeSpam("I just visited sharpenscore.com and wondered if you'd ever thought about having an engaging video? Our prices start from $195. Let me know if you're interested in seeing samples.", "joanna@example.com"));
        // Real messages, including ones that mention Google or SEO once, stay in the inbox
        org.junit.jupiter.api.Assertions.assertFalse(guard.looksLikeSpam("Good", "bhushan@example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(guard.looksLikeSpam("I could not find the site on Google — is it indexed yet? Also the PDF download button does nothing on Safari.", null));
        org.junit.jupiter.api.Assertions.assertFalse(guard.looksLikeSpam("Love the score idea. Could the report show which tool I used most? Would you consider a dark mode toggle?", "user@example.com"));
        org.junit.jupiter.api.Assertions.assertFalse(guard.looksLikeSpam("Please delete my account, email is x@example.com", "x@example.com"));
    }

    @Test
    void healthReportsTheBuild() throws Exception {
        // Open endpoint: status plus the version and build time Maven wrote into the jar (build-info goal).
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").isString())
                .andExpect(jsonPath("$.built").isString());
    }

    @Test
    void helpGuideIsOnEveryPageAndNeedsNoSignIn() throws Exception {
        // The corner guide is plain markup + one static script; it must be there for visitors and members alike,
        // and the script must be reachable without signing in (it is what answers "how do I enrol?").
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(content().string(containsString("id=\"help-widget\"")))
                .andExpect(content().string(containsString("data-signed-in=\"false\"")))
                .andExpect(content().string(containsString("no live agents")));
        mvc.perform(get("/dashboard").with(user(me.getEmail()).roles("INDIVIDUAL"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-signed-in=\"true\"")))
                .andExpect(content().string(containsString("data-handle=\"" + me.getHandle() + "\"")));
        mvc.perform(get("/js/help.js")).andExpect(status().isOk())
                .andExpect(content().string(containsString("no customer-service team")))
                .andExpect(content().string(containsString("/feedback?from=help")));
    }

    @Test
    void registerThroughTheForm() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "New").param("middleName", "Q.").param("lastName", "Person")
                        .param("email", "new@example.com").param("password", "password123").param("accountType", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
        Person created = people.byEmail("new@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("New Q. Person", created.getDisplayName());
        org.junit.jupiter.api.Assertions.assertEquals("Person", created.getLastName());
        org.junit.jupiter.api.Assertions.assertEquals("NP", created.getInitials());
        // Same email again → "already exists"; a person without a last name → validation error, no account
        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Dup").param("lastName", "Licate").param("email", "new@example.com")
                        .param("password", "password123").param("accountType", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
        mvc.perform(post("/register").with(csrf())
                        .param("firstName", "Only").param("email", "only@example.com")
                        .param("password", "password123").param("accountType", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Last name is required")));
        // A company gives one name and gets no parts
        mvc.perform(post("/register").with(csrf())
                        .param("companyName", "Acme Talent").param("email", "acme@example.com")
                        .param("password", "password123").param("accountType", "COMPANY"))
                .andExpect(status().is3xxRedirection());
        Person acme = people.byEmail("acme@example.com").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Acme Talent", acme.getDisplayName());
        org.junit.jupiter.api.Assertions.assertNull(acme.getFirstName());
    }

    @Test
    void settingsSaveNameParts() throws Exception {
        var asMe = user(me.getEmail()).roles("INDIVIDUAL");
        mvc.perform(post("/settings").with(csrf()).with(asMe)
                        .param("firstName", "Flow").param("middleName", "M").param("lastName", "Tester-Two")
                        .param("handle", me.getHandle()).param("publicProfile", "true"))
                .andExpect(status().is3xxRedirection());
        Person saved = people.byEmail(me.getEmail()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Flow M Tester-Two", saved.getDisplayName());
        org.junit.jupiter.api.Assertions.assertEquals("M", saved.getMiddleName());
        // Missing last name is rejected and nothing changes
        mvc.perform(post("/settings").with(csrf()).with(asMe)
                        .param("firstName", "Flow").param("lastName", "").param("handle", me.getHandle()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Required")));
        org.junit.jupiter.api.Assertions.assertEquals("Flow M Tester-Two", people.byEmail(me.getEmail()).orElseThrow().getDisplayName());
        // Put the test account back for the other tests
        mvc.perform(post("/settings").with(csrf()).with(asMe)
                        .param("firstName", "Flow").param("lastName", "Tester").param("handle", me.getHandle()).param("publicProfile", "true"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void exportsRoundTrip() throws Exception {
        var asMe = user(me.getEmail()).roles("INDIVIDUAL");
        // A session with a comma and a quote in the notes: the CSV must quote it and the importer must read it back
        mvc.perform(post("/sessions").with(csrf()).with(asMe)
                        .param("occurredOn", LocalDate.now().toString()).param("context", "PROFESSIONAL").param("tool", "Claude")
                        .param("taskCategory", "CODING").param("durationMinutes", "30").param("promptCount", "4")
                        .param("humanContributionPct", "70").param("verifiedOutput", "true").param("outcome", "4")
                        .param("notes", "Refactored the \"export\" path, twice"))
                .andExpect(status().is3xxRedirection());
        String csv = mvc.perform(get("/settings/export/sessions.csv").with(asMe)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("sharpen-" + me.getHandle() + "-sessions.csv")))
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(csv.startsWith("date,context,tool,task,minutes,prompts,human_pct,"), csv.substring(0, 60));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"Refactored the \"\"export\"\" path, twice\""), csv);
        long before = people.byEmail(me.getEmail()).map(p -> p.getId()).map(id -> countSessions(id)).orElseThrow();

        // Import the export into the company-less second account: every row lands, notes intact
        Person other = people.byEmail("rt@example.com").orElseGet(() ->
                people.register("rt@example.com", "password123", "Round Trip", AccountType.INDIVIDUAL));
        var asOther = user(other.getEmail()).roles("INDIVIDUAL");
        mvc.perform(multipart("/import").file(new MockMultipartFile("file", "sessions.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("context", "PERSONAL").with(csrf()).with(asOther))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Imported " + before + " new")));
        org.junit.jupiter.api.Assertions.assertEquals(before, countSessions(other.getId()));
        String otherCsv = mvc.perform(get("/settings/export/sessions.csv").with(asOther)).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(otherCsv.contains("\"Refactored the \"\"export\"\" path, twice\""), "notes survived the round trip");

        // JSON: profile + sessions + reports, and nothing secret
        String json = mvc.perform(get("/settings/export/sharpen-data.json").with(asMe)).andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("sharpen-export/1"))
                .andExpect(jsonPath("$.profile.email").value(me.getEmail()))
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.reports").isArray())
                .andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertFalse(json.contains("passwordHash") || json.contains("apiKey") || json.contains("shp_"), "secrets in export");
        // Visitors get nothing
        mvc.perform(get("/settings/export/sessions.csv")).andExpect(status().is3xxRedirection());
    }

    @org.springframework.beans.factory.annotation.Autowired io.sharpen.repo.UsageSessionRepository sessionRepo;
    private long countSessions(Long personId) { return sessionRepo.countByPersonId(personId); }

    @Test
    void deleteAccountRemovesEverythingButKeepsFeedback() throws Exception {
        Person gone = people.byEmail("gone@example.com").orElseGet(() ->
                people.register("gone@example.com", "password123", "Going Away", AccountType.INDIVIDUAL));
        var asGone = user(gone.getEmail()).roles("INDIVIDUAL");
        mvc.perform(post("/sessions").with(csrf()).with(asGone)
                        .param("occurredOn", LocalDate.now().toString()).param("context", "PERSONAL").param("tool", "Claude")
                        .param("taskCategory", "WRITING").param("durationMinutes", "10").param("promptCount", "2")
                        .param("humanContributionPct", "50").param("outcome", "3"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/reports/" + YearMonth.from(LocalDate.now()) + "/generate").with(csrf()).with(asGone)).andExpect(status().is3xxRedirection());
        mvc.perform(multipart("/settings/avatar").file(new MockMultipartFile("picture", "me.png", "image/png", testPng(64, 64))).with(csrf()).with(asGone))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/feedback").with(csrf()).with(asGone).param("message", "Leaving, but it was fun").param("t", guard.tokenIssuedAgo(10)))
                .andExpect(status().is3xxRedirection());
        org.junit.jupiter.api.Assertions.assertEquals(1, countSessions(gone.getId()));
        mvc.perform(get("/p/" + gone.getHandle())).andExpect(status().isOk());

        // Wrong password: nothing happens
        mvc.perform(post("/settings/delete").with(csrf()).with(asGone).param("password", "nope-nope"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/settings#delete"));
        org.junit.jupiter.api.Assertions.assertTrue(people.byEmail("gone@example.com").isPresent());

        // Right password: account, session, report and picture are gone; the feedback stays, unlinked
        mvc.perform(post("/settings/delete").with(csrf()).with(asGone).param("password", "password123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        org.junit.jupiter.api.Assertions.assertTrue(people.byEmail("gone@example.com").isEmpty());
        org.junit.jupiter.api.Assertions.assertEquals(0, countSessions(gone.getId()));
        mvc.perform(get("/p/" + gone.getHandle())).andExpect(status().isOk())
                .andExpect(content().string(containsString("No public profile")));   // the directory page with the notice
        mvc.perform(get("/p/" + gone.getHandle() + "/avatar")).andExpect(status().isNotFound());
        String inbox = mvc.perform(get("/admin/feedback").with(user(me.getEmail()).roles("INDIVIDUAL"))).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(inbox.contains("Leaving, but it was fun"), "feedback kept");
        org.junit.jupiter.api.Assertions.assertTrue(inbox.contains("anonymous") || !inbox.contains("member #" + gone.getId()), "feedback unlinked");
    }

    @Test
    void changePasswordNeedsTheOldOne() throws Exception {
        Person pw = people.byEmail("pw@example.com").orElseGet(() ->
                people.register("pw@example.com", "password123", "Pass Word", AccountType.INDIVIDUAL));
        var asPw = user(pw.getEmail()).roles("INDIVIDUAL");
        mvc.perform(get("/settings").with(asPw)).andExpect(status().isOk()).andExpect(content().string(containsString("id=\"password\"")));
        // wrong current, mismatched repeat, too short, unchanged → all refused, hash untouched
        String before = people.byEmail(pw.getEmail()).orElseThrow().getPasswordHash();
        for (String[] bad : new String[][] {{"nope", "newpassword1", "newpassword1"}, {"password123", "newpassword1", "newpassword2"},
                                             {"password123", "short", "short"}, {"password123", "password123", "password123"}}) {
            mvc.perform(post("/settings/password").with(csrf()).with(asPw).param("current", bad[0]).param("next", bad[1]).param("repeat", bad[2]))
                    .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("flash", containsString("not changed")));
        }
        org.junit.jupiter.api.Assertions.assertEquals(before, people.byEmail(pw.getEmail()).orElseThrow().getPasswordHash());
        // the right way: the new password signs in, the old one does not
        mvc.perform(post("/settings/password").with(csrf()).with(asPw).param("current", "password123").param("next", "newpassword1").param("repeat", "newpassword1"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("flash", "Password changed."));
        mvc.perform(post("/login").with(csrf()).param("username", pw.getEmail()).param("password", "newpassword1"))
                .andExpect(redirectedUrl("/dashboard"));
        mvc.perform(post("/login").with(csrf()).param("username", pw.getEmail()).param("password", "password123"))
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void namesSplitAndJoin() {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Bhushan", "Arun", "Ladde"}, Person.splitName("Bhushan Arun Ladde"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Priya", null, "Natarajan"}, Person.splitName("  Priya   Natarajan "));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Cher", null, null}, Person.splitName("Cher"));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new String[] {"Ana", "Maria de la", "Cruz"}, Person.splitName("Ana Maria de la Cruz"));
        org.junit.jupiter.api.Assertions.assertEquals("Ana Cruz", Person.joinName("Ana", "  ", "Cruz"));
        // Registered with a whole name (API, demo data, older accounts): parts are derived, greeting uses the first
        org.junit.jupiter.api.Assertions.assertEquals("Flow", me.getFirstName());
        org.junit.jupiter.api.Assertions.assertEquals("Tester", me.getLastName());
        org.junit.jupiter.api.Assertions.assertEquals("Flow", me.getGivenName());
    }

    @Test
    void logSessionThenSeeItEverywhere() throws Exception {
        var asMe = user(me.getEmail()).roles("INDIVIDUAL");
        LocalDate day = LocalDate.now().minusDays(1);

        mvc.perform(post("/sessions").with(csrf()).with(asMe)
                        .param("occurredOn", day.toString()).param("context", "PROFESSIONAL")
                        .param("tool", "Claude").param("taskCategory", "CODING")
                        .param("durationMinutes", "45").param("promptCount", "6")
                        .param("humanContributionPct", "70").param("verifiedOutput", "true")
                        .param("learnedSomething", "true").param("outcome", "4"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/dashboard"));

        mvc.perform(get("/dashboard").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Claude")))
                .andExpect(content().string(containsString("Provisional")));

        // Sharpen CSV import
        String csv = "date,context,tool,task,minutes,prompts,human_pct,verified,learned,outcome,notes,external_id\n"
                + day + ",personal,ChatGPT,planning,15,3,80,no,yes,4,Trip,csv-1\n";
        mvc.perform(multipart("/import").file(new MockMultipartFile("file", "s.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                        .param("context", "PROFESSIONAL").with(csrf()).with(asMe))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported 1 new")));

        // Provider usage export lands as unrated
        String usage = "date,model,n_requests,n_context_tokens_total,n_generated_tokens_total\n" + day + ",gpt-4o,12,5000,1200\n";
        mvc.perform(multipart("/import").file(new MockMultipartFile("file", "usage.csv", "text/csv", usage.getBytes(StandardCharsets.UTF_8)))
                        .param("context", "PROFESSIONAL").with(csrf()).with(asMe))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Imported 1 new")));
        mvc.perform(get("/sessions").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("unrated")));

        // API ingest with the key, twice: second call is an update, not a duplicate
        String body = "{\"sessions\":[{\"externalId\":\"ext-1\",\"date\":\"" + day + "\",\"tool\":\"Gemini\",\"task\":\"research\",\"minutes\":20,\"prompts\":4}]}";
        mvc.perform(post("/api/v1/sessions").header("X-Api-Key", me.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
        mvc.perform(post("/api/v1/sessions").header("X-Api-Key", me.getApiKey())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1));
        mvc.perform(get("/api/v1/me/score").header("X-Api-Key", me.getApiKey()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sessionCount").value(4));

        // Report: live preview, generate, stored, PDF
        String ym = YearMonth.from(day).toString();
        mvc.perform(get("/reports/" + ym).with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("live preview")));
        mvc.perform(post("/reports/" + ym + "/generate").with(csrf()).with(asMe)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/reports/" + ym).with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("generated")));
        mvc.perform(get("/reports/" + ym + ".pdf").with(asMe)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().string(containsString("%PDF")));

        // Public profile, and the company sees the candidate
        mvc.perform(get("/p/" + me.getHandle())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")));
        // The open directory lists the public profile with its search text and filter chips; ?q= pre-fills the box;
        // a missing handle shows the directory too
        mvc.perform(get("/p")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")))
                .andExpect(content().string(containsString("data-search=\"flow tester")))
                .andExpect(content().string(containsString("id=\"dir-sort\"")));
        mvc.perform(get("/p").param("q", "flow")).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"flow\"")));
        mvc.perform(get("/p/no-such-handle")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No public profile at")))
                .andExpect(content().string(containsString("Flow Tester")));

        // Profile picture: none yet → initials; upload → served with a year-long cache; remove → gone again
        mvc.perform(get("/p/" + me.getHandle() + "/avatar")).andExpect(status().isNotFound());
        mvc.perform(get("/p")).andExpect(content().string(containsString(">FT<")));
        mvc.perform(multipart("/settings/avatar").file(new MockMultipartFile("picture", "me.png", "image/png", testPng(300, 200)))
                .with(csrf()).with(asMe)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/p/" + me.getHandle() + "/avatar").param("v", "1")).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
        mvc.perform(get("/p")).andExpect(content().string(containsString("/p/" + me.getHandle() + "/avatar?v=1")));
        mvc.perform(multipart("/settings/avatar").file(new MockMultipartFile("picture", "notes.txt", "text/plain", "hello".getBytes()))
                .with(csrf()).with(asMe)).andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flash", containsString("JPEG or PNG")));
        mvc.perform(post("/settings/avatar/remove").with(csrf()).with(asMe)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/p/" + me.getHandle() + "/avatar")).andExpect(status().isNotFound());
        // Edit an existing session: the page renders, the form posts to /sessions/{id}, the change is saved, delete works
        var firstId = mvc.perform(get("/sessions").with(asMe)).andReturn().getResponse().getContentAsString()
                .replaceAll("(?s).*?href=\"/sessions/(\\d+)/edit\".*", "$1");
        mvc.perform(get("/sessions/" + firstId + "/edit").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/sessions/" + firstId + "\"")))
                .andExpect(content().string(containsString("value=\"" + day + "\"")))   // ISO date, not 8/27/26
                .andExpect(content().string(containsString("formaction=\"/sessions/" + firstId + "/delete\"")));
        mvc.perform(post("/sessions/" + firstId).with(csrf()).with(asMe)
                        .param("occurredOn", day.toString()).param("context", "PERSONAL").param("tool", "Claude Code")
                        .param("taskCategory", "CODING").param("durationMinutes", "45").param("promptCount", "20")
                        .param("humanContributionPct", "50").param("verifiedOutput", "true").param("learnedSomething", "true")
                        .param("outcome", "4").param("notes", "edited"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/sessions"));
        mvc.perform(get("/sessions/" + firstId + "/edit").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Claude Code\"")));
        // Tools on the profile come from logged sessions, no Settings entry needed
        mvc.perform(get("/p/" + me.getHandle())).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Claude Code<")))
                .andExpect(content().string(not(containsString("Not listed"))));
        mvc.perform(post("/sessions/" + firstId + "/delete").with(csrf()).with(asMe)).andExpect(status().is3xxRedirection());
        mvc.perform(get("/sessions/" + firstId + "/edit").with(asMe)).andExpect(status().is4xxClientError());

        // Search engines: description + structured data on the landing page, noindex on private pages,
        // robots.txt and a sitemap that lists the public profile
        mvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("<meta name=\"description\" content=\"Sharpen is a free")))
                .andExpect(content().string(containsString("application/ld+json")))
                .andExpect(content().string(containsString("content=\"index, follow\"")));
        mvc.perform(get("/dashboard").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("content=\"noindex, nofollow\"")));
        mvc.perform(get("/p/" + me.getHandle())).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester&#39;s AI profile")));
        mvc.perform(get("/robots.txt")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Disallow: /dashboard")))
                .andExpect(content().string(containsString("Sitemap: ")));
        mvc.perform(get("/sitemap.xml")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/p/" + me.getHandle() + "</loc>")));

        // A trailing slash reaches the same page (no redirect, so no Location header to abuse)
        mvc.perform(get("/sessions/").with(asMe)).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"));
        mvc.perform(get("/p/")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Public AI profiles")));

        // Traffic analytics: the pages above were counted (bots excluded), the owner sees the dashboard and exports,
        // a company account gets 404
        mvc.perform(get("/p").header("User-Agent", "Mozilla/5.0 test").header("Referer", "https://news.ycombinator.com/item?id=1")
                .header("Accept-Language", "en-US,en;q=0.9")).andExpect(status().isOk());
        mvc.perform(get("/p").header("User-Agent", "Googlebot/2.1")).andExpect(status().isOk());
        traffic.flush();
        mvc.perform(get("/admin/traffic").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("news.ycombinator.com")))
                .andExpect(content().string(containsString("Evidence PDF")));
        // A month on its own (the link every month chip produces) — days is null on that path
        mvc.perform(get("/admin/traffic").param("month", YearMonth.from(LocalDate.now()).toString()).with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Evidence PDF")));
        mvc.perform(get("/admin/traffic").param("days", "0").with(asMe)).andExpect(status().isOk());   // "All time"
        mvc.perform(get("/admin/traffic.csv").param("days", "7").with(asMe)).andExpect(status().isOk())
                .andExpect(content().string(containsString("day,page_views,visitors,signups,sessions_logged,reports_generated")));
        mvc.perform(get("/admin/traffic.pdf").param("month", YearMonth.from(LocalDate.now()).toString()).with(asMe)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
        mvc.perform(get("/admin/traffic").with(user(company.getEmail()).roles("COMPANY"))).andExpect(status().isNotFound());

        mvc.perform(get("/candidates").with(user(company.getEmail()).roles("COMPANY"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")));
        mvc.perform(get("/candidates").with(asMe)).andExpect(status().isForbidden());
    }

    /** A small solid PNG, wider than tall, so the crop path is exercised. */
    private static byte[] testPng(int w, int h) throws Exception {
        var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics(); g.setColor(java.awt.Color.ORANGE); g.fillRect(0, 0, w, h); g.dispose();
        var out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
