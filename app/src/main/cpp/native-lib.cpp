#include <jni.h>
#include <cstdint>
#include <cerrno>
#include <cstring>
#include <sstream>
#include <string>
#include <sys/types.h>
#include <sys/uio.h>

// The only target value in this prototype belongs to MemoryGG itself.
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

extern "C"
JNIEXPORT jlong JNICALL
Java_com_kentaakakashi_memorygg_MainActivity_nativeTestAddress(JNIEnv*, jobject) {
    return static_cast<jlong>(
        reinterpret_cast<uintptr_t>(const_cast<int32_t*>(&controlled_value)));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_MemoryUserService_nativeProbeRemoteValue(
        JNIEnv* env, jobject, jint target_pid, jlong target_address) {
    std::ostringstream out;
    if (target_pid <= 0 || target_address <= 0) {
        return env->NewStringUTF("INVALID target PID/address; no memory access attempted.");
    }

    const pid_t pid = static_cast<pid_t>(target_pid);
    void* remote_address = reinterpret_cast<void*>(
        static_cast<uintptr_t>(target_address));

    int32_t original = 0;
    iovec read_local{&original, sizeof(original)};
    iovec read_remote{remote_address, sizeof(original)};
    errno = 0;
    const ssize_t read_count = process_vm_readv(
        pid, &read_local, 1, &read_remote, 1, 0);
    if (read_count != static_cast<ssize_t>(sizeof(original))) {
        const int error = errno;
        out << "READ DENIED/FAILED (bytes=" << read_count
            << ", errno=" << error << ": " << std::strerror(error) << "). "
            << "No write was attempted.";
        return env->NewStringUTF(out.str().c_str());
    }

    constexpr int32_t replacement = 67890;
    iovec write_local{const_cast<int32_t*>(&replacement), sizeof(replacement)};
    iovec write_remote{remote_address, sizeof(replacement)};
    errno = 0;
    const ssize_t write_count = process_vm_writev(
        pid, &write_local, 1, &write_remote, 1, 0);
    if (write_count != static_cast<ssize_t>(sizeof(replacement))) {
        const int error = errno;
        // Attempt to restore the original value even if a partial write occurred.
        iovec restore_local{&original, sizeof(original)};
        errno = 0;
        const ssize_t restore_count = process_vm_writev(
            pid, &restore_local, 1, &write_remote, 1, 0);
        out << "READ PASS (value=" << original << "); WRITE DENIED/FAILED (bytes="
            << write_count << ", errno=" << error << ": " << std::strerror(error)
            << "); restore=" << (restore_count == static_cast<ssize_t>(sizeof(original))
                                     ? "PASS" : "FAILED") << ".";
        return env->NewStringUTF(out.str().c_str());
    }

    int32_t observed = 0;
    iovec verify_local{&observed, sizeof(observed)};
    errno = 0;
    const ssize_t verify_count = process_vm_readv(
        pid, &verify_local, 1, &read_remote, 1, 0);
    const int verify_error = errno;
    const bool verify_ok = verify_count == static_cast<ssize_t>(sizeof(observed))
                           && observed == replacement;

    iovec restore_local{&original, sizeof(original)};
    errno = 0;
    const ssize_t restore_count = process_vm_writev(
        pid, &restore_local, 1, &write_remote, 1, 0);
    const bool restore_ok = restore_count == static_cast<ssize_t>(sizeof(original));

    out << "READ PASS (original=" << original << "); WRITE PASS (temporary value="
        << replacement << "); VERIFY "
        << (verify_ok ? "PASS" : "FAIL");
    if (verify_count != static_cast<ssize_t>(sizeof(observed))) {
        out << " (bytes=" << verify_count << ", errno=" << verify_error
            << ": " << std::strerror(verify_error) << ")";
    } else if (!verify_ok) {
        out << " (observed=" << observed << ")";
    }
    out << "; RESTORE " << (restore_ok ? "PASS" : "FAIL")
        << (restore_ok ? " (original value restored)." : " (original value may remain changed; inspect immediately).");
    return env->NewStringUTF(out.str().c_str());
}
