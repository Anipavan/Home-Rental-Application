package com.spa.home_rental_application.auth_service.Util;

import com.spa.home_rental_application.auth_service.Dto.Response.AuthResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

/**
 * Central cookie handling for the refresh token.
 *
 * <p>Prior state: the SPA persisted the opaque refresh token to
 * {@code localStorage} (zustand-persist). Any XSS on the domain
 * could exfiltrate it and impersonate the user for the token's
 * full 24-hour TTL. Moving it into an {@code HttpOnly + Secure +
 * SameSite=Lax} cookie eliminates that exfiltration path — JS can
 * no longer read the value; only the browser can send it, and only
 * on same-site requests.
 *
 * <p>Cookie attributes:
 * <ul>
 *   <li><b>HttpOnly</b> — hard block on {@code document.cookie} access.</li>
 *   <li><b>Secure</b> — never sent over plain HTTP. Modern browsers allow
 *       this flag on {@code localhost} in dev, so we set it
 *       unconditionally.</li>
 *   <li><b>SameSite=Lax</b> — sent on top-level navigation (email magic
 *       links, redirects from Razorpay's hosted checkout) but NOT on
 *       third-party form POSTs. {@code Strict} would break the magic-
 *       link "click from email → land on the app already signed in"
 *       UX because top-level navigation from Gmail is treated as
 *       cross-site.</li>
 *   <li><b>Path=/</b> — sent on every request to the domain. Bandwidth
 *       cost is negligible (opaque token is short) and it avoids
 *       path-mismatch bugs when the SPA calls {@code /auth/refresh}
 *       through the nginx {@code /api} strip layer.</li>
 * </ul>
 */
public final class RefreshCookie {

    /** Cookie name — kept short but distinctive to avoid clashes with
     *  other services that might set generic {@code refresh_token}. */
    public static final String NAME = "hra_refresh";

    /** 24 hours in seconds. Matches the auth-service's refresh-token TTL
     *  so the cookie evaporates at the same time the token becomes
     *  invalid server-side. */
    private static final long MAX_AGE_SECONDS = 24 * 60 * 60;

    private RefreshCookie() { /* util */ }

    /**
     * Read the refresh token from the incoming request's
     * {@code hra_refresh} cookie. Returns {@code null} when the cookie
     * is absent.
     */
    public static String read(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /**
     * Build a {@code Set-Cookie} header value that installs the token.
     * Caller adds it to the response via
     * {@code response.headers().add(HttpHeaders.SET_COOKIE, build(token))}.
     */
    public static String issue(String token) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE_SECONDS)
                .build()
                .toString();
    }

    /**
     * Set-Cookie value that immediately clears the cookie
     * (Max-Age=0). Used by {@code /auth/logout} so the browser drops
     * the cookie right after the server revokes the token.
     */
    public static String clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build()
                .toString();
    }

    /** Convenience: add the Set-Cookie header for an issued token. */
    public static void addToHeaders(HttpHeaders headers, String token) {
        headers.add(HttpHeaders.SET_COOKIE, issue(token));
    }

    /** Convenience: add the Set-Cookie header that clears the cookie. */
    public static void clearHeader(HttpHeaders headers) {
        headers.add(HttpHeaders.SET_COOKIE, clear());
    }

    /**
     * Given a service-layer {@link AuthResponse} (which still has the
     * refresh token in its body field), produce a browser-safe
     * {@link ResponseEntity}: refresh token moved to the
     * {@code hra_refresh} HttpOnly cookie, body field nulled so no
     * client JS ever reads it.
     */
    public static ResponseEntity<AuthResponse> wrap(AuthResponse resp) {
        HttpHeaders headers = new HttpHeaders();
        String refresh = resp == null ? null : resp.refreshToken();
        if (refresh != null && !refresh.isBlank()) {
            addToHeaders(headers, refresh);
        }
        AuthResponse sanitized = resp == null ? null : new AuthResponse(
                resp.accessToken(),
                null,
                resp.tokenType(),
                resp.accessTokenExpiresInSeconds(),
                resp.userName(),
                resp.authUserId(),
                resp.role(),
                resp.roles()
        );
        return ResponseEntity.ok().headers(headers).body(sanitized);
    }
}
