# Synthetic HTTPS fixtures

`test-ca.crt` is a public, synthetic test CA certificate. `server.p12` contains
only a synthetic localhost server key and its certificate chain. Its intentionally
public test password is `public-test-fixture`. Neither file contains provider or
application credentials. They belong only in test resources.

Certificates are valid from 2020-01-01 for 36,500 days to avoid real-clock expiry
in deterministic tests. The server certificate has `DNS:localhost` as its only
subject alternative name. A request to `127.0.0.1` must therefore fail hostname
validation even when the test CA is trusted.

Created with Java 25 `keytool`: RSA 2048 / SHA256withRSA, a CA with certificate-signing
usage, and a signed server certificate with digital-signature/key-encipherment and
server-auth usage. To replace them, generate a new synthetic CA and server key in
an ignored build directory, sign the localhost CSR, import the CA followed by the
signed server certificate into a PKCS12 store, and copy only the public CA and
server store here. Never use a production CA or key.

`ControlledHttpsServer` binds IPv4 loopback on an ephemeral port. Its injected
client trusts only this test CA and keeps normal hostname verification. No JVM
global trust store, system property or default SSL context is changed. Close both
fixture and client with try-with-resources. Synthetic request captures are bounded
to 8,193 bytes and must never be used with real tokens.

The certificate store, CA certificate and controlled HTTPS server were reused from
the `origin/llm-feature` snapshot `7d028e4`, then adapted for this common benchmark
fixture. These are synthetic test credentials only.

`FakeMonotonicClock`, `DeterministicScheduler`, `CapturingLlmClient` and
`CompletionGate` provide explicit time, deadline, transport completion and UI
delivery control. The gate accepts `Platform::runLater` in JavaFX tests; its own
self-test uses a queued executor and starts no extra JavaFX toolkit.
