#include <jni.h>
#include <map>
#include <mutex>
#include <string>
#include "Config.hpp"
#include "Converter.hpp"
#include "Exception.hpp"

namespace {

/**
 * RAII wrapper around GetStringUTFChars()/ReleaseStringUTFChars().
 *
 * The buffer returned by GetStringUTFChars() is owned by the JVM and is only
 * guaranteed to stay valid until ReleaseStringUTFChars() is called.  Holding
 * it in a scoped object makes the lifetime correct by construction, so the
 * release can no longer drift above the code that reads the buffer.
 *
 * Only used for the config file name and the data folder path. Those are
 * plain ASCII paths, so the Modified UTF-8 that GetStringUTFChars() produces
 * is identical to UTF-8 for them. The text being converted goes through
 * byte[] instead, see below.
 */
class ScopedUtfChars {
public:
    ScopedUtfChars(JNIEnv *env, jstring str)
            : env_(env), str_(str), chars_(env->GetStringUTFChars(str, nullptr)) {}

    ~ScopedUtfChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(str_, chars_);
        }
    }

    ScopedUtfChars(const ScopedUtfChars &) = delete;
    ScopedUtfChars &operator=(const ScopedUtfChars &) = delete;

    // Null when the JVM failed to allocate the buffer; an OutOfMemoryError is
    // then already pending and must be allowed to propagate.
    bool valid() const { return chars_ != nullptr; }

    const char *c_str() const { return chars_; }

private:
    JNIEnv *env_;
    jstring str_;
    const char *chars_;
};

/**
 * Copies a Java byte[] holding UTF-8 text into a std::string.
 *
 * Returns false if the copy failed, in which case a Java exception is already
 * pending and the caller must return to Java without touching the JNI
 * environment further.
 */
bool CopyUtf8Bytes(JNIEnv *env, jbyteArray array, std::string *out) {
    const jsize length = env->GetArrayLength(array);
    out->resize(static_cast<size_t>(length));
    if (length > 0) {
        env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte *>(&(*out)[0]));
    }
    return !env->ExceptionCheck();
}

/**
 * Wraps UTF-8 text in a new Java byte[].
 *
 * Returns null with an OutOfMemoryError pending if the array could not be
 * allocated.
 */
jbyteArray NewUtf8Bytes(JNIEnv *env, const std::string &text) {
    const jsize length = static_cast<jsize>(text.size());
    jbyteArray array = env->NewByteArray(length);
    if (array == nullptr) {
        return nullptr;
    }
    if (length > 0) {
        env->SetByteArrayRegion(array, 0, length, reinterpret_cast<const jbyte *>(text.data()));
    }
    return array;
}

/**
 * Raises java.lang.IllegalStateException with the given message.
 *
 * OpenCC reports a missing or corrupt config / dictionary file by throwing
 * (opencc::FileNotFound, opencc::InvalidFormat, ...). A C++ exception must not
 * escape a JNI entry point: the runtime has no handler for it and calls
 * std::terminate(), killing the whole process. Translating it lets the caller
 * catch the failure, for example when the dictionary copy into the data
 * folder was interrupted.
 */
void ThrowIllegalStateException(JNIEnv *env, const std::string &message) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    if (clazz == nullptr) {
        return;  // NoClassDefFoundError is pending; let it propagate instead.
    }
    env->ThrowNew(clazz, message.c_str());
    env->DeleteLocalRef(clazz);
}

/**
 * Converters cached by config path.
 *
 * Config::NewFromFile() reads and deserialises every dictionary the config
 * references (STPhrases alone is close to 1 MB), which took longer than the
 * conversion itself on every call. A Converter is immutable once built and
 * OpenCC guards the little internal state it keeps (thread-local marisa
 * agents and match caches, a mutex around lazy lexicon reconstruction), so
 * one instance per config can serve concurrent Convert() calls. The mutex
 * only protects the map; conversion runs outside it on a shared_ptr copy,
 * so clearing the cache while a conversion is in flight is safe too.
 */
std::mutex g_convertersMutex;
std::map<std::string, opencc::ConverterPtr> g_converters;

opencc::ConverterPtr GetConverter(const std::string &configPath) {
    std::lock_guard<std::mutex> lock(g_convertersMutex);
    auto it = g_converters.find(configPath);
    if (it != g_converters.end()) {
        return it->second;
    }
    opencc::Config config;
    opencc::ConverterPtr converter = config.NewFromFile(configPath);
    g_converters.emplace(configPath, converter);
    return converter;
}

} // namespace

/**
 * Drops every cached Converter. Called by the Java side after the
 * dictionary data on disk was removed or replaced, so the next conversion
 * loads the new files instead of serving the old ones from memory.
 */
extern "C"
void
Java_com_zqc_opencc_android_lib_ChineseConverter_clearConverterCache(JNIEnv * /* env */, jclass /* clazz */) {
    std::lock_guard<std::mutex> lock(g_convertersMutex);
    g_converters.clear();
}

/**
 * The text crosses the JNI boundary as UTF-8 in a byte[] rather than as a
 * jstring on purpose.  GetStringUTFChars()/NewStringUTF() speak Modified
 * UTF-8, which encodes every character outside the Basic Multilingual Plane
 * as a pair of 3-byte surrogates instead of one 4-byte sequence.  OpenCC's
 * dictionaries contain such characters (CJK Extension B and beyond, for
 * example 㓆 -> 𠗣), so with jstring a supplementary character in the input
 * never matched a dictionary entry, and a supplementary character in the
 * output was handed to NewStringUTF() as real UTF-8, which is invalid
 * Modified UTF-8: CheckJNI aborts the process, and without it the result is
 * garbage.  The Java side does the String <-> UTF-8 conversion, which handles
 * surrogate pairs correctly.
 */
extern "C"
jbyteArray
Java_com_zqc_opencc_android_lib_ChineseConverter_convert(
        JNIEnv *env, jclass /* clazz */, jbyteArray utf8Text_, jstring configFile_,
        jstring absoluteDataFolderPath_) {
    std::string text;
    if (!CopyUtf8Bytes(env, utf8Text_, &text)) {
        return nullptr;
    }
    ScopedUtfChars configFile(env, configFile_);
    ScopedUtfChars absoluteDataFolderPath(env, absoluteDataFolderPath_);
    if (!configFile.valid() || !absoluteDataFolderPath.valid()) {
        return nullptr;
    }

    const std::string configPath =
            std::string(absoluteDataFolderPath.c_str()) + "/" + std::string(configFile.c_str());

    std::string converted;
    try {
        converted = GetConverter(configPath)->Convert(text);
    } catch (const opencc::Exception &e) {
        // opencc::Exception does not derive from std::exception.
        ThrowIllegalStateException(env, "OpenCC conversion with " + configPath + " failed: " + e.what());
        return nullptr;
    } catch (const std::exception &e) {
        // marisa errors, std::bad_alloc, ...
        ThrowIllegalStateException(env, "OpenCC conversion with " + configPath + " failed: " + e.what());
        return nullptr;
    } catch (...) {
        ThrowIllegalStateException(env, "OpenCC conversion with " + configPath + " failed");
        return nullptr;
    }

    return NewUtf8Bytes(env, converted);
}
