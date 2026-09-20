package io.cdap.cdap.proto.id;

/**
 * NON-SUBSTANTIVE STUB for the PoC harness only -- not the real CDAP class.
 * The real NamespaceId is part of a large EntityId class hierarchy that
 * cdap-proto/cdap-common depend on broadly, none of which is relevant to
 * ProxyUserIdentityExtractor.extract() (the code under test). This stub
 * provides only the one static member (Constants.java's) actual usage
 * needs: NamespaceId.SYSTEM.getNamespace(). It does not affect the
 * behavior of the security-relevant code being tested in any way.
 */
public class NamespaceId {
  public static final NamespaceId SYSTEM = new NamespaceId();

  public String getNamespace() {
    return "system";
  }
}
