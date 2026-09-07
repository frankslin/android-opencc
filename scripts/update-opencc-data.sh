#!/usr/bin/env bash
#
# Regenerates lib-opencc-android/src/main/assets/openccdata from the OpenCC
# submodule: builds OpenCC's opencc_dict for the host, compiles every
# dictionary to .ocd2, copies the conversion configs, and writes the VERSION
# marker that ChineseConverter uses to detect a data change on the device.
#
# Run it after bumping the submodule, then commit the assets together with
# the submodule change. The build tree lives under the module's gitignored
# build/ directory and is reused on the next run.
#
# Requirements on the host: cmake >= 3.12, a C++17 compiler, python3, and
# optionally ninja. The host must be little-endian: .ocd2 files are marisa
# trie images written in the host's byte order, and every Android ABI is
# little-endian.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODULE_DIR="$REPO_ROOT/lib-opencc-android"
OPENCC_DIR="$MODULE_DIR/src/main/jni/OpenCC"
ASSETS_DIR="$MODULE_DIR/src/main/assets/openccdata"
BUILD_DIR="${OPENCC_HOST_BUILD_DIR:-$MODULE_DIR/build/opencc-host}"

die() { echo "error: $*" >&2; exit 1; }

[ -f "$OPENCC_DIR/CMakeLists.txt" ] \
  || die "the OpenCC submodule is empty; run: git submodule update --init --recursive"
command -v cmake >/dev/null || die "cmake not found"
command -v python3 >/dev/null || die "python3 not found (OpenCC's dictionary generation scripts need it)"
python3 -c 'import sys; sys.exit(0 if sys.byteorder == "little" else 1)' \
  || die "this host is big-endian; .ocd2 files must be generated on a little-endian host"

GENERATOR=()
if command -v ninja >/dev/null; then
  GENERATOR=(-G Ninja)
fi

echo "==> Configuring OpenCC host build in $BUILD_DIR"
cmake -S "$OPENCC_DIR" -B "$BUILD_DIR" "${GENERATOR[@]}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=OFF \
  -DOPENCC_ENABLE_INSTALL=OFF \
  -DENABLE_GTEST=OFF \
  -DENABLE_BENCHMARK=OFF \
  -DBUILD_OPENCC_JIEBA_PLUGIN=OFF \
  -DBUILD_PYTHON=OFF \
  -DBUILD_DOCUMENTATION=OFF \
  -DOPENCC_DICT_FORMAT=ocd2

echo "==> Building opencc_dict and compiling the dictionaries"
cmake --build "$BUILD_DIR" --target Dictionaries

shopt -s nullglob
OCD2_FILES=("$BUILD_DIR"/data/*.ocd2)
[ "${#OCD2_FILES[@]}" -gt 0 ] || die "no .ocd2 files were produced under $BUILD_DIR/data"

CONFIG_FILES=()
for f in "$OPENCC_DIR"/data/config/*.json; do
  case "$(basename "$f")" in
    *.schema.json) ;;            # the config schema is not a conversion config
    *) CONFIG_FILES+=("$f") ;;
  esac
done
[ "${#CONFIG_FILES[@]}" -gt 0 ] || die "no config files found under $OPENCC_DIR/data/config"

VERSION="$(git -C "$OPENCC_DIR" describe --tags --always)"

echo "==> Replacing the dictionaries and configs in $ASSETS_DIR"
mkdir -p "$ASSETS_DIR"
rm -f "$ASSETS_DIR"/*.ocd2 "$ASSETS_DIR"/*.json
cp "${OCD2_FILES[@]}" "${CONFIG_FILES[@]}" "$ASSETS_DIR/"
printf '%s\n' "$VERSION" > "$ASSETS_DIR/VERSION"

echo "==> Done: ${#OCD2_FILES[@]} dictionaries, ${#CONFIG_FILES[@]} configs, VERSION=$VERSION"
echo "    Review with: git status -- $ASSETS_DIR"
