package com.matriz.accounts;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.matriz.shared.ApiError;

/**
 * Limits the request body of the open routes under {@code /auth/} to {@value #MAX_BYTES} bytes (decided 2026-11-18,
 * security audit S-04), so a huge body is not read and parsed before the rate limits apply. A declared length over the
 * limit gets 413 {@code payload_too_large}; a body without a declared length is cut off at the limit while it is read,
 * which the JSON parser reports as a malformed request (400).
 */
@Component
class AuthBodyLimitFilter extends OncePerRequestFilter {

    static final int MAX_BYTES = 16 * 1024;

    private final ObjectMapper json;

    AuthBodyLimitFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > MAX_BYTES) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(),
                    new ApiError("payload_too_large", "The request is too large."));
            return;
        }
        chain.doFilter(new LimitedRequest(request), response);
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        LimitedRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream in = super.getInputStream();
            return new ServletInputStream() {
                private long total;

                @Override
                public int read() throws IOException {
                    int b = in.read();
                    if (b >= 0 && ++total > MAX_BYTES) {
                        throw new IOException("Request body too large");
                    }
                    return b;
                }

                @Override
                public int read(byte[] buffer, int off, int len) throws IOException {
                    int n = in.read(buffer, off, len);
                    if (n > 0) {
                        total += n;
                        if (total > MAX_BYTES) {
                            throw new IOException("Request body too large");
                        }
                    }
                    return n;
                }

                @Override
                public boolean isFinished() {
                    return in.isFinished();
                }

                @Override
                public boolean isReady() {
                    return in.isReady();
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    in.setReadListener(listener);
                }
            };
        }
    }
}
