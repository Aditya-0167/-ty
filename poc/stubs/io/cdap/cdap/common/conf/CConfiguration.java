package io.cdap.cdap.common.conf;

import java.util.HashMap;
import java.util.Map;

/**
 * NON-SUBSTANTIVE STUB for the PoC harness only -- not the real CDAP class.
 * The real CConfiguration extends Hadoop's Configuration class and pulls
 * in a large dependency tree unrelated to the security logic under test.
 * ProxyUserIdentityExtractor's constructor only ever calls .get(String),
 * so this stub implements exactly that -- a simple key/value lookup --
 * and nothing else. This does not alter any logic in
 * ProxyUserIdentityExtractor itself, which is used completely unmodified.
 */
public class CConfiguration {
  private final Map<String, String> values = new HashMap<>();

  public static CConfiguration create() {
    return new CConfiguration();
  }

  public void set(String key, String value) {
    values.put(key, value);
  }

  public String get(String key) {
    return values.get(key);
  }
}
