# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `ConversionType.S2HKP` and `ConversionType.HK2SP`: Simplified Chinese to
  Hong Kong Traditional Chinese and back with Hong Kong / Mainland regional
  vocabulary (鼠标 <-> 滑鼠, 文件夹 <-> 資料夾, ...), backed by the `s2hkp.json`
  and `hk2sp.json` configs that ship with OpenCC 1.4.2

### Changed
- Converters are cached per conversion type in native memory, so only the
  first conversion with a type pays for loading its dictionaries (about
  150 ms for `s2twp` on a desktop JVM, more on a phone); later calls take
  well under a millisecond. `ChineseConverter.clearConverterCache()` releases
  the memory, and the cache is dropped automatically when the dictionary
  data is re-installed or cleared
- **minSdk is now 21** (was 14). NDK r26+ only builds for API 21 and up;
  Android 5.0 and newer devices are unaffected
- Toolchain: AGP 8.11.1, Gradle 8.14.3, JDK 17, compileSdk 35, NDK r27c
  (27.2.12479018), CMake 3.22.1, Java 11 bytecode. Both modules declare a
  `namespace`, the JitPack repository and Jetifier are gone from the build,
  and the AndroidX test libraries / appcompat move to their current releases
- Update the OpenCC submodule to upstream ver.1.4.2 (#1)
- Regenerate the bundled dictionaries and configs from the ver.1.4.2
  submodule with the new `scripts/update-opencc-data.sh`. The previous data
  came from a 2021 build, so this brings in every upstream dictionary fix
  since then (for example `tw2t` / `tw2s` no longer turn 什么 into 什幺) and the
  1.4.x config features (normalization via `CJK_Compatibility_Ideographs`,
  `TSCharactersExt`, regional phrase generation). 22 dictionaries and 16
  configs, 1.4 MB
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
