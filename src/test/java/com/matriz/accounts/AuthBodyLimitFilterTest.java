package com.matriz.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matriz.shared.PayloadTooLargeException;

/**
 * The body limit of /auth/ for a request without a declared length, as pure logic (INC-12; NFR-11, SP-08; US-22,
 * US-01; AC-22.6; decided 2026-11-18, security audit S-04). The body is cut off at 16384 bytes while it is read, which
 * raises PayloadTooLargeException (an IOException), answered 413 payload_too_large by ApiExceptionHandler. No Spring context.
 */
class AuthBodyLimitFilterTest {

    /** A request that does not declare its length, like a chunked body. */
    private static MockHttpServletRequest undeclared(String uri, int size) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return -1;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setServletPath(uri); // the decoded path, as a servlet container reports it (audit S-11)
        byte[] body = new byte[size];
        Arrays.fill(body, (byte) ' ');
        request.setContent(body);
        return request;
    }

    private final AuthBodyLimitFilter filter = new AuthBodyLimitFilter(new ObjectMapper());

    private long readAll(MockHttpServletRequest request) throws Exception {
        AtomicLong total = new AtomicLong();
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            byte[] buffer = new byte[1000];
            int n;
            var in = req.getInputStream();
            while ((n = in.read(buffer)) > 0) {
                total.addAndGet(n);
            }
        });
        return total.get();
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · an undeclared body of 16384 bytes is read completely")
    void undeclaredBodyAtTheLimitIsRead() throws Exception {
        assertThat(readAll(undeclared("/auth/sign-up", 16384))).isEqualTo(16384);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · an undeclared body of 16385 bytes is cut off while it is read with a PayloadTooLargeException")
    void undeclaredBodyOverTheLimitIsCutOff() {
        assertThatThrownBy(() -> readAll(undeclared("/auth/sign-in", 16385))).isInstanceOf(PayloadTooLargeException.class).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · a declared length over the limit gets 413 without calling the rest of the chain")
    void declaredBodyOverTheLimitGets413() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/sign-up");
        request.setServletPath("/auth/sign-up");
        request.setContent(new byte[16385]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("payload_too_large");
        assertThat(called[0]).isFalse();
    }

    @Test
    @DisplayName("NFR-11 · routes outside /auth/ are not limited by this filter")
    void otherRoutesAreNotLimited() throws Exception {
        assertThat(readAll(undeclared("/projects", 20000))).isEqualTo(20000);
    }

    @Test
    @DisplayName("AC-22.6 · NFR-11 · SP-08 · audit S-11 · an encoded path such as /%61uth/sign-in is limited like /auth/sign-in")
    void encodedPathIsLimited() throws Exception {
        for (String[] route : new String[][] {{"/auth", "/sign-in"}, {"/auth", "/sign-up"}}) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/%61uth" + route[1]);
            request.setServletPath(route[0]);
            request.setPathInfo(route[1]);
            request.setContent(new byte[16385]);
            MockHttpServletResponse response = new MockHttpServletResponse();
            boolean[] called = {false};

            filter.doFilter(request, response, (req, res) -> called[0] = true);

            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(response.getContentAsString()).contains("payload_too_large");
            assertThat(called[0]).isFalse();
        }
    }

    @Test
    @DisplayName("NFR-11 · audit S-11 · the decoded path decides: a path outside /auth/ is not limited even if the raw URI starts with /auth/")
    void decodedPathDecides() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/../projects");
        request.setServletPath("/projects");
        request.setContent(new byte[20000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean[] called = {false};

        filter.doFilter(request, response, (req, res) -> called[0] = true);

        assertThat(called[0]).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("NFR-11 · SP-08 · audit S-14 · a reader over 16385 bytes is cut off with an IOException")
    void readerOverTheLimitIsCutOff() {
        MockHttpServletRequest request = undeclared("/auth/sign-up", 16385);
        assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var reader = req.getReader();
            char[] buffer = new char[1000];
            while (reader.read(buffer) > 0) {
                // read on
            }
        })).isInstanceOf(PayloadTooLargeException.class).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("NFR-11 · SP-08 · audit S-14 · a reader over exactly 16384 bytes reads everything")
    void readerAtTheLimitReadsEverything() throws Exception {
        MockHttpServletRequest request = undeclared("/auth/sign-up", 16384);
        long[] total = {0};
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            var reader = req.getReader();
            char[] buffer = new char[1000];
            int n;
            while ((n = reader.read(buffer)) > 0) {
                total[0] += n;
            }
        });
        assertThat(total[0]).isEqualTo(16384);
    }
}
