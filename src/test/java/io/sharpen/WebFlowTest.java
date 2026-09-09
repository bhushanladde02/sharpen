package io.sharpen;

import io.sharpen.domain.Enums.AccountType;
import io.sharpen.domain.Person;
import io.sharpen.service.PersonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.hamcrest.Matchers.containsString;
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
        mvc.perform(get("/feedback")).andExpect(status().isOk());
        mvc.perform(post("/feedback").with(csrf()).param("message", "Nice idea").param("rating", "4").param("page", "/"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/feedback").with(user(company.getEmail()).roles("COMPANY"))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/feedback").with(user(me.getEmail()).roles("INDIVIDUAL"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nice idea")));
    }

    @Test
    void registerThroughTheForm() throws Exception {
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "New Person").param("email", "new@example.com")
                        .param("password", "password123").param("accountType", "INDIVIDUAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered"));
        mvc.perform(post("/register").with(csrf())
                        .param("displayName", "Dup").param("email", "new@example.com")
                        .param("password", "password123").param("accountType", "INDIVIDUAL"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
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
        // The open directory lists the public profile, finds it by search, and a missing handle shows the directory too
        mvc.perform(get("/p")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")));
        mvc.perform(get("/p").param("q", "flow")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")));
        mvc.perform(get("/p").param("q", "zzz-nobody")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No public profile matches")));
        mvc.perform(get("/p/no-such-handle")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No public profile at")))
                .andExpect(content().string(containsString("Flow Tester")));
        mvc.perform(get("/candidates").with(user(company.getEmail()).roles("COMPANY"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Flow Tester")));
        mvc.perform(get("/candidates").with(asMe)).andExpect(status().isForbidden());
    }
}
