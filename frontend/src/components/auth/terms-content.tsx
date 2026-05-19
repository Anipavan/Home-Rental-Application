/**
 * Terms & Conditions + Privacy Policy body for the registration
 * modal. Kept in a separate component so the register page stays
 * focused on form logic, and so the same text can be reused later
 * on a standalone `/terms` route or a footer link.
 *
 * The Aadhaar / data-sharing clause (clause 4) is intentionally
 * explicit — KYC features later in the platform rely on the user
 * having consented to share government-issued ID details.
 */
export function TermsAndConditionsContent() {
  return (
    <div className="space-y-4 text-sm leading-relaxed text-muted-foreground">
      <p className="text-foreground font-medium">
        Last updated: {new Date().getFullYear()}
      </p>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          1. Acceptance of Terms
        </h3>
        <p>
          By creating an account on Anirudh Homes, you confirm that you
          have read, understood, and agree to be bound by these Terms &
          Conditions and our Privacy Policy. If you do not agree, please
          do not register or use the platform.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          2. Account & Eligibility
        </h3>
        <p>
          You must be at least 18 years old and legally able to enter
          contracts in your jurisdiction. The information you provide
          (name, email, phone, address) must be accurate and current —
          inaccurate details may delay or invalidate rental agreements.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          3. Use of the Platform
        </h3>
        <p>
          Anirudh Homes connects tenants with property owners and
          facilitates rent collection, lease management, document
          storage and tenant verification. You agree not to use the
          platform for fraudulent listings, harassment, or any unlawful
          purpose. Owners are responsible for the accuracy of property
          listings; tenants are responsible for the accuracy of their
          profile and payment information.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          4. Sharing of Personal &amp; Identity Information
        </h3>
        <p className="text-foreground">
          <strong>
            You consent to sharing personal information — including
            Aadhaar number, PAN, address proof, and other
            government-issued identity details — with Anirudh Homes for
            the purposes of KYC verification, tenant screening, and
            legal compliance with Indian rental regulations.
          </strong>
        </p>
        <p className="mt-2">
          We store this information securely (encrypted at rest, access
          restricted to authorised KYC and compliance personnel), and we
          share it with property owners or third parties{" "}
          <strong>only with your explicit consent</strong> for a
          specific tenancy. We never sell your data. You may request
          deletion of your identity documents at any time after your
          tenancy ends — see clause 8.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          5. Payments
        </h3>
        <p>
          Rent and deposit payments are processed via UPI. Anirudh
          Homes does not store your bank or UPI credentials —
          transactions are settled directly between tenant and owner
          via your UPI provider. We retain transaction metadata
          (amount, timestamp, payer/payee handles) for receipt and
          audit purposes.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          6. Privacy &amp; Data Security
        </h3>
        <p>
          All sensitive data is encrypted in transit (TLS) and at rest.
          Passwords are stored as one-way bcrypt hashes — we never see
          your plaintext password. We use industry-standard practices
          to protect your data, including rate-limiting, audit logging,
          and access controls on internal services.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          7. Communications
        </h3>
        <p>
          By registering, you agree to receive transactional
          communications (account confirmations, payment receipts,
          maintenance updates) via email and (optionally) WhatsApp /
          SMS on the phone number you provide. You may opt out of
          non-essential communications at any time from your profile
          settings.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          8. Data Retention &amp; Deletion
        </h3>
        <p>
          We retain your profile and tenancy records for as long as
          your account is active, plus a statutory retention period
          required by Indian tax and rental laws (typically 7 years for
          financial records). You may request account deletion at any
          time; we will anonymise personally identifiable information
          while preserving anonymised transaction logs for legal
          compliance.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          9. Limitation of Liability
        </h3>
        <p>
          Anirudh Homes acts as a platform connecting tenants and
          owners. We are not a party to the rental agreement itself.
          We do our best to verify users via KYC, but we are not
          liable for disputes arising between tenants and owners,
          property damage, or unpaid rent.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          10. Changes to These Terms
        </h3>
        <p>
          We may update these Terms from time to time. Material
          changes will be notified via email and shown on the platform
          before they take effect. Continued use of the platform after
          changes take effect constitutes acceptance.
        </p>
      </section>

      <section>
        <h3 className="text-foreground font-semibold mb-1.5">
          11. Contact
        </h3>
        <p>
          Questions about these Terms or our handling of your data?
          Reach out to{" "}
          <a
            href="mailto:support@anirudhhomes.in"
            className="text-primary hover:underline"
          >
            support@anirudhhomes.in
          </a>
          .
        </p>
      </section>
    </div>
  );
}
