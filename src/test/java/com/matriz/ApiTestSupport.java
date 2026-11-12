package com.matriz;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.matriz.seed.DemoDataSeeder;

/**
 * Base for API tests. Runs against the matriz_test database (profile "test"). Each test loads the demo data inside
 * its own transaction, which is rolled back afterwards, so tests never change stored data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
abstract class ApiTestSupport {

    /** Fictitious password for the demo accounts in the test database only. */
    static final String TEST_PASSWORD = "test-only-demo-password";

    // Demo data ids (see DemoDataSeeder and README "Demo users").
    static final String ACME_ANA = "c0000000-0000-0000-0000-000000000002";
    static final String ACME_BEN = "c0000000-0000-0000-0000-000000000003";
    static final String ACME_VERA = "c0000000-0000-0000-0000-000000000004";
    static final String NW_CARLA = "c0000000-0000-0000-0000-000000000005";
    static final String WEBSITE = "d0000000-0000-0000-0000-000000000001";
    static final String TRADE_FAIR = "d0000000-0000-0000-0000-000000000002";
    static final String LAB = "d0000000-0000-0000-0000-000000000003";
    static final String T_IMAGES = "e0000000-0000-0000-0000-000000000001";
    static final String T_LAYOUT = "e0000000-0000-0000-0000-000000000002";
    static final String T_EQUIPMENT = "e0000000-0000-0000-0000-000000000005";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @BeforeEach
    void loadDemoData() {
        new DemoDataSeeder(jdbc, tx, TEST_PASSWORD).run();
    }

    /** Signs in through the API and returns the new session. */
    MockHttpSession signIn(String email) throws Exception {
        return (MockHttpSession) mvc.perform(post("/auth/sign-in").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + TEST_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }
}
