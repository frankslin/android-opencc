# Native build for lib-opencc-android.
#
# OpenCC is consumed as a CMake subproject straight from the pinned submodule,
# so upgrading OpenCC is a submodule bump and nothing else: the source list,
# the bundled dependency versions (marisa, darts-clone, rapidjson) and the
# compile definitions all come from OpenCC's own CMakeLists.txt.

cmake_minimum_required(VERSION 3.18.1)
project(ChineseConverter CXX)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/OpenCC/CMakeLists.txt")
  message(FATAL_ERROR
    "The OpenCC submodule is empty. Run: git submodule update --init --recursive")
endif()

# libopencc is linked into a shared library, and OpenCC uses exceptions plus
# dynamic_cast (PrefixMatch.cpp). The NDK's CMake toolchain enables exceptions
# and RTTI by default -- unlike ndk-build, whose -fno-rtti default is why the
# previous Application.mk could not have built OpenCC 1.4.x -- but state the
# requirements here so they survive a toolchain default change.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
string(APPEND CMAKE_CXX_FLAGS " -fexceptions -frtti")

# EXCLUDE_FROM_ALL keeps OpenCC's own ALL targets out of this build: the
# command line tools (opencc, opencc_dict, opencc_phrase_extract) and the
# Dictionaries / ConvertedConfigs targets that run opencc_dict to regenerate
# .ocd2 files. Those tools would be cross-compiled for the device and could
# not be executed by the build host. Dictionaries ship prebuilt under
# src/main/assets/openccdata instead. libopencc is still built because
# ChineseConverter links against it.
add_subdirectory(OpenCC EXCLUDE_FROM_ALL)

add_library(ChineseConverter SHARED chineseconverter.cpp)

# OpenCC::OpenCC carries OpenCC's include directory as a usage requirement,
# so no manual LOCAL_C_INCLUDES equivalent is needed. The old Android.mk also
# linked -llog and -landroid; chineseconverter.cpp calls into neither.
target_link_libraries(ChineseConverter PRIVATE OpenCC::OpenCC)

# Android 15+ devices may use 16 KB memory pages; shared libraries have to be
# aligned accordingly. Default since NDK r27, set explicitly for older NDKs.
target_link_options(ChineseConverter PRIVATE "LINKER:-z,max-page-size=16384")
