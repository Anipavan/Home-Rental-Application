package com.spa.home_rental_application.kyc_service.provider;

import com.spa.home_rental_application.kyc_service.Exceptionclass.KycProviderException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;

/**
 * Parses the signed eAadhaar XML that DigiLocker returns from
 * {@code /public/oauth2/3/xml/eaadhaar}.
 *
 * <p>The document is a UIDAI-issued XML envelope of the shape:
 * <pre>{@code
 * <OfflinePaperlessKyc>
 *   <UidData uid="XXXXXXXX1234">      <-- masked to last 4 in some flavours
 *     <Poi name="John Doe" gender="M" dob="01-01-1990"/>
 *     <Poa ... />
 *     <Pht>BASE64...</Pht>
 *   </UidData>
 *   <Signature xmlns="http://www.w3.org/2000/09/xmldsig#"> ... </Signature>
 * </OfflinePaperlessKyc>
 * }</pre>
 *
 * <p><b>XMLDSig signature verification is deliberately deferred to v2.</b>
 * The DigiLocker access_token already proves the document came from MeitY
 * over TLS, so accepting the parsed contents for the MVP is acceptable —
 * the {@code signatureVerified} flag on the returned record stays false
 * so the compliance report can downgrade confidence accordingly. Before
 * we onboard high-value transactions we MUST add UIDAI's public-key
 * verification here (KUA-cert chain → {@link javax.xml.crypto.dsig.XMLSignatureFactory}).
 *
 * <p>The parser is hardened against XXE: DTDs, external general entities,
 * and external parameter entities are all disabled
 * ({@link XMLConstants#FEATURE_SECURE_PROCESSING}).
 */
@Component
@Slf4j
public class EAadhaarXmlParser {

    /**
     * Extracts the user-identifying fields from a UIDAI eAadhaar XML payload.
     *
     * @param xml the raw XML string as returned by DigiLocker
     * @return a {@link EAadhaarData} record with name/dob/gender/last4
     * @throws KycProviderException if the document is malformed or missing required fields
     */
    public EAadhaarData parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new KycProviderException("Empty eAadhaar XML from DigiLocker");
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            // XXE hardening — see OWASP XML External Entity cheat sheet.
            // DigiLocker payloads never need DTDs, so disable them outright.
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();

            // UIDAI publishes a few variants of the envelope; we accept the
            // ones we've actually seen in the wild. {@code Poi} ("Proof of
            // Identity") is the element that carries name/gender/dob in
            // every variant of the spec we've tested against.
            String uid = firstAttr(doc, "UidData", "uid");
            String name = firstAttr(doc, "Poi", "name");
            String gender = firstAttr(doc, "Poi", "gender");
            String dob = firstAttr(doc, "Poi", "dob");
            String address = combineAddress(doc);

            if (name == null || name.isBlank()) {
                throw new KycProviderException("eAadhaar XML missing name");
            }
            if (uid == null || uid.length() < 4) {
                throw new KycProviderException("eAadhaar XML missing uid / too short");
            }

            String last4 = uid.substring(uid.length() - 4);
            // Note: the {@code uid} attribute is itself often returned
            // masked (e.g. "XXXXXXXX1234"). If it's the full 12-digit
            // value the caller is responsible for hashing immediately
            // and discarding the plain text — see KycServiceImpl.
            log.info("eAadhaar parsed name={} last4={} dobPresent={} signatureVerified=false (v2)",
                    name, last4, dob != null);

            return new EAadhaarData(
                    name.trim(),
                    normalizeGender(gender),
                    dob,
                    uid,           // raw uid — caller MUST hash + discard
                    last4,
                    address,
                    false          // signatureVerified — DEFER to v2
            );
        } catch (KycProviderException e) {
            throw e;
        } catch (Exception ex) {
            throw new KycProviderException("Failed to parse eAadhaar XML", ex);
        }
    }

    private String firstAttr(Document doc, String tag, String attr) {
        var nodes = doc.getElementsByTagName(tag);
        if (nodes == null || nodes.getLength() == 0) return null;
        Element el = (Element) nodes.item(0);
        String v = el.getAttribute(attr);
        return (v == null || v.isBlank()) ? null : v;
    }

    /**
     * Combines the parts of the &lt;Poa&gt; element into a single line. Used
     * for display only — never persisted to the KYC table (DPDP minimisation).
     */
    private String combineAddress(Document doc) {
        var nodes = doc.getElementsByTagName("Poa");
        if (nodes == null || nodes.getLength() == 0) return null;
        Element el = (Element) nodes.item(0);
        StringBuilder sb = new StringBuilder();
        String[] parts = {"house", "street", "loc", "vtc", "po", "subdist", "dist", "state", "country", "pc"};
        for (String p : parts) {
            String v = el.getAttribute(p);
            if (v != null && !v.isBlank()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(v);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Maps UIDAI's single-letter gender to our canonical labels. */
    private String normalizeGender(String g) {
        if (g == null) return null;
        return switch (g.trim().toUpperCase()) {
            case "M" -> "MALE";
            case "F" -> "FEMALE";
            case "T" -> "OTHER";
            default -> g;
        };
    }

    /**
     * Immutable carrier of the fields we pull out of an eAadhaar payload.
     *
     * @param name              full name on Aadhaar
     * @param gender            MALE / FEMALE / OTHER, or raw value if unknown
     * @param dob               date of birth as the string UIDAI returned (dd-MM-yyyy or yyyy)
     * @param rawUid            the {@code uid} attribute as-is — caller MUST hash and discard
     * @param last4             last 4 digits of the Aadhaar, safe to persist
     * @param address           single-line address for display; never persisted
     * @param signatureVerified false until we add XMLDSig verification in v2
     */
    public record EAadhaarData(
            String name,
            String gender,
            String dob,
            String rawUid,
            String last4,
            String address,
            boolean signatureVerified
    ) {}
}
