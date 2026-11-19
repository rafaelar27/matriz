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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.matriz.accounts.AttemptLimiter;

/**
 * Parallel wrong-password sign-ins from one address and email (INC-12; AC-22.6, EC-16, NFR-11, SP-08; US-22, US-01;
 * audit S-02, review R-01). The attempt is counted before the password is checked, so parallel guesses cannot all
 * pass the limit check. The email is unknown, so the test creates and changes no data. Small limits for this class
 * only; the counters are cleared before and after the test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "matriz.rate-limit.sign-in.max-per-ip-and-email=5",
        "matriz.rate-limit.sign-in.max-per-ip=1000"})
class SignInConcurrencyTest {

    private static final int LIMIT = 5;
    private static final int PARALLEL = 40;

    @Autowired
    MockMvc mvc;

    @Autowired
    AttemptLimiter limiter;

    @BeforeEach
    @AfterEach
    void clearCounters() {
        limiter.clear();
    }

    @Test
    @DisplayName("AC-22.6 · EC-16 · audit S-02 · 40 parallel wrong-password sign-ins give at most the limit of 401 and the rest 429 with Retry-After")
    void parallelGuessesCannotPassTheLimit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        CountDownLatch ready = new CountDownLatch(PARALLEL);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<int[]>> results = new ArrayList<>();
        try {
            for (int i = 0; i < PARALLEL; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await(10, TimeUnit.SECONDS);
                    var response = mvc.perform(post("/auth/sign-in").with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"email\":\"nobody.race@example.com\",\"password\":\"wrong-password-1\"}"))
                            .andReturn().getResponse();
                    String retry = response.getHeader("Retry-After");
                    boolean hasRetry = retry != null && Long.parseLong(retry) >= 1;
                    return new int[] {response.getStatus(), hasRetry ? 1 : 0};
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            int unauthorized = 0;
            int tooMany = 0;
            for (Future<int[]> f : results) {
                int[] r = f.get(60, TimeUnit.SECONDS);
                if (r[0] == 401) {
                    unauthorized++;
                } else if (r[0] == 429) {
                    tooMany++;
                    assertThat(r[1]).as("429 has Retry-After of at least 1").isEqualTo(1);
                }
            }
            assertThat(unauthorized).isLessThanOrEqualTo(LIMIT);
            assertThat(unauthorized + tooMany).isEqualTo(PARALLEL);
            // Every attempt is a credentials failure, so exactly the limit is let through.
            assertThat(unauthorized).isEqualTo(LIMIT);
        } finally {
            pool.shutdownNow();
        }
    }
}
