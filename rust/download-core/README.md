# vortexa-download-core

Rust core for Vortexa's download engine.

**Scope.** Pure logic and bulk I/O that benefits from native code:

| Module | What it owns |
|---|---|
| `segment` | byte-range planning, inclusive `Range` headers, dynamic re-splitting of the largest range |
| `resume` | whether a partial file can be continued (ETag / Last-Modified / size validation) |
| `checksum` | streaming SHA-256 and MD5 |

HTTP transport deliberately stays in Kotlin/OkHttp: HTTP/2, proxies, cookie jars,
custom CAs and certificate pinning already live there, so the JNI boundary only
carries data and never callbacks.

## Test

```bash
cargo test          # 21 unit tests, no Android toolchain needed
```

## Android build (pending)

The `jni-bridge` feature adds the JNI entry points. Building the `.so` requires
the NDK (`ndk;28.2.13676358`) plus the Rust Android targets:

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
cargo build --release --features jni-bridge --target aarch64-linux-android
```

Until that runs in CI, the app uses `KotlinSegmentPlanner`, which is covered by
the *same* test vectors as `segment.rs` so the two cannot silently diverge.
