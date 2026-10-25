#include <jni.h>
#include <cstdint>
#include <sstream>
#include <string>

// Deliberately limited to a value owned by this app's own process.
static volatile int32_t controlled_value = 12345;

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_MainActivity_nativeSelfTest(JNIEnv* env, jobject) {
    const int32_t before = controlled_value;
    controlled_value = 54321;
    const int32_t after = controlled_value;
    const bool pass = (before == 12345 && after == 54321);
    std::ostringstream out;
    out << (pass ? "PASS" : "FAIL")
        << " (own-process int32 read/write; before=" << before
        << ", after=" << after << ")";
    controlled_value = 12345;
    return env->NewStringUTF(out.str().c_str());
}
