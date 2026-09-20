# PoC: cdap PROXY-mode identity spoofing

Compiles and runs the real, unmodified `ProxyUserIdentityExtractor.java`
(plus its real supporting types) against a real Netty `HttpRequest`, and
shows it accepts an attacker-chosen `X-CDAP-UserId: admin` header as a
fully authenticated identity with zero verification.

## Run it yourself

Needs a JDK and a few Java libraries (no Maven Central access required —
these come from standard Debian/Ubuntu packages):

```bash
apt-get install -y openjdk-21-jdk-headless libnetty-java libguava-java libguice-java libslf4j-java

git clone https://github.com/cdapio/cdap.git
cd cdap
git checkout 35493c2a4f66e16f4324ee4ef4c7243c64cd8a72

bash /path/to/this/report/poc/run-repro.sh "$(pwd)"
```

`run-repro.sh` copies the real files it needs straight out of your `cdap`
checkout, combines them with the disclosed stubs in `stubs/` (each file
has a header explaining exactly what it replaces and why — none of them
touch the security logic under test), compiles everything, and runs the
real driver (`ProxySpoofRepro.java`).

Expected (vulnerable) output ends with:
```
VULNERABLE: the real, unmodified ProxyUserIdentityExtractor accepted an
attacker-chosen identity ('admin') from a plain HTTP header, with no
verification of any kind that the request came from a trusted proxy
rather than directly from the attacker.
```

See `captured-output.txt` for a full run, and
`../.github/workflows/verify-poc.yml` for an independently-runnable CI
version of the same steps.
