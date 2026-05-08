package com.spa.home_rental_application.compliance_service.provider;

import com.spa.home_rental_application.compliance_service.DTO.Request.ReraRegisterRequest;
import com.spa.home_rental_application.compliance_service.config.ComplianceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Stub RERA adapter — generates a plausible-looking registration number
 * and a 5-year expiry. Real state adapters would HTTP-POST to the state
 * portal here.
 */
@Component
@ConditionalOnProperty(prefix = "app.compliance.rera", name = "provider", havingValue = "MOCK", matchIfMissing = true)
@Slf4j
public class MockReraPortalAdapter implements ReraPortalAdapter {

    private final ComplianceProperties props;

    public MockReraPortalAdapter(ComplianceProperties props) {
        this.props = props;
    }

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public RegistrationResult register(ReraRegisterRequest request) {
        String stateCode = request.state().substring(0, Math.min(3, request.state().length()))
                .toUpperCase(Locale.ROOT);
        String number = "PRM/KA/RERA/" + stateCode + "/"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LocalDate expiry = LocalDate.now().plusYears(props.getRera().getRegistrationValidityYears());
        log.info("[MOCK-RERA] register propertyId={} state={} → {}",
                request.propertyId(), request.state(), number);
        return new RegistrationResult(true, number, "MOCK-PORTAL", expiry, null);
    }
}
