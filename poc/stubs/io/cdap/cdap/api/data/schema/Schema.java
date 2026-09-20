package io.cdap.cdap.api.data.schema;

import java.util.Map;
import java.util.HashMap;

/**
 * NON-SUBSTANTIVE STUB for the PoC harness only -- not the real CDAP class.
 * The real Schema is CDAP's Avro-like schema-definition utility, used
 * elsewhere for encoding/decoding UserIdentity to bytes for token storage.
 * UserIdentity.java references it only in a static initializer
 * (the Schemas inner class) that builds schema *definitions* for that
 * encoding -- it is never invoked by, and has no bearing on,
 * ProxyUserIdentityExtractor.extract() (the code under test), which never
 * touches encoding/decoding at all. This stub provides just enough surface
 * for that static initializer to compile and run without error; its
 * return values are never read by anything relevant to this PoC.
 */
public class Schema {
  public enum Type { STRING, LONG }

  public static class Field {
    public static Field of(String name, Schema schema) {
      return new Field();
    }
  }

  public static Schema of(Type type) {
    return new Schema();
  }

  public static Schema arrayOf(Schema schema) {
    return new Schema();
  }

  public static Schema enumWith(Class<? extends Enum<?>> enumClass) {
    return new Schema();
  }

  public static Schema recordOf(String name, Field... fields) {
    return new Schema();
  }
}
