// Compile the same bounded in-process scan engine into the target-host app.
#include "../../../../app/src/main/cpp/native-lib.cpp"

extern "C" JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeSelfTest(JNIEnv* e, jobject o) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeSelfTest(e, o);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeScanFirst(JNIEnv* e, jobject o, jstring v, jint t) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeScanFirst(e, o, v, t);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeScanNext(JNIEnv* e, jobject o, jstring v, jint t, jint c) {
    return Java_com_kentaakashi_memorygg_MainActivity_nativeScanNext(e, o, v, t, c);
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeWriteValue(JNIEnv* e, jobject o, jlong a, jstring v, jint t) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeWriteValue(e, o, a, v, t);
}
extern "C" JNIEXPORT void JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeClearScan(JNIEnv* e, jobject o) {
    Java_com_kentaakakashi_memorygg_MainActivity_nativeClearScan(e, o);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeGetTestValue(JNIEnv* e, jobject o) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeGetTestValue(e, o);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostService_nativeSetTestValue(JNIEnv* e, jobject o, jint v) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeSetTestValue(e, o, v);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostActivity_nativeGetTestValue(JNIEnv* e, jobject o) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeGetTestValue(e, o);
}
extern "C" JNIEXPORT jint JNICALL
Java_com_kentaakakashi_memorygg_agenthost_AgentHostActivity_nativeSetTestValue(JNIEnv* e, jobject o, jint v) {
    return Java_com_kentaakakashi_memorygg_MainActivity_nativeSetTestValue(e, o, v);
}
