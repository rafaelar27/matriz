package com.matriz;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

/**
 * The CSRF endpoint is open and writes the XSRF-TOKEN cookie. Runs in a fresh context because Spring Security's
 * {@code csrf()} test helper, used by the other tests, replaces the cookie-based token store for the whole context.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class CsrfCookieTest extends ApiTestSupport {

    @Test
    void csrfEndpointIsOpenAndSetsTheTokenCookie() throws Exception {
        mvc.perform(get("/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("XSRF-TOKEN=")));
    }
}
