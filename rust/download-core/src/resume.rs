//! Decides whether a partially downloaded file can be continued.
//!
//! The rule is deliberately conservative: without a strong validator (ETag) or
//! a Last-Modified timestamp plus an unchanged length, we restart instead of
//! risking a silently corrupted file.

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StoredDownload {
    pub downloaded_bytes: u64,
    pub total_bytes: u64,
    pub etag: Option<String>,
    pub last_modified: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RemoteMetadata {
    pub total_bytes: u64,
    pub etag: Option<String>,
    pub last_modified: Option<String>,
    pub supports_ranges: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Rejection {
    NothingDownloaded,
    ServerIgnoresRanges,
    MissingValidator,
    EtagChanged,
    LastModifiedChanged,
    SizeChanged,
    AlreadyComplete,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Decision {
    /// Start from byte 0, truncating whatever is on disk.
    Restart(Rejection),
    /// Continue from `offset` bytes already written.
    Continue { offset: u64 },
}

pub fn decide(stored: &StoredDownload, remote: &RemoteMetadata) -> Decision {
    if !remote.supports_ranges {
        return Decision::Restart(Rejection::ServerIgnoresRanges);
    }
    if stored.downloaded_bytes == 0 {
        return Decision::Restart(Rejection::NothingDownloaded);
    }
    if stored.downloaded_bytes >= stored.total_bytes && stored.total_bytes > 0 {
        return Decision::Restart(Rejection::AlreadyComplete);
    }
    if stored.total_bytes != remote.total_bytes {
        return Decision::Restart(Rejection::SizeChanged);
    }
    match (&stored.etag, &remote.etag) {
        (Some(local), Some(fresh)) if local != fresh => {
            return Decision::Restart(Rejection::EtagChanged);
        }
        (Some(_), None) => return Decision::Restart(Rejection::MissingValidator),
        (None, Some(_)) => return Decision::Restart(Rejection::MissingValidator),
        _ => {}
    }
    match (&stored.last_modified, &remote.last_modified) {
        (Some(local), Some(fresh)) if local != fresh => {
            return Decision::Restart(Rejection::LastModifiedChanged);
        }
        (Some(_), None) => return Decision::Restart(Rejection::MissingValidator),
        (None, Some(_)) => return Decision::Restart(Rejection::MissingValidator),
        _ => {}
    }
    if stored.etag.is_none() && stored.last_modified.is_none() {
        return Decision::Restart(Rejection::MissingValidator);
    }
    Decision::Continue { offset: stored.downloaded_bytes }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn stored(downloaded: u64, total: u64, etag: Option<&str>, lm: Option<&str>) -> StoredDownload {
        StoredDownload {
            downloaded_bytes: downloaded,
            total_bytes: total,
            etag: etag.map(str::to_owned),
            last_modified: lm.map(str::to_owned),
        }
    }

    fn remote(total: u64, etag: Option<&str>, lm: Option<&str>, ranges: bool) -> RemoteMetadata {
        RemoteMetadata {
            total_bytes: total,
            etag: etag.map(str::to_owned),
            last_modified: lm.map(str::to_owned),
            supports_ranges: ranges,
        }
    }

    #[test]
    fn continues_with_matching_etag() {
        let decision = decide(
            &stored(400, 1_000, Some("\"abc\""), None),
            &remote(1_000, Some("\"abc\""), None, true),
        );
        assert_eq!(decision, Decision::Continue { offset: 400 });
    }

    #[test]
    fn continues_with_matching_last_modified() {
        let decision = decide(
            &stored(400, 1_000, None, Some("Wed, 21 Oct 2026 07:28:00 GMT")),
            &remote(1_000, None, Some("Wed, 21 Oct 2026 07:28:00 GMT"), true),
        );
        assert_eq!(decision, Decision::Continue { offset: 400 });
    }

    #[test]
    fn restarts_when_etag_changes() {
        let decision = decide(
            &stored(400, 1_000, Some("\"old\""), None),
            &remote(1_000, Some("\"new\""), None, true),
        );
        assert_eq!(decision, Decision::Restart(Rejection::EtagChanged));
    }

    #[test]
    fn restarts_when_size_changes() {
        let decision = decide(
            &stored(400, 1_000, Some("\"abc\""), None),
            &remote(1_200, Some("\"abc\""), None, true),
        );
        assert_eq!(decision, Decision::Restart(Rejection::SizeChanged));
    }

    #[test]
    fn restarts_without_any_validator() {
        let decision = decide(&stored(400, 1_000, None, None), &remote(1_000, None, None, true));
        assert_eq!(decision, Decision::Restart(Rejection::MissingValidator));
    }

    #[test]
    fn restarts_when_server_drops_the_validator() {
        let decision = decide(
            &stored(400, 1_000, Some("\"abc\""), None),
            &remote(1_000, None, None, true),
        );
        assert_eq!(decision, Decision::Restart(Rejection::MissingValidator));
    }

    #[test]
    fn restarts_when_ranges_are_unsupported() {
        let decision = decide(
            &stored(400, 1_000, Some("\"abc\""), None),
            &remote(1_000, Some("\"abc\""), None, false),
        );
        assert_eq!(decision, Decision::Restart(Rejection::ServerIgnoresRanges));
    }

    #[test]
    fn restarts_when_nothing_was_downloaded() {
        let decision = decide(
            &stored(0, 1_000, Some("\"abc\""), None),
            &remote(1_000, Some("\"abc\""), None, true),
        );
        assert_eq!(decision, Decision::Restart(Rejection::NothingDownloaded));
    }
}
