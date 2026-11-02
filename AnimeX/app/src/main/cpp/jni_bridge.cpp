#include <jni.h>

#include <string>
#include <vector>

#include "ax_core.h"

#ifndef AX_K0
#define AX_K0 ""
#endif
#ifndef AX_K1
#define AX_K1 ""
#endif
#ifndef AX_K2
#define AX_K2 ""
#endif
#ifndef AX_K3
#define AX_K3 ""
#endif

namespace {

std::string to_std(JNIEnv* env, jstring s) {
    if (s == nullptr) return "";
    const char* c = env->GetStringUTFChars(s, nullptr);
    if (c == nullptr) return "";
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring to_j(JNIEnv* env, const std::string& s) { return env->NewStringUTF(s.c_str()); }

jobjectArray to_arr(JNIEnv* env, const std::vector<std::string>& v) {
    jclass cls = env->FindClass("java/lang/String");
    jobjectArray arr = env->NewObjectArray(static_cast<jsize>(v.size()), cls, nullptr);
    for (std::size_t i = 0; i < v.size(); ++i) {
        jstring s = to_j(env, v[i]);
        env->SetObjectArrayElement(arr, static_cast<jsize>(i), s);
        env->DeleteLocalRef(s);
    }
    env->DeleteLocalRef(cls);
    return arr;
}

}  // namespace

#define FN(ret, name) extern "C" JNIEXPORT ret JNICALL Java_com_uwu_animex_core_security_NativeCore_##name

FN(jstring, secret)(JNIEnv* env, jobject, jint id) {
    const char* masked = "";
    switch (id) {
        case ax::MAL: masked = AX_K0; break;
        case ax::ANILIST: masked = AX_K1; break;
        case ax::GATE: masked = AX_K2; break;
        case ax::BASE: masked = AX_K3; break;
        default: break;
    }
    return to_j(env, ax::secret(id, masked));
}

FN(jstring, endpoint)(JNIEnv* env, jobject, jint op, jstring arg) {
    return to_j(env, ax::endpoint(op, to_std(env, arg)));
}

FN(jboolean, isVolatile)(JNIEnv* env, jobject, jstring path) {
    return ax::is_volatile(to_std(env, path)) ? JNI_TRUE : JNI_FALSE;
}

FN(jobjectArray, exploreTarget)(JNIEnv* env, jobject, jstring kind) {
    return to_arr(env, ax::explore_target(to_std(env, kind)));
}

FN(jobjectArray, searchPlan)(JNIEnv* env, jobject) { return to_arr(env, ax::search_plan()); }

FN(jobjectArray, scheduleDays)(JNIEnv* env, jobject) { return to_arr(env, ax::schedule_days()); }

FN(jstring, normalizeBase)(JNIEnv* env, jobject, jstring raw) {
    return to_j(env, ax::normalize_base(to_std(env, raw)));
}

FN(jstring, gateDomain)(JNIEnv* env, jobject, jstring json) {
    return to_j(env, ax::gate_domain(to_std(env, json)));
}
