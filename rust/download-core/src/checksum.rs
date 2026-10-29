//! Streaming checksums. Files are hashed without loading them into memory, so a
//! 20 GB MKV costs a constant few kilobytes of RAM.

use md5::Md5;
use sha2::{Digest, Sha256};
use std::fs::File;
use std::io::{self, Read};
use std::path::Path;

fn hash_reader<R: Read>(mut reader: R, mut digest: impl Digest) -> io::Result<String> {
    let mut buffer = vec![0u8; 64 * 1024];
    loop {
        let read = reader.read(&mut buffer)?;
        if read == 0 {
            break;
        }
        digest.update(&buffer[..read]);
    }
    Ok(to_hex(&digest.finalize()))
}

fn to_hex(bytes: &[u8]) -> String {
    let mut out = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        out.push_str(&format!("{byte:02x}"));
    }
    out
}

pub fn sha256_reader<R: Read>(reader: R) -> io::Result<String> {
    hash_reader(reader, Sha256::new())
}

pub fn md5_reader<R: Read>(reader: R) -> io::Result<String> {
    hash_reader(reader, Md5::new())
}

pub fn sha256_file(path: &Path) -> io::Result<String> {
    sha256_reader(File::open(path)?)
}

pub fn md5_file(path: &Path) -> io::Result<String> {
    md5_reader(File::open(path)?)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sha256_matches_known_vector() {
        let hex = sha256_reader(&b"abc"[..]).unwrap();
        assert_eq!(
            hex,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }

    #[test]
    fn md5_matches_known_vector() {
        let hex = md5_reader(&b"abc"[..]).unwrap();
        assert_eq!(hex, "900150983cd24fb0d6963f7d28e17f72");
    }

    #[test]
    fn hashes_large_input_in_chunks() {
        let data = vec![7u8; 1_000_000];
        let hex = sha256_reader(&data[..]).unwrap();
        assert_eq!(hex.len(), 64);
    }
}
