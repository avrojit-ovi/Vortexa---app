//! Vortexa download core.
//!
//! Scope: everything that is pure logic or bulk I/O and benefits from a native
//! implementation — byte-range planning, resume validation, and checksums.
//! Network transport deliberately stays in Kotlin/OkHttp, where HTTP/2,
//! proxies, cookies and certificate pinning already live, so the JNI boundary
//! carries only data, never callbacks.

pub mod checksum;
pub mod resume;
pub mod segment;

#[cfg(feature = "jni-bridge")]
mod jni_bridge;

pub use checksum::{md5_file, md5_reader, sha256_file, sha256_reader};
pub use resume::{decide, Decision, Rejection, RemoteMetadata, StoredDownload};
pub use segment::{plan, split_largest, Segment, DEFAULT_MIN_SEGMENT_BYTES};

/// Library version, surfaced to the JVM for diagnostics.
pub const VERSION: &str = env!("CARGO_PKG_VERSION");
