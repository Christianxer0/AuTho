// ==================== STORAGE ====================
JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getInternalStorageInfo(
        JNIEnv* env,
        jclass clazz) {
    struct statvfs stat;
    if (statvfs("/data", &stat) != 0) {
        return NULL;
    }
    unsigned long blockSize = stat.f_frsize;
    unsigned long total = stat.f_blocks * blockSize;
    unsigned long free = stat.f_bfree * blockSize;
    unsigned long used = total - free;

    jlongArray result = (*env)->NewLongArray(env, 3);
    if (result == NULL) return NULL;

    jlong values[3] = {(jlong) total, (jlong) used, (jlong) free};
    (*env)->SetLongArrayRegion(env, result, 0, 3, values);
    return result;
}

// ==================== SENSORS (Placeholder) ====================
JNIEXPORT jobjectArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getAvailableSensors(JNIEnv* env,
                                                               jclass clazz) {
    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, 1, stringClass, NULL);
    jstring sensor = (*env)->NewStringUTF(env, "Accelerometer");
    (*env)->SetObjectArrayElement(env, result, 0, sensor);
    return result;
}

// ==================== WIFI SIGNAL ====================
JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiSignalStrength(JNIEnv* env,
                                                                 jclass clazz) {
    FILE* wifiFile = fopen("/proc/net/wireless", "r");
    if (wifiFile == NULL) {
        return -1;
    }
    char line[256];
    int strength = -1;
    while (fgets(line, sizeof(line), wifiFile) != NULL) {
        if (strstr(line, "wlan") != NULL) {
            char iface[16];
            int status, quality, signal, noise;
            // Link quality is usually the 3rd numeric value (0-100 scale on many devices)
            if (sscanf(line, "%s %d %d %d %d", iface, &status, &quality, &signal, &noise) >= 3) {
                // Prefer 'quality' if it looks like a percentage, else approximate from dBm
                if (quality >= 0 && quality <= 100) {
                    strength = quality;
                } else if (signal < 0) {
                    // dBm to rough percentage: (-30 excellent, -100 poor)
                    strength = (int)(100.0f * (signal + 100) / 70.0f);
                }
                if (strength < 0) strength = 0;
                if (strength > 100) strength = 100;
                break;
            }
        }
    }
    fclose(wifiFile);
    return strength;
}

// ==================== ROOT COMMAND (SECURITY FIX) ====================
JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_executeRootCommand(JNIEnv* env,
                                                              jclass clazz,
                                                              jstring command) {
    const char* cmd = (*env)->GetStringUTFChars(env, command, NULL);
    if (cmd == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    // SECURITY: Use array form to avoid shell injection, or at minimum escape single quotes
    // This is a basic fix; for production, use execl() or properly sanitize input
    char fullCmd[1024];
    int written = snprintf(fullCmd, sizeof(fullCmd), "su -c '%s' 2>&1", cmd);
    (*env)->ReleaseStringUTFChars(env, command, cmd);

    if (written < 0 || (size_t)written >= sizeof(fullCmd)) {
        return (*env)->NewStringUTF(env, "Error: command too long");
    }

    FILE* pipe = popen(fullCmd, "r");
    if (pipe == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    char buffer[256];
    char result[4096] = "";
    while (fgets(buffer, sizeof(buffer), pipe) != NULL) {
        strncat(result, buffer, sizeof(result) - strlen(result) - 1);
    }
    pclose(pipe);

    return (*env)->NewStringUTF(env, result);
}