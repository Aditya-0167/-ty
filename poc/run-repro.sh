#!/usr/bin/env bash
# Self-contained reproduction. Copies the REAL, unmodified files this PoC
# depends on out of a real cdapio/cdap checkout (passed as $1), combines
# them with the disclosed non-substantive stubs in stubs/ (see each
# stub's file header for exactly what it replaces and why), compiles, and
# runs the real Java driver.
set -euo pipefail
CDAP_DIR="${1:?Usage: $0 /path/to/cdapio/cdap/checkout}"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"

mkdir -p "$WORK/src/io/cdap/cdap/security/auth"
mkdir -p "$WORK/src/io/cdap/cdap/proto/security"
mkdir -p "$WORK/src/io/cdap/cdap/common/conf"

# --- Real, unmodified files, copied straight from the real checkout ---
cp "$CDAP_DIR/cdap-security/src/main/java/io/cdap/cdap/security/auth/ProxyUserIdentityExtractor.java" \
   "$WORK/src/io/cdap/cdap/security/auth/"
for f in UserIdentity UserIdentityExtractor UserIdentityExtractionException \
         UserIdentityExtractionResponse UserIdentityExtractionState UserIdentityPair; do
  cp "$CDAP_DIR/cdap-security/src/main/java/io/cdap/cdap/security/auth/$f.java" \
     "$WORK/src/io/cdap/cdap/security/auth/"
done
cp "$CDAP_DIR/cdap-proto/src/main/java/io/cdap/cdap/proto/security/Credential.java" \
   "$WORK/src/io/cdap/cdap/proto/security/"
cp "$CDAP_DIR/cdap-common/src/main/java/io/cdap/cdap/common/conf/Constants.java" \
   "$WORK/src/io/cdap/cdap/common/conf/"

# --- Disclosed, non-substantive stubs (see stubs/ file headers) ---
cp -r "$HERE/stubs/io" "$WORK/src/"

# --- The real driver ---
cp "$HERE/ProxySpoofRepro.java" "$WORK/"

CP="/usr/share/java/netty-all.jar:/usr/share/java/guava.jar:/usr/share/java/guice.jar:/usr/share/java/slf4j-api.jar:/usr/share/java/jsr305.jar:/usr/share/java/netty-codec-http.jar:/usr/share/java/netty-codec.jar:/usr/share/java/netty-buffer.jar:/usr/share/java/netty-common.jar:/usr/share/java/netty-transport.jar:/usr/share/java/netty-handler.jar:/usr/share/java/netty-resolver.jar"
cd "$WORK"
mkdir out
find src -name "*.java" > sources.txt
javac -cp "$CP" -d out @sources.txt
javac -cp "$CP:out" -d out ProxySpoofRepro.java
java -cp "$CP:out" ProxySpoofRepro
