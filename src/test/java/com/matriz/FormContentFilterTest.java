package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.web.filter.FormContentFilter;

/**
 * The form-content filter is switched off (INC-12; NFR-11, SP-08; US-22; audit S-13): it would read form bodies of
 * PUT, PATCH and DELETE requests before the body limit and the rate limits apply. JSON requests are covered by the
 * other sign-up and sign-in tests.
 */
class FormContentFilterTest extends ApiTestSupport {

    @Autowired
    ApplicationContext context;

    @Test
    @DisplayName("NFR-11 · SP-08 · audit S-13 · no FormContentFilter bean exists in the web context")
    void noFormContentFilterBean() {
        assertThat(context.getBeanNamesForType(FormContentFilter.class)).isEmpty();
    }

    @Test
    @DisplayName("NFR-11 · SP-08 · audit S-13 · an unauthenticated PUT with a form body to /auth/sign-in is refused (4xx, never a success)")
    void unauthenticatedFormPutIsRefused() throws Exception {
        int refused = mvc.perform(put("/auth/sign-in").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("email=ana%40example.com&password=" + TEST_PASSWORD))
                .andReturn().getResponse().getStatus();
        assertThat(refused).isBetween(400, 499);
    }

    @Test
    @DisplayName("FR-01 · audit S-13 · normal JSON sign-in is unaffected")
    void jsonSignInIsUnaffected() throws Exception {
        signIn("ana@example.com");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/auth/csrf"))
                .andExpect(status().isNoContent());
    }
}
