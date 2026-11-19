package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Two simultaneous sign-ups with the same email (INC-12; EC-12, EC-13; AC-22.4, BR-24, AC-INC12.3; US-22).
 * Unlike the other API tests this class cannot run inside one rolled-back transaction: each request must commit in
 * its own transaction for the database index to decide. It therefore removes the accounts it created, by their
 * fictitious email addresses, after each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SignUpConcurrencyTest {

    private static final String[] EMAILS = {"race.one@example.com", "race.two@example.com"};

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void removeCreatedAccounts() {
        for (String email : EMAILS) {
            jdbc.update("DELETE FROM account WHERE lower(email) = ?", email);
        }
    }

    private int count(String email) {
        return jdbc.queryForObject("SELECT count(*) FROM account WHERE lower(email) = ?", Integer.class, email);
    }

    @Test
    @DisplayName("EC-12 · AC-INC12.3 · BR-24 · two simultaneous sign-ups with the same email give one 201 and one 409 email_taken, and one row")
    void simultaneousDuplicateGivesOneAccount() throws Exception {
        assertThat(count(EMAILS[0])).isZero();
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        String[] variants = {"race.one@example.com", " Race.One@Example.com"};
        List<Future<int[]>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                String email = variants[i];
                String name = "Racer " + i;
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    var response = mvc.perform(post("/auth/sign-up").with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"name\":\"" + name + "\",\"email\":\"" + email
                                            + "\",\"password\":\"fictitious-race-pw-1\"}"))
                            .andReturn().getResponse();
                    int code = response.getContentAsString().contains("\"email_taken\"") ? 1 : 0;
                    return new int[] {response.getStatus(), code};
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<int[]> f : results) {
                int[] r = f.get(30, TimeUnit.SECONDS);
                if (r[0] == 201) {
                    created++;
                } else if (r[0] == 409) {
                    conflicts++;
                    assertThat(r[1]).as("the 409 body has code email_taken").isEqualTo(1);
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(count(EMAILS[0])).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
