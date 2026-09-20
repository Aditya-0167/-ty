# cdap: PROXY authentication mode accepts any client-supplied identity header with zero verification that it came from a trusted proxy

**Program:** Google Cloud VRP (`cdapio/cdap` is `SCOPE_CLOUD_VRP` / `TIER_OT1` per
`google/bughunters` `oss-repository-tier/external_repositories.txtpb`)
**Repo / component:** `cdapio/cdap`, `cdap-security/src/main/java/io/cdap/cdap/security/auth/ProxyUserIdentityExtractor.java`
**Verified against commit:** `35493c2a4f66e16f4324ee4ef4c7243c64cd8a72`
**Class:** Missing authentication — identity trusted from an unverified HTTP header
**Vulnerability type suggestion:** Auth Bypass
**Reporter note on severity:** this is a real, code-level gap, verified end-to-end with the real Java class. Whether it's remotely exploitable against any specific real deployment depends on that deployment's network topology, which I can't see from here — full honest breakdown in "Honest caveats." I'm not asserting a severity tier.

## Summary

`security.authentication.mode` is a documented, first-class CDAP configuration option (`cdap-common/src/main/resources/cdap-default.xml`):

```
Supported modes include MANAGED and PROXY.
MANAGED mode supports a CDAP-managed authentication server and uses CDAP's
access tokens to authenticate the user.
PROXY mode assumes that authentication has already been performed upstream
and instead extracts the user's identity and credentials from the
configured headers.
```

When `PROXY` mode is selected, `ProxyUserIdentityExtractor` becomes the entire authentication mechanism for every CDAP API request (it fully replaces `AccessTokenIdentityExtractor`, the cryptographic-token-based mechanism used in the default `MANAGED` mode — see `ExternalAuthenticationModule.java`). I read its `extract()` method and it does exactly what it says: take a configured header's value and treat it as the authenticated username, with **no check of any kind** that the request actually came from a trusted proxy rather than directly from whoever is talking to the server — no shared secret, no source-IP allowlist, no mutual TLS requirement, nothing. I confirmed there is no such configuration option anywhere near it (`grep` for anything else under `security.authentication.proxy.*` in `Constants.java` turns up only the one header-name property).

I verified this by compiling and running the real, unmodified class.

## Root cause, with exact unmodified code

**`ProxyUserIdentityExtractor.extract()`, in full:**
```java
@Override
public UserIdentityExtractionResponse extract(HttpRequest request)
    throws UserIdentityExtractionException {
  long now = System.currentTimeMillis();
  if (userIdentityHeader == null) {
    return new UserIdentityExtractionResponse(UserIdentityExtractionState.ERROR_MISSING_IDENTITY,
        "User identity header config missing");
  }
  String userIdentity = request.headers().get(userIdentityHeader);
  if (userIdentity == null) {
    return new UserIdentityExtractionResponse(UserIdentityExtractionState.ERROR_MISSING_IDENTITY,
        "No user identity found");
  }

  UserIdentity identity = new UserIdentity(userIdentity, UserIdentity.IdentifierType.EXTERNAL,
      new LinkedHashSet<>(), now, now + EXPIRATION_SECS);
  ...
  return new UserIdentityExtractionResponse(new UserIdentityPair(userCredential, identity));
}
```
`userIdentityHeader` is just the configured header *name* (`security.authentication.proxy.user.identity.header`); whatever string value is present under that header name in the incoming request becomes the authenticated identity, full stop. This `UserIdentity` then flows into `SecurityRequestContext` and is what every subsequent authorization decision for that request is made against.

## Proof of Concept

I compiled and ran the **real, unmodified** `ProxyUserIdentityExtractor.java` (and its real supporting types: `UserIdentity`, `UserIdentityExtractor`, `UserIdentityPair`, `UserIdentityExtractionResponse`, `UserIdentityExtractionState`, `UserIdentityExtractionException`, `Credential`, `Constants`) against a **real** `io.netty.handler.codec.http.HttpRequest` object — not a mock of either the CDAP class or the HTTP request type.

Three files needed disclosed, non-substantive stubs to compile standalone outside the full Maven build (this environment has no Maven Central access; dependencies came from Ubuntu's `libnetty-java`/`libguava-java`/`libguice-java` packages instead), all attached under `poc/stubs/` with a header on each explaining exactly what it replaces and why it doesn't touch the security logic under test:
- `CConfiguration` — the real class extends Hadoop's `Configuration` and pulls in an unrelated dependency tree; the code under test only ever calls `.get(String)` on it, which the stub implements as a plain map lookup.
- `io.cdap.cdap.api.data.schema.Schema` — referenced only in `UserIdentity`'s codec-related static initializer (for token *encoding*, used elsewhere, never by `extract()`); stubbed with just enough surface to let that initializer run.
- `NamespaceId` — `Constants.java` uses exactly one static member of it (`NamespaceId.SYSTEM.getNamespace()`) for an unrelated constant; stubbed minimally.

```bash
git clone https://github.com/cdapio/cdap.git
cd cdap
git checkout 35493c2a4f66e16f4324ee4ef4c7243c64cd8a72

# On Debian/Ubuntu:
apt-get install -y openjdk-21-jdk-headless libnetty-java libguava-java libguice-java libslf4j-java

bash /path/to/this/report/poc/run-repro.sh "$(pwd)"
```

Captured output (attached, `captured-output.txt`):
```
=== Scenario: attacker sends a direct request claiming to be 'admin' ===
Extraction success: true
Resulting identity username: admin
Resulting identity type: EXTERNAL
Credential supplied by attacker: null

=== Result ===
VULNERABLE: the real, unmodified ProxyUserIdentityExtractor accepted an
attacker-chosen identity ('admin') from a plain HTTP header, with no
verification of any kind that the request came from a trusted proxy
rather than directly from the attacker.
```

A GitHub Actions workflow (attached, `verify-poc.yml`) reproduces the same steps on independent infrastructure.

Verified against commit `35493c2a4f66e16f4324ee4ef4c7243c64cd8a72`.

## Due diligence: I checked whether this generalizes further, and it doesn't quite

CDAP has a second, related-looking mechanism — `AuthenticationChannelHandler` (`cdap-common`), installed on every CDAP service via `CommonNettyHttpServiceBuilder` whenever security is enabled at all (not just in PROXY mode). It reads a *fixed* header (`CDAP-UserId`, distinct from the configurable PROXY header) and sets it directly into `SecurityRequestContext` with no verification either. I traced this all the way to the Router's own `AuthenticationHandler` (`cdap-gateway`) — the actual external-facing entry point — and confirmed it does the *real* authentication first (via `UserIdentityExtractor`, i.e., real token validation in `MANAGED` mode or the header-trust of `PROXY` mode) and only *then* explicitly sets `CDAP-UserId` to the validated username via Netty's `HttpHeaders.set()`, which replaces any existing value for that header — so a client-supplied `CDAP-UserId` on the way in does not survive to reach internal services. That path is intentionally designed and correctly implemented: internal CDAP services trust the Router because the Router already did real verification. I'm including this because I want to be precise about scope: **the gap I'm reporting is specific to `ProxyUserIdentityExtractor` / PROXY mode**, not a universal bypass of CDAP's whole security model.

I also checked whether this could be escalated to `IdentifierType.INTERNAL` (a more privileged identity class) by setting the `Authorization` header to `INTERNAL <anything>`: `ProxyUserIdentityExtractor` will happily label the identity as INTERNAL, but `InternalAccessEnforcer` (the enforcement point that actually matters for internal-tier access) independently re-validates the credential as a real signed access token via `TokenManager.validateSecret()` — an attacker-supplied arbitrary string fails that check. So the INTERNAL escalation path is not viable; the achievable impact is impersonating any **EXTERNAL**-tier identity (i.e., any regular or admin user), not the separately-protected internal-system tier.

## Completing the chain: does the spoofed identity actually reach a real permission decision?

The PoC above proves the extraction step is unverified. I went further and traced (by reading the real, unmodified source — I did not additionally compile this second layer, its dependency tree is significantly deeper; noting that split honestly) how that identity is actually used downstream:

`MasterAuthenticationContext` (`cdap-security/src/main/java/io/cdap/cdap/security/auth/context/MasterAuthenticationContext.java`), used for REST endpoint requests per its own Javadoc, in full:
```java
@Override
public Principal getPrincipal() {
  // When requests come in via rest endpoints, the userId is updated inside SecurityRequestContext, so give that
  // precedence.
  String userId = SecurityRequestContext.getUserId();
  Credential userCredential = SecurityRequestContext.getUserCredential();
  if (userId == null) {
    userId = UserGroupInformation.getCurrentUser().getShortUserName();
  }
  return new Principal(userId, Principal.PrincipalType.USER, userCredential);
}
```
This builds the `Principal` used for authorization **directly** from `SecurityRequestContext.getUserId()` — the same value the Router sets from `ProxyUserIdentityExtractor`'s output — with no additional verification at this layer either.

That `Principal` then goes straight into `DefaultAccessEnforcer.enforce(EntityId entity, Principal principal, Set<? extends Permission> permissions)`, which performs the actual RBAC decision. One additional, real precondition I found while tracing this: `enforce()` returns immediately (implicitly allowing) if `security.authorization.enabled` is false — so the full impersonation-to-permission-grant chain requires that flag to be `true` as well as `security.authentication.mode=PROXY`. For a deployment with both turned on (i.e., one actually trying to enforce fine-grained per-user permissions, not just skip authorization entirely), the spoofed "admin" identity is exactly what gets checked against real permissions — completing the chain from one forged header to an actual authorization outcome.

## Impact

- If PROXY mode is configured and the CDAP router/service is reachable directly (bypassing whatever proxy is supposed to front it), a request with a single forged header authenticates as any user of the attacker's choosing, including an admin account, for all subsequent authorization decisions. I traced this through to `DefaultAccessEnforcer`'s actual RBAC check (see previous section) — provided `security.authorization.enabled=true` (an additional real precondition), the spoofed identity is exactly what gets checked against real permissions, not just accepted and left unused.
- This is a real, code-level absence of defense-in-depth: the security of this documented, supported mode rests entirely on the operator's network topology being correct, with nothing in the application itself to catch a misconfiguration or a direct-access path that shouldn't exist.
- I want to flag directly: this is architecturally similar in shape to a finding I sent for `google-gemini/gemini-cli`'s devtools WebSocket endpoint (missing verification of request origin, relying entirely on network-level assumptions) — same general class of gap (trust a request without verifying its claimed source), different product, different specific mechanism. Not the same root cause or the same code, just worth knowing the pattern isn't a one-off for me today.

## Honest caveats

- I cannot verify whether any real, live CDAP or Google Cloud Data Fusion deployment actually uses `PROXY` mode, or what network-level protections (firewalling, VPC isolation, mutual TLS between the fronting proxy and CDAP) exist around it in practice. The default mode is `MANAGED`, not `PROXY` — this requires an explicit operator choice.
- I have not stood up a full running CDAP cluster and sent a real network request end-to-end; I verified the extraction logic itself compiles and runs with the real class and a real Netty request object, and traced the surrounding request-handling code by reading it, including checking the Router's mitigating behavior for the separate internal-header mechanism.
- No claims about severity tier or reward — that's Google's call, and I recognize the real-world exploitability here depends on deployment specifics I can't see.
