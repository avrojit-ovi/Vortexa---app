//! Byte-range planning for parallel downloads.
//!
//! Ranges are inclusive on both ends, matching HTTP `Range: bytes=start-end`
//! semantics, so no off-by-one conversion is needed at the network boundary.

use core::fmt;

/// One contiguous byte range of the remote file.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Segment {
    pub index: u32,
    /// Inclusive first byte.
    pub start: u64,
    /// Inclusive last byte.
    pub end: u64,
}

impl Segment {
    pub fn new(index: u32, start: u64, end: u64) -> Self {
        debug_assert!(end >= start, "segment end must not precede start");
        Self { index, start, end }
    }

    /// Number of bytes covered by this segment.
    pub fn len(&self) -> u64 {
        self.end.saturating_sub(self.start) + 1
    }

    pub fn is_empty(&self) -> bool {
        self.end < self.start
    }

    /// Value for the `Range` request header.
    pub fn range_header(&self) -> String {
        format!("bytes={}-{}", self.start, self.end)
    }

    /// The part of this segment that still has to be fetched after `done` bytes.
    pub fn remaining(&self, done: u64) -> Option<Segment> {
        if done >= self.len() {
            return None;
        }
        Some(Segment {
            index: self.index,
            start: self.start + done,
            end: self.end,
        })
    }
}

impl fmt::Display for Segment {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "#{} {}..{} ({} B)", self.index, self.start, self.end, self.len())
    }
}

/// Smallest segment we are willing to create; below this the request overhead
/// (TLS + headers per connection) outweighs the parallelism.
pub const DEFAULT_MIN_SEGMENT_BYTES: u64 = 512 * 1024;

/// Splits `total` bytes into at most `parts` contiguous segments.
///
/// The part count is reduced when the file is too small to give every segment
/// at least `min_segment_bytes`. The last segment absorbs the remainder so the
/// union is always exactly `0..=total-1`.
pub fn plan(total: u64, parts: u32, min_segment_bytes: u64) -> Vec<Segment> {
    if total == 0 {
        return Vec::new();
    }
    let min = min_segment_bytes.max(1);
    let affordable = (total / min).max(1) as u32;
    let parts = parts.clamp(1, 32).min(affordable).max(1);

    let base = total / parts as u64;
    let mut segments = Vec::with_capacity(parts as usize);
    let mut start = 0u64;
    for index in 0..parts {
        let is_last = index == parts - 1;
        let end = if is_last { total - 1 } else { start + base - 1 };
        segments.push(Segment::new(index, start, end));
        start = end + 1;
    }
    segments
}

/// Splits the largest unfinished segment in half, so slow connections do not
/// hold the whole download hostage. `next_index` is the id for the new segment.
pub fn split_largest(segments: &[Segment], next_index: u32) -> Option<(usize, Segment, Segment)> {
    let (position, largest) = segments
        .iter()
        .enumerate()
        .filter(|(_, segment)| segment.len() >= 2)
        .max_by_key(|(_, segment)| segment.len())?;

    if largest.len() < 2 {
        return None;
    }

    let first_len = largest.len() / 2;
    let left = Segment::new(largest.index, largest.start, largest.start + first_len - 1);
    let right = Segment::new(next_index, left.end + 1, largest.end);
    Some((position, left, right))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_covers(segments: &[Segment], total: u64) {
        assert_eq!(segments.first().unwrap().start, 0);
        assert_eq!(segments.last().unwrap().end, total - 1);
        for window in segments.windows(2) {
            assert_eq!(window[0].end + 1, window[1].start, "segments must be contiguous");
        }
        let sum: u64 = segments.iter().map(Segment::len).sum();
        assert_eq!(sum, total, "segments must not overlap or skip bytes");
    }

    #[test]
    fn empty_file_has_no_segments() {
        assert!(plan(0, 8, DEFAULT_MIN_SEGMENT_BYTES).is_empty());
    }

    #[test]
    fn small_file_gets_one_segment() {
        let segments = plan(100, 8, DEFAULT_MIN_SEGMENT_BYTES);
        assert_eq!(segments.len(), 1);
        assert_eq!(segments[0], Segment::new(0, 0, 99));
    }

    #[test]
    fn even_split_covers_every_byte() {
        let segments = plan(4_000, 4, 100);
        assert_eq!(segments.len(), 4);
        assert_eq!(segments[0].len(), 1_000);
        assert_covers(&segments, 4_000);
    }

    #[test]
    fn remainder_goes_to_last_segment() {
        let segments = plan(1_000, 3, 100);
        assert_covers(&segments, 1_000);
        assert_eq!(segments[2].len(), 1_000 - 333 * 2);
    }

    #[test]
    fn part_count_is_capped_by_minimum_segment_size() {
        // 700 bytes with a 512 byte floor can only afford one segment.
        let segments = plan(700, 8, DEFAULT_MIN_SEGMENT_BYTES);
        assert_eq!(segments.len(), 1);
    }

    #[test]
    fn part_count_is_clamped_to_32() {
        let segments = plan(64 * DEFAULT_MIN_SEGMENT_BYTES, 999, DEFAULT_MIN_SEGMENT_BYTES);
        assert_eq!(segments.len(), 32);
        assert_covers(&segments, 64 * DEFAULT_MIN_SEGMENT_BYTES);
    }

    #[test]
    fn range_header_is_inclusive() {
        assert_eq!(Segment::new(0, 0, 99).range_header(), "bytes=0-99");
        assert_eq!(Segment::new(1, 500, 999).range_header(), "bytes=500-999");
    }

    #[test]
    fn remaining_tracks_progress() {
        let segment = Segment::new(0, 100, 199);
        assert_eq!(segment.remaining(0), Some(Segment::new(0, 100, 199)));
        assert_eq!(segment.remaining(40), Some(Segment::new(0, 140, 199)));
        assert_eq!(segment.remaining(100), None);
        assert_eq!(segment.remaining(500), None);
    }

    #[test]
    fn split_largest_halves_the_biggest_range() {
        let segments = vec![Segment::new(0, 0, 99), Segment::new(1, 100, 999)];
        let (position, left, right) = split_largest(&segments, 2).unwrap();
        assert_eq!(position, 1);
        assert_eq!(left, Segment::new(1, 100, 549));
        assert_eq!(right, Segment::new(2, 550, 999));
        assert_eq!(left.len() + right.len(), segments[1].len());
    }

    #[test]
    fn split_largest_ignores_single_byte_ranges() {
        let segments = vec![Segment::new(0, 0, 0)];
        assert!(split_largest(&segments, 1).is_none());
    }
}
