package com.spa.home_rental_application.auth_commons;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewaySignatureVerifierTest {

    private static final String SECRET = "Z2F0ZXdheS1zaGFyZWQtc2VjcmV0LWNoYW5nZS1tZS1pbi1wcm9k";

    @Test
    void roundTrip_signThenVerify_succeeds() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        long ts = Instant.now().getEpochSecond();
        String sig = v.sign(ts, "GET", "/properties/buildings");
        assertThat(v.verify(String.valueOf(ts), sig, "GET", "/properties/buildings"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.OK);
    }

    @Test
    void wrongSignature_isMismatch() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        long ts = Instant.now().getEpochSecond();
        var outcome = v.verify(String.valueOf(ts),
                "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                "GET", "/x");
        assertThat(outcome).isEqualTo(GatewaySignatureVerifier.Outcome.MISMATCH);
    }

    @Test
    void differentMethod_isMismatch() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        long ts = Instant.now().getEpochSecond();
        String sig = v.sign(ts, "GET", "/x");
        assertThat(v.verify(String.valueOf(ts), sig, "POST", "/x"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.MISMATCH);
    }

    @Test
    void differentPath_isMismatch() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        long ts = Instant.now().getEpochSecond();
        String sig = v.sign(ts, "GET", "/x");
        assertThat(v.verify(String.valueOf(ts), sig, "GET", "/y"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.MISMATCH);
    }

    @Test
    void staleTimestamp_isStale() {
        var v = new GatewaySignatureVerifier(SECRET, 5);
        long old = Instant.now().getEpochSecond() - 60;  // way out of skew window
        String sig = v.sign(old, "GET", "/x");
        assertThat(v.verify(String.valueOf(old), sig, "GET", "/x"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.STALE);
    }

    @Test
    void missingHeader_isMissing() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        assertThat(v.verify(null, "x", "GET", "/x"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.MISSING);
        assertThat(v.verify("123", null, "GET", "/x"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.MISSING);
    }

    @Test
    void malformedTimestamp_isMalformed() {
        var v = new GatewaySignatureVerifier(SECRET, 60);
        assertThat(v.verify("not-a-number", "x", "GET", "/x"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.MALFORMED);
    }

    @Test
    void rejectsTooShortSecret() {
        // base64 of "tiny" decodes to 4 bytes — below 16-byte minimum
        assertThatThrownBy(() -> new GatewaySignatureVerifier("dGlueQ==", 60))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new GatewaySignatureVerifier(null, 60))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new GatewaySignatureVerifier("   ", 60))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void signer_andVerifier_useSameAlgorithm() {
        // GatewaySigner is a thin wrapper around the verifier; ensure they round-trip.
        var signer = new GatewaySigner(SECRET, 60);
        var verifier = new GatewaySignatureVerifier(SECRET, 60);
        var s = signer.sign("POST", "/api/auth/login");
        assertThat(verifier.verify(String.valueOf(s.timestamp()), s.signature(), "POST", "/api/auth/login"))
                .isEqualTo(GatewaySignatureVerifier.Outcome.OK);
    }
}
