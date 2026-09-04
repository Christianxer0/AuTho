#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/statvfs.h>

// ==================== STORAGE ====================


JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getInternalStorageInfo(JNIEnv *env, jclass clazz)
{
    struct  statvfs stat;
    if(statvfs("/data", &stat) != 0) {
        return NULL;
    }
    unsigned long blockSize = stat.f_frsize;
    unsigned long total = stat.f_blocks * blockSize;
    unsigned long free = stat.f_bfree * blockSize;
    unsigned long used = total - free;

    jlongArray result = (*env)->NewLongArray(env, 3);
    if(result == NULL) return NULL;

    jlong value[3] = {(jlong) total, (jlong) used, (jlong) free};
    (*env)->SetLongArrayRegion(env, result, 0, 3, value);
    return result;
}

// ==================== SENSORS (Placeholder) ====================


// ==================== WIFI SIGNAL ====================
JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiSignalStrength(JNIEnv *env, jclass clazz) {
    FILE * wifiFile = fopen("proc/net/wireless", "r");
    if(wifiFile == NULL) {
        return -1;
    }
    char line[256];
    int strength = -1;
    while(fgets(line, sizeof(line), wifiFile) != NULL) {
        if(strstr(line, "wlan") != NULL){
            char iface[16];
            int status, quality, signal, noise;
            if (sscanf(line, "%s %d %d %d %d", iface, &status, &quality, &signal, &noise) >= 3){
                if(quality >= 0 && quality <= 100){
                    strength = quality;
                } else {
                    strength = (int)(100.0f * (signal + 100)/ 70.0f);
                }
                if(strength < 0) strength = 0;
                if(strength > 100) strength = 100;
                break;
            }
        }
    }
    fclose(wifiFile);
    return strength;
}



JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_executeRootCommand(JNIEnv *env, jclass clazz,
                                                              jstring command) {
    const char* cmd = (*env)->GetStringUTFChars(env, command, NULL);
    if(cmd == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    char fullCmd[1024];
    int written = snprintf(fullCmd, sizeof(fullCmd), "su -c '%s' 2>&1", cmd);
    (*env)->ReleaseStringUTFChars(env, command, cmd);

    if(written < 0 || (size_t)written >= sizeof(fullCmd)){
        return (*env)->NewStringUTF(env, "Error: command too long");
    }
    FILE* pipe = popen(fullCmd, "r");
    if(pipe == NULL){
        return (*env)->NewStringUTF(env, "");
    }

    char buffer[265];
    char result[4096] = "";
    while(fgets(buffer, sizeof(buffer), pipe) != NULL) {
        strncat(result, buffer, sizeof(result) - strlen(result) - 1);
    }
    pclose(pipe);

    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuUsage(JNIEnv *env, jclass clazz) {
    // TODO: implement getCpuUsage()
}

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuCoreCount(JNIEnv *env, jclass clazz) {
    // TODO: implement getCpuCoreCount()
}

JNIEXPORT jfloat JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuTemperature(JNIEnv *env, jclass clazz) {
    // TODO: implement getCpuTemperature()
}

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getRamInfo(JNIEnv *env, jclass clazz) {
    // TODO: implement getRamInfo()
}

JNIEXPORT jintArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getBatteryInfo(JNIEnv *env, jclass clazz) {
    // TODO: implement getBatteryInfo()
}

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getExternalStorageInfo(JNIEnv *env, jclass clazz) {
    // TODO: implement getExternalStorageInfo()
}

JNIEXPORT jobjectArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_scanNetworkDevices(JNIEnv *env, jclass clazz,
                                                              jint timeout_ms) {
    // TODO: implement scanNetworkDevices()
}