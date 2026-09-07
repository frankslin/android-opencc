# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Update the OpenCC submodule to upstream ver.1.4.2 (#1)
- Build the native library with CMake `add_subdirectory(OpenCC)` instead of
  hand-written ndk-build files, so an OpenCC upgrade is a submodule bump (#2)
- Remove the stray IDE workspace backup, the dead Bintray publishing properties
  and the wrong ProGuard file name in the demo app
- The dictionary data in the application's files directory is now versioned:
  `openccdata/VERSION` is compared with the bundled copy on the first
  conversion in each process and the data is re-installed when it differs,
  so an upgrade takes effect without the app calling `clearDictDataFolder()`.
  The `zFinished2` marker file is gone. Concurrent first calls no longer copy
  the data twice, and a failed copy raises `IllegalStateException` instead of
  being logged and then crashing in native code
- Ship consumer ProGuard rules in the AAR that keep the JNI entry point, so
  apps with a custom R8 configuration cannot break the native binding

### Fixed
- Characters outside the Basic Multilingual Plane (CJK Extension B and later,
  emoji) are now converted correctly: the text crosses JNI as UTF-8 bytes
  instead of Modified UTF-8 strings, so dictionary entries such as 㓆 -> 𠗣
  match, and results containing such characters no longer trip CheckJNI or
  come back corrupted
- A missing or corrupt dictionary file (for example after an interrupted
  first-run copy) now raises `IllegalStateException` from
  `ChineseConverter.convert()` instead of letting the C++ exception escape
  JNI and kill the process
- Use-after-free of the JNI text buffer in `ChineseConverter.convert()`, the
  cause of the SIGSEGV / bad_alloc / "not valid Modified UTF-8" crashes (#3)

## [1.2.0] - 2021-05-25

### Added
- Update OpenCC dictionary data
- Add hk2t support
- Add jp2t support
- Add t2jp support
- Add tw2t support

### Changed
- Replace ocd files with ocd2 files

## [1.1.0] - 2020-02-12

### Added

- Update OpenCC data

## [1.0.0] - 2019-11-07

### Added

- 1.0 release
