#include <jni.h>

#include <string>
#include <vector>

#include "animex_core.h"

#ifndef ANIMEX_MASKED_SECRET_MY_ANIME_LIST
#define ANIMEX_MASKED_SECRET_MY_ANIME_LIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_ANILIST
#define ANIMEX_MASKED_SECRET_ANILIST ""
#endif
#ifndef ANIMEX_MASKED_SECRET_API_GATE
#define ANIMEX_MASKED_SECRET_API_GATE ""
#endif

namespace {

std::string javaStringToStdString(JNIEnv *environment, jstring javaString) {
  if (environment == nullptr || javaString == nullptr)
    return "";

  const jsize utfLength = environment->GetStringUTFLength(javaString);
  const char *utf8Characters =
      environment->GetStringUTFChars(javaString, nullptr);
  if (utf8Characters == nullptr)
    return "";

  std::string standardString(utf8Characters,
                             static_cast<std::size_t>(utfLength));
  environment->ReleaseStringUTFChars(javaString, utf8Characters);
  return standardString;
}

jstring stdStringToJavaString(JNIEnv *environment,
                              const std::string &standardString) {
  if (environment == nullptr)
    return nullptr;
  return environment->NewStringUTF(standardString.c_str());
}

jobjectArray
stdStringListToJavaArray(JNIEnv *environment,
                         const std::vector<std::string> &stringList) {
  if (environment == nullptr)
    return nullptr;
  jclass stringClass = environment->FindClass("java/lang/String");
  if (stringClass == nullptr)
    return nullptr;

  jobjectArray javaArray = environment->NewObjectArray(
      static_cast<jsize>(stringList.size()), stringClass, nullptr);
  if (javaArray == nullptr) {
    environment->DeleteLocalRef(stringClass);
    return nullptr;
  }

  for (std::size_t index = 0; index < stringList.size(); ++index) {
    jstring javaString = stdStringToJavaString(environment, stringList[index]);
    if (javaString != nullptr) {
      environment->SetObjectArrayElement(javaArray, static_cast<jsize>(index),
                                         javaString);
      environment->DeleteLocalRef(javaString);
    }
  }

  environment->DeleteLocalRef(stringClass);
  return javaArray;
}

const char *maskedSecretFor(jint secretId) {
  switch (secretId) {
  case animex::SECRET_MY_ANIME_LIST:
    return ANIMEX_MASKED_SECRET_MY_ANIME_LIST;
  case animex::SECRET_ANILIST:
    return ANIMEX_MASKED_SECRET_ANILIST;
  case animex::SECRET_API_GATE:
    return ANIMEX_MASKED_SECRET_API_GATE;
  default:
    return "";
  }
}

} // namespace

#define NATIVE_CORE_FUNCTION(returnType, functionName)                         \
  extern "C" JNIEXPORT returnType JNICALL                                      \
      Java_com_uwu_animex_core_security_NativeCore_##functionName

NATIVE_CORE_FUNCTION(jstring, secret)
(JNIEnv *environment, jobject, jint secretId) {
  std::string decoded = animex::decodeSecret(maskedSecretFor(secretId));
  jstring result = stdStringToJavaString(environment, decoded);
  animex::secureZeroString(decoded);
  return result;
}

NATIVE_CORE_FUNCTION(jstring, endpoint)
(JNIEnv *environment, jobject, jint operation, jstring argument) {
  const std::string path = animex::buildEndpointPath(
      operation, javaStringToStdString(environment, argument));
  return stdStringToJavaString(environment, path);
}

NATIVE_CORE_FUNCTION(jboolean, isVolatile)
(JNIEnv *environment, jobject, jstring path) {
  const bool pathIsVolatile =
      animex::isVolatilePath(javaStringToStdString(environment, path));
  return pathIsVolatile ? JNI_TRUE : JNI_FALSE;
}

NATIVE_CORE_FUNCTION(jobjectArray, exploreTarget)
(JNIEnv *environment, jobject, jstring kind) {
  const std::vector<std::string> target =
      animex::exploreTargetFor(javaStringToStdString(environment, kind));
  return stdStringListToJavaArray(environment, target);
}

NATIVE_CORE_FUNCTION(jobjectArray, searchPlan)(JNIEnv *environment, jobject) {
  return stdStringListToJavaArray(environment, animex::buildSearchPlan());
}

NATIVE_CORE_FUNCTION(jobjectArray, scheduleDays)(JNIEnv *environment, jobject) {
  return stdStringListToJavaArray(environment, animex::scheduleDayNames());
}
