#include <jni.h>

#include <string>
#include <vector>

#include "animex_core.h"

// Nilai rahasia (sudah di-XOR) dikirim dari Gradle lewat CMake.
// Jika tidak diisi, nilainya string kosong supaya build tetap berhasil.
#ifndef ANIMEX_MASKED_SECRET_MY_ANIME_LIST
#define ANIMEX_MASKED_SECRET_MY_ANIME_LIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_ANILIST
#define ANIMEX_MASKED_SECRET_ANILIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_API_GATE
#define ANIMEX_MASKED_SECRET_API_GATE ""
#endif
#ifndef ANIMEX_MASKED_SECRET_API_BASE
#define ANIMEX_MASKED_SECRET_API_BASE ""
#endif

namespace {

// Java String -> std::string. Mengembalikan string kosong jika null.
std::string javaStringToStdString(JNIEnv* environment, jstring javaString) {
    if (javaString == nullptr) return "";

    const char* utf8Characters = environment->GetStringUTFChars(javaString, nullptr);
    if (utf8Characters == nullptr) return "";

    std::string standardString(utf8Characters);
    environment->ReleaseStringUTFChars(javaString, utf8Characters);
    return standardString;
}

// std::string -> Java String.
jstring stdStringToJavaString(JNIEnv* environment, const std::string& standardString) {
    return environment->NewStringUTF(standardString.c_str());
}

// std::vector<std::string> -> Java Array<String>.
jobjectArray stdStringListToJavaArray(JNIEnv* environment,
                                      const std::vector<std::string>& stringList) {
    jclass stringClass = environment->FindClass("java/lang/String");
    jobjectArray javaArray = environment->NewObjectArray(
        static_cast<jsize>(stringList.size()), stringClass, nullptr);

    for (std::size_t index = 0; index < stringList.size(); ++index) {
        jstring javaString = stdStringToJavaString(environment, stringList[index]);
        environment->SetObjectArrayElement(javaArray, static_cast<jsize>(index), javaString);
        environment->DeleteLocalRef(javaString);
    }

    environment->DeleteLocalRef(stringClass);
    return javaArray;
}

}  // namespace

// Nama fungsi JNI harus sama dengan package dan class Kotlin:
// com.uwu.animex.core.security.NativeCore
#define NATIVE_CORE_FUNCTION(returnType, functionName) \
    extern "C" JNIEXPORT returnType JNICALL            \
        Java_com_uwu_animex_core_security_NativeCore_##functionName

NATIVE_CORE_FUNCTION(jstring, secret)(JNIEnv* environment, jobject, jint secretId) {
    const char* maskedHexText = "";
    switch (secretId) {
        case animex::SECRET_MY_ANIME_LIST: maskedHexText = ANIMEX_MASKED_SECRET_MY_ANIME_LIST; break;
        case animex::SECRET_ANILIST:       maskedHexText = ANIMEX_MASKED_SECRET_ANILIST;       break;
        case animex::SECRET_API_GATE:      maskedHexText = ANIMEX_MASKED_SECRET_API_GATE;      break;
        case animex::SECRET_API_BASE:      maskedHexText = ANIMEX_MASKED_SECRET_API_BASE;      break;
        default: break;
    }
    return stdStringToJavaString(environment, animex::decodeSecret(secretId, maskedHexText));
}

NATIVE_CORE_FUNCTION(jstring, endpoint)(JNIEnv* environment, jobject, jint operation,
                                        jstring argument) {
    return stdStringToJavaString(
        environment,
        animex::buildEndpointPath(operation, javaStringToStdString(environment, argument)));
}

NATIVE_CORE_FUNCTION(jboolean, isVolatile)(JNIEnv* environment, jobject, jstring path) {
    return animex::isVolatilePath(javaStringToStdString(environment, path)) ? JNI_TRUE : JNI_FALSE;
}

NATIVE_CORE_FUNCTION(jobjectArray, exploreTarget)(JNIEnv* environment, jobject, jstring kind) {
    return stdStringListToJavaArray(
        environment, animex::exploreTargetFor(javaStringToStdString(environment, kind)));
}

NATIVE_CORE_FUNCTION(jobjectArray, searchPlan)(JNIEnv* environment, jobject) {
    return stdStringListToJavaArray(environment, animex::buildSearchPlan());
}

NATIVE_CORE_FUNCTION(jobjectArray, scheduleDays)(JNIEnv* environment, jobject) {
    return stdStringListToJavaArray(environment, animex::scheduleDayNames());
}

NATIVE_CORE_FUNCTION(jstring, normalizeBase)(JNIEnv* environment, jobject, jstring rawUrl) {
    return stdStringToJavaString(
        environment, animex::normalizeBaseUrl(javaStringToStdString(environment, rawUrl)));
}

NATIVE_CORE_FUNCTION(jstring, gateDomain)(JNIEnv* environment, jobject, jstring jsonText) {
    return stdStringToJavaString(
        environment, animex::extractGateDomain(javaStringToStdString(environment, jsonText)));
}
