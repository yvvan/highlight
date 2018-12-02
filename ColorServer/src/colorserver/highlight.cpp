#include "colorserver_ColorServer.h"

#include "highlight.h"

#include <array>
#include <atomic>

static std::atomic<int> currentRevision{0};

JNIEXPORT void JNICALL Java_colorserver_ColorServer_requestColors
  (JNIEnv *env, jclass clazz, jlong revision, jstring javaString, jbyteArray result) {
    if (currentRevision > revision)
        return;

    jboolean isCopy = false;
    const char *nativeString = (*env).GetStringUTFChars(javaString, &isCopy);
    jsize size = (*env).GetStringUTFLength(javaString);

    std::vector<char> data(nativeString, nativeString + size);
    std::vector<Color> colors;
    colors.resize(data.size());

    highlight(data, [revision]() {
        return currentRevision > revision;
    }, colors.begin());

    // Can we get rid of this this copy?
    std::vector<jbyte> colorData;
    colorData.reserve(colors.size() * 3);
    for (const auto &color : colors) {
        colorData.push_back(static_cast<jbyte>(color.r));
        colorData.push_back(static_cast<jbyte>(color.g));
        colorData.push_back(static_cast<jbyte>(color.b));
    }

    (*env).SetByteArrayRegion(result, 0, colorData.size(), colorData.data());

    (*env).ReleaseStringUTFChars(javaString, nativeString);
}

JNIEXPORT void JNICALL Java_colorserver_ColorServer_updateRevision
(JNIEnv *, jclass, jlong revision) {
    currentRevision = revision;
}
