#include <unistd.h>
#include <sys/sysinfo.h>
#include <sys/statvfs.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <ifaddrs.h>
#include <android/log.h>
#include <errno.h>
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <pthread.h>
#include <time.h>

#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <math.h>

//NDK multimedia APK
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>
#include <android/sensor.h>
#include <android/looper.h>

// Camera2 NDK requires API 24+, and the header only ships with NDK r14+.
// __has_include keeps this compiling on older NDKs / lower API levels.
#if defined(__has_include)
#  if __has_include(<android/camera/ndk_camera.h>)
#    include <android/camera/ndk_camera.h>
#    define HAVE_NDK_CAMERA 1
#  endif
#else
#  if __ANDROID_API__ >= 24
#    include <android/camera/ndk_camera.h>
#    define HAVE_NDK_CAMERA 1
#  endif
#endif

#include <linux/fb.h>

#define LOG_TAG "AuThoNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define PI 3.14159265358979323846



//Small helper
static long long nowMs() {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int clampInt(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

// write a string to a sysfs node, with optional root fallback
static int writeSysfs(const char* path, const char* value) {
    int fd = open(path, O_WRONLY);
    if (fd < 0) return -1;
    ssize_t w = write(fd, value, strlen(value));
    close(fd);
    return (w < 0) ? -1 : 0;
}

static int writeSysfsOrRoot(const char* path, const char* value) {
    if (writeSysfs(path, value) == 0) return 0;
    char cmd[512];
    snprintf(cmd, sizeof(cmd), "su -c 'echo %s > %s' >/dev/null 2>&1", value, path);
    return (system(cmd) == 0) ? 0 : -1;
}




// ==================================================================
// 0. REALTIME MONITORING  (native thread -> Java callback push model)
// ==================================================================
//
// Architecture:
//   Java calls startNativeMonitor(callback, intervalMs) once.
//   A pthread samples CPU / temp / RAM / network-speed every intervalMs
//   and pushes results into Java via CallVoidMethod on a global callback
//   object. Callback fires on the native-attached thread -> always post
//   to the main looper before touching views.
//
// Signature pushed to Java:
//   onUpdate(int cpuUsage, float cpuTemp,
//            long ramTotal, long ramUsed, long ramFree,
//            long rxBytesPerSec, long txBytesPerSec)
// ==================================================================

static JavaVM*          g_vm              = NULL;
static jobject          g_monitorCallback = NULL;      // global ref
static jmethodID        g_midOnUpdate     = NULL;
static pthread_t        g_monitorThread;
static volatile int     g_monitorRunning  = 0;
static int              g_intervalMs      = 1000;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

// ---- metric readers (pure helpers, no JNI) ----

static int readCpuUsage(long* prevTotal, long* prevIdle) {
    FILE* f = fopen("/proc/stat", "r");
    if (!f) return -1;
    char line[256], cpu[10];
    long user, nice, system, idle, iowait, irq, softirq, steal;
    if (!fgets(line, sizeof(line), f)) { fclose(f); return -1; }
    fclose(f);

    sscanf(line, "%s %ld %ld %ld %ld %ld %ld %ld %ld",
           cpu, &user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal);

    long total     = user + nice + system + idle + iowait + irq + softirq + steal;
    long idleTotal = idle + iowait;

    long diffTotal = total - *prevTotal;
    long diffIdle  = idleTotal - *prevIdle;
    *prevTotal = total;
    *prevIdle  = idleTotal;

    if (diffTotal <= 0) return 0;
    int usage = (int)(100.0 * (diffTotal - diffIdle) / diffTotal);
    if (usage < 0) usage = 0;
    if (usage > 100) usage = 100;
    return usage;
}

static float readCpuTemperature() {
    FILE* f = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if (!f) return -1.0f;
    int temp;
    if (fscanf(f, "%d", &temp) != 1) { fclose(f); return -1.0f; }
    fclose(f);
    return temp / 1000.0f;
}

static void readNetworkCounters(long* rx, long* tx) {
    *rx = 0; *tx = 0;
    FILE* f = fopen("/proc/net/dev", "r");
    if (!f) return;
    char line[256], iface[32];
    long r, t;
    fgets(line, sizeof(line), f);  // skip 2 header lines
    fgets(line, sizeof(line), f);
    while (fgets(line, sizeof(line), f)) {
        if (sscanf(line, "%s %ld %*d %*d %*d %*d %*d %*d %ld", iface, &r, &t) == 3) {
            if (strstr(iface, "wlan") || strstr(iface, "eth") || strstr(iface, "rmnet")) {
                *rx += r;
                *tx += t;
            }
        }
    }
    fclose(f);
}

// ---- the monitor thread ----

static void* monitorLoop(void* arg) {
    JNIEnv* env;
    JavaVMAttachArgs attachArgs = { JNI_VERSION_1_6, "AuThoMonitor", NULL };
    if ((*g_vm)->AttachCurrentThread(g_vm, &env, &attachArgs) != JNI_OK) {
        LOGE("Monitor thread failed to attach JVM");
        return NULL;
    }

    // CPU needs two samples to compute a delta; the per-thread state
    // replaces the unsafe static-globals approach used in the old code.
    long prevTotal = 0, prevIdle = 0;
    long prevRx = 0, prevTx = 0;
    readCpuUsage(&prevTotal, &prevIdle);       // prime
    readNetworkCounters(&prevRx, &prevTx);     // prime

    LOGD("Monitor thread started, interval=%d ms", g_intervalMs);

    while (g_monitorRunning) {
        struct timespec ts = { g_intervalMs / 1000,
                               (long)(g_intervalMs % 1000) * 1000000L };
        nanosleep(&ts, NULL);
        if (!g_monitorRunning) break;

        int   cpu  = readCpuUsage(&prevTotal, &prevIdle);
        float temp = readCpuTemperature();

        struct sysinfo info;
        long totalRam = 0, usedRam = 0, freeRam = 0;
        if (sysinfo(&info) == 0) {
            totalRam = (long)info.totalram * info.mem_unit;
            freeRam  = (long)info.freeram  * info.mem_unit;
            usedRam  = totalRam - freeRam;
        }

        // realtime network throughput = delta since last tick
        long rx, tx;
        readNetworkCounters(&rx, &tx);
        long rxBps = (rx - prevRx) * 1000 / g_intervalMs;
        long txBps = (tx - prevTx) * 1000 / g_intervalMs;
        if (rxBps < 0) rxBps = 0;
        if (txBps < 0) txBps = 0;
        prevRx = rx; prevTx = tx;

        (*env)->CallVoidMethod(env, g_monitorCallback, g_midOnUpdate,
                               (jint)cpu, (jfloat)temp,
                               (jlong)totalRam, (jlong)usedRam, (jlong)freeRam,
                               (jlong)rxBps, (jlong)txBps);
        if ((*env)->ExceptionCheck(env)) {
            (*env)->ExceptionClear(env);
            LOGE("Exception in monitor callback");
        }
    }

    (*g_vm)->DetachCurrentThread(g_vm);
    LOGD("Monitor thread stopped");
    return NULL;
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_startNativeMonitor(
        JNIEnv* env, jclass clazz, jobject callback, jint intervalMs) {
    if (g_monitorRunning) return JNI_TRUE;

    if (g_monitorCallback == NULL) {
        jclass cbClass = (*env)->GetObjectClass(env, callback);
        g_midOnUpdate = (*env)->GetMethodID(env, cbClass, "onUpdate",
                                            "(IFJJJJJ)V");
        (*env)->DeleteLocalRef(env, cbClass);
        if (g_midOnUpdate == NULL) {
            LOGE("callback method onUpdate(IFJJJJJ)V not found");
            return JNI_FALSE;
        }
        g_monitorCallback = (*env)->NewGlobalRef(env, callback);
        if (g_monitorCallback == NULL) return JNI_FALSE;
    }

    g_intervalMs = intervalMs > 0 ? intervalMs : 1000;
    g_monitorRunning = 1;

    if (pthread_create(&g_monitorThread, NULL, monitorLoop, NULL) != 0) {
        g_monitorRunning = 0;
        LOGE("pthread_create failed: %s", strerror(errno));
        return JNI_FALSE;
    }
    LOGD("Native monitor started");
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_yourteam_autho_utils_NativeHelper_stopNativeMonitor(
        JNIEnv* env, jclass clazz) {
    g_monitorRunning = 0;
    pthread_join(g_monitorThread, NULL);
    if (g_monitorCallback != NULL) {
        (*env)->DeleteGlobalRef(env, g_monitorCallback);
        g_monitorCallback = NULL;
    }
    g_midOnUpdate = NULL;
    LOGD("Native monitor stopped");
}

// ==================================================================
// 1. CPU INFORMATION  (kept for one-shot queries)
// ==================================================================

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuUsage(JNIEnv *env, jclass clazz) {
    FILE* statFile = fopen("/proc/stat", "r");
    if(!statFile) {
        LOGE("Failed to open /proc/stat");
        return -1;
    }
    char line[256];
    char cpu[10];
    long user, nice, system, idle, iowait, irq, softirq, steal;
    if(!fgets(line, sizeof(line), statFile)) {
        fclose(statFile);
        return -1;
    }
    fclose(statFile);

    sscanf(line, "%s %ld %ld %ld %ld %ld %ld %ld %ld",
           cpu, &user, &nice, &system, &idle, &iowait, &irq, &softirq, &steal);

    long total = user + nice + system + idle + iowait + irq + softirq + steal;
    long idleTotal = idle + iowait;

    static long prevTotal = 0;
    static long prevIdle = 0;

    long diffTotal = total - prevTotal;
    long diffIdle = idleTotal - prevIdle;

    prevTotal = total;
    prevIdle = idleTotal;

    if(diffTotal == 0) return 0;
    int usage = (int)(100.0f * (diffTotal - diffIdle) / diffTotal);
    if(usage < 0) usage = 0;
    if(usage > 100) usage = 100;

    LOGD("CPU usage: %d%%", usage);
    return usage;
}

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuCoreCount(JNIEnv *env, jclass clazz) {
    int cores = (int) sysconf(_SC_NPROCESSORS_ONLN);
    LOGD("CPU CORE: %d", cores);
    return cores;
}

JNIEXPORT jfloat JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getCpuTemperature(JNIEnv *env, jclass clazz) {
    FILE* tempFile = fopen("/sys/class/thermal/thermal_zone0/temp", "r");
    if(!tempFile){
        LOGE("Failed to open thermal zone");
        return -1.0f;
    }
    int temp;
    if(fscanf(tempFile, "%d", &temp) != 1) {
        fclose(tempFile);
        return -1.0f;
    }
    fclose(tempFile);
    float celsius = temp / 1000.0f;
    LOGD("CPU temp: %.1f C", celsius);
    return celsius;
}

// ==================================================================
// 2. RAM INFORMATION
// ==================================================================

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getRamInfo(JNIEnv *env, jclass clazz) {
    struct sysinfo info;
    if(sysinfo(&info) != 0) {
        LOGE("sysinfo Failed");
        return NULL;
    }

    long totalRam = info.totalram * info.mem_unit;
    long freeRam = info.freeram * info.mem_unit;
    long usedRam = totalRam - freeRam;

    jlongArray result = (*env)->NewLongArray(env, 3);
    if(!result) return NULL;

    jlong values[3] = {totalRam, usedRam, freeRam};
    (*env)->SetLongArrayRegion(env, result, 0, 3, values);

    LOGD("RAM: total: %ld used= %ld free = %ld", totalRam, usedRam, freeRam);
    return result;
}

// ==================================================================
// 3. BATTERY INFORMATION
// ==================================================================

JNIEXPORT jintArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getBatteryInfo(JNIEnv *env, jclass clazz) {
    jintArray result = (*env)->NewIntArray(env, 6);
    if(!result) return NULL;
    jint values[6] = {75, 2, 1, 0, 37, 4200};
    (*env)->SetIntArrayRegion(env, result, 0, 6, values);
    return result;
}

// ==================================================================
// 4. STORAGE INFORMATION
// ==================================================================

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getInternalStorageInfo(JNIEnv *env, jclass clazz) {
    struct statvfs stat;
    if(statvfs("/data", &stat) != 0) {
        LOGE("statvfs failed");
        return NULL;
    }

    unsigned long total = stat.f_blocks * stat.f_frsize;
    unsigned long free  = stat.f_bfree  * stat.f_frsize;
    unsigned long used  = total - free;   // FIX: was total / free

    jlongArray result = (*env)->NewLongArray(env, 3);
    if(!result) return NULL;
    jlong values[3] = {(jlong)total, (jlong)used, (jlong)free};
    (*env)->SetLongArrayRegion(env, result, 0, 3, values);

    LOGD("Internal storage: total= %lu, used= %lu, free=%lu", total, used, free);
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getExternalStorageInfo(JNIEnv *env, jclass clazz) {
    const char* paths[] = {"/storage/emulated/0", "/sdcard", "/mnt/sdcard", NULL};
    for(int i = 0; paths[i] != NULL; i++) {
        struct statvfs stat;
        if(statvfs(paths[i], &stat) == 0) {
            unsigned long total = stat.f_blocks * stat.f_frsize;
            unsigned long free  = stat.f_bfree  * stat.f_frsize;
            unsigned long used  = total - free;
            jlongArray result = (*env)->NewLongArray(env, 3);
            if(!result) return NULL;
            jlong values[3] = {(jlong)total, (jlong)used, (jlong)free};
            (*env)->SetLongArrayRegion(env, result, 0, 3, values);
            LOGD("External storage at %s", paths[i]);
            return result;   // FIX: was falling through and leaking the array
        }
    }
    LOGE("External storage not found");
    return NULL;
}

// ==================================================================
// 5. NETWORK
// ==================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_scanNetworkDevices(JNIEnv *env, jclass clazz,
                                                              jint timeout_ms) {
    LOGD("Scanning network devices (timeout: %d ms)", timeout_ms);

    char ipPrefix[16] = "192.168.1.";

    struct ifaddrs *ifaddr, *ifa;
    if(getifaddrs(&ifaddr) == 0) {
        for(ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
            if(ifa->ifa_addr == NULL) continue;
            // FIX: added parentheses so the interface check applies to both
            if(ifa->ifa_addr->sa_family == AF_INET &&
               (strcmp(ifa->ifa_name, "wlan0") == 0 ||
                strcmp(ifa->ifa_name, "eth0")  == 0)) {
                struct sockaddr_in* addr = (struct sockaddr_in*)ifa->ifa_addr;
                char ip[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &addr->sin_addr, ip, sizeof(ip));

                char* lastDot = strrchr(ip, '.');
                if(lastDot) {
                    int len = lastDot - ip + 1;
                    strncpy(ipPrefix, ip, len);
                    ipPrefix[len] = '\0';
                    break;
                }
            }
        }
        freeifaddrs(ifaddr);
    }

    int foundCount = 0;
    char* devices[254];

    for(int i = 1; i <= 254 && foundCount < 254; i++) {
        char ip[50];
        snprintf(ip, sizeof(ip), "%s%d", ipPrefix, i);

        char cmd[100];
        snprintf(cmd, sizeof(cmd), "ping -c 1 -W %d %s > /dev/null 2>&1", timeout_ms/1000, ip);
        int result = system(cmd);
        if (result == 0) {
            char entry[128];
            snprintf(entry, sizeof(entry), "%s|00:00:00:00:00:00|Device-%d|Unknown", ip, i);
            devices[foundCount] = (char*)malloc(strlen(entry)+1);
            strcpy(devices[foundCount], entry);
            foundCount++;
            LOGD("Found device: %s", ip);
        }
    }

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray resultArray = (*env)->NewObjectArray(env, foundCount, stringClass, NULL);
    for (int i = 0; i < foundCount; i++) {
        jstring str = (*env)->NewStringUTF(env, devices[i]);
        (*env)->SetObjectArrayElement(env, resultArray, i, str);
        (*env)->DeleteLocalRef(env, str);   // FIX: release local refs
        free(devices[i]);
    }

    LOGD("Found %d devices", foundCount);
    return resultArray;
}

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiSignalStrength(JNIEnv *env, jclass clazz) {
    FILE* wifiFile = fopen("/proc/net/wireless", "r");
    if(!wifiFile) {
        LOGE("Failed to open /proc/net/wireless");
        return -1;
    }

    char line[256];
    int strength = -1;
    while(fgets(line, sizeof(line), wifiFile)) {
        if(strstr(line, "wlan") != NULL) {
            char iface[10];
            int status, quality, signal, noise;
            sscanf(line, "%s %d %d %d %d", iface, &status, &quality, &signal, &noise);
            strength = (int)(100.0f * (signal + 100) / 100);
            if(strength < 0) strength = 0;
            if(strength > 100) strength = 100;
            break;
        }
    }
    fclose(wifiFile);
    LOGD("WiFi signal strength %d%%", strength);
    return strength;   // FIX: was missing a return -> undefined behavior
}

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiSSID(JNIEnv *env, jclass clazz) {
    return (*env)->NewStringUTF(env, "WiFi_Network");
}

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiIPAddress(JNIEnv *env, jclass clazz) {
    struct ifaddrs *ifaddr, *ifa;
    if(getifaddrs(&ifaddr) == -1) {
        LOGE("getifaddrs failed");
        return (*env)->NewStringUTF(env, "0.0.0.0");
    }

    char ip[INET_ADDRSTRLEN] = "0.0.0.0";
    for(ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
        if(ifa->ifa_addr == NULL) continue;
        if(ifa->ifa_addr->sa_family == AF_INET) {   // FIX: was ifa->ifa_addr == AF_INET
            struct sockaddr_in* addr = (struct sockaddr_in*)ifa->ifa_addr;
            if(strcmp(ifa->ifa_name, "wlan0") == 0 || strcmp(ifa->ifa_name, "eth0") == 0) {
                inet_ntop(AF_INET, &addr->sin_addr, ip, sizeof(ip));
                break;
            }
        }
    }
    freeifaddrs(ifaddr);
    LOGD("IP address: %s", ip);
    return (*env)->NewStringUTF(env, ip);
}

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getWifiGateway(JNIEnv *env, jclass clazz) {
    FILE* routeFile = fopen("/proc/net/route", "r");
    if(!routeFile) {
        LOGE("Failed to open /proc/net/route");
        return (*env)->NewStringUTF(env, "192.168.1.1");
    }
    char line[256];
    char iface[16];
    unsigned int dest, gateway;
    char gatewayIP[INET_ADDRSTRLEN] = "0.0.0.0";
    while(fgets(line, sizeof(line), routeFile)) {
        if(sscanf(line, "%s %x %x", iface, &dest, &gateway) == 3) {
            // FIX: was strcmp(iface,"wlan0") || strcmp(iface,"eth0")==0 (always true)
            if(dest == 0 && (strcmp(iface, "wlan0") == 0 || strcmp(iface, "eth0") == 0)) {
                struct in_addr addr;
                addr.s_addr = (in_addr_t)gateway;   // /proc/net/route stores LE hex
                char* gw = inet_ntoa(addr);
                // reverse byte order: route file is little-endian
                snprintf(gatewayIP, sizeof(gatewayIP), "%d.%d.%d.%d",
                         gateway & 0xFF, (gateway >> 8) & 0xFF,
                         (gateway >> 16) & 0xFF, (gateway >> 24) & 0xFF);
                (void)gw;
                break;
            }
        }
    }
    fclose(routeFile);
    LOGD("Gateway: %s", gatewayIP);
    return (*env)->NewStringUTF(env, gatewayIP);
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_pingDevice(JNIEnv *env, jclass clazz, jstring ip_address,
                                                      jint timeout_ms) {
    const char* ip = (*env)->GetStringUTFChars(env, ip_address, NULL);
    char cmd[100];
    snprintf(cmd, sizeof(cmd), "ping -c 1 -W %d %s > /dev/null 2>&1", timeout_ms/1000, ip);
    int result = system(cmd);
    (*env)->ReleaseStringUTFChars(env, ip_address, ip);
    LOGD("Ping: %s", result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

// ==================================================================
// 6. ROOT OPERATIONS
// ==================================================================

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_isDeviceRooted(JNIEnv *env, jclass clazz) {
    const char* suPath[] = {
            "/system/bin/su", "/system/xbin/su", "/system/sbin/su", "/sbin/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su", NULL
    };
    for(int i = 0; suPath[i] != NULL; i++) {
        if(access(suPath[i], F_OK) == 0) {
            LOGD("Root Detected: %s", suPath[i]);
            return JNI_TRUE;
        }
    }

    const char* rootPackages[] = {
            "com.noshufou.android.su", "com.thirdparty.superuser",
            "eu.chainfire.supersu", "com.koushikdutta.superuser", NULL
    };
    for(int i = 0; rootPackages[i] != NULL; i++) {
        char cmd[100];
        snprintf(cmd, sizeof(cmd), "pm list packages | grep %s > /dev/null 2>&1", rootPackages[i]);
        if(system(cmd) == 0) {
            LOGD("Root package detected: %s", rootPackages[i]);
            return JNI_TRUE;
        }
    }
    LOGD("Device not rooted");
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_executeRootCommand(JNIEnv *env, jclass clazz,
                                                              jstring command) {
    const char* cmd = (*env)->GetStringUTFChars(env, command, NULL);
    char fullCmd[512];
    snprintf(fullCmd, sizeof(fullCmd), "su -c '%s' 2>&1", cmd);
    FILE* pipe = popen(fullCmd, "r");
    if (!pipe) {
        LOGE("popen failed");
        (*env)->ReleaseStringUTFChars(env, command, cmd);
        return (*env)->NewStringUTF(env, "");
    }
    char buffer[256];
    char result[4096] = "";
    while (fgets(buffer, sizeof(buffer), pipe)) {
        strncat(result, buffer, sizeof(result)-strlen(result)-1);
    }
    pclose(pipe);
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    return (*env)->NewStringUTF(env, result);
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_uninstallPackage(JNIEnv *env, jclass clazz,
                                                            jstring package_name,
                                                            jboolean is_system_app) {
    const char* pkg = (*env)->GetStringUTFChars(env, package_name, NULL);
    char cmd[256];
    if (is_system_app) {
        snprintf(cmd, sizeof(cmd), "su -c 'pm uninstall -k --user 0 %s'", pkg);
    } else {
        snprintf(cmd, sizeof(cmd), "su -c 'pm uninstall %s'", pkg);
    }
    int result = system(cmd);
    (*env)->ReleaseStringUTFChars(env, package_name, pkg);
    LOGD("Uninstall %s: %s", pkg, result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_restorePackage(JNIEnv *env, jclass clazz,
                                                          jstring package_name) {
    const char* pkg = (*env)->GetStringUTFChars(env, package_name, NULL);
    char cmd[256];
    snprintf(cmd, sizeof(cmd), "su -c 'cmd package install-existing %s'", pkg);
    int result = system(cmd);
    (*env)->ReleaseStringUTFChars(env, package_name, pkg);
    LOGD("Restore %s: %s", pkg, result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_yourteam_autho_utils_NativeHelper_killBackgroundProcesses(JNIEnv *env, jclass clazz) {
    system("su -c 'am kill-all'");
    LOGD("Killed background processes");
    return 1;
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_clearSystemCache(JNIEnv *env, jclass clazz) {
    int result = system("su -c 'sync && echo 3 > /proc/sys/vm/drop_caches'");
    LOGD("Clear system cache: %s", result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

// ==================================================================
// 7. PERFORMANCE & PROCESS LIST
// ==================================================================

JNIEXPORT jobjectArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getProcessList(JNIEnv *env, jclass clazz) {
    FILE* psFile = popen("ps -A -o pid,comm,%cpu,rss 2>/dev/null", "r");
    if (!psFile) {
        LOGE("ps failed");
        return NULL;
    }

    char line[256];
    char processInfo[256];
    char* processes[200];
    int count = 0;

    fgets(line, sizeof(line), psFile);  // skip header

    while (fgets(line, sizeof(line), psFile) && count < 200) {
        int pid;
        char name[64];
        float cpu;
        long mem;
        if (sscanf(line, "%d %s %f %ld", &pid, name, &cpu, &mem) == 4) {
            snprintf(processInfo, sizeof(processInfo), "%d|%s|%.1f|%ld", pid, name, cpu, mem);
            processes[count] = (char*)malloc(strlen(processInfo)+1);
            strcpy(processes[count], processInfo);
            count++;
        }
    }
    pclose(psFile);

    jclass stringClass = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, count, stringClass, NULL);
    for (int i = 0; i < count; i++) {
        jstring str = (*env)->NewStringUTF(env, processes[i]);
        (*env)->SetObjectArrayElement(env, result, i, str);
        (*env)->DeleteLocalRef(env, str);
        free(processes[i]);
    }

    LOGD("Process list: %d processes", count);
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getNetworkStats(JNIEnv *env, jclass clazz) {
    FILE* netFile = fopen("/proc/net/dev", "r");
    if (!netFile) {
        LOGE("Failed to open /proc/net/dev");
        return NULL;
    }
    char line[256];
    long rxBytes = 0, txBytes = 0;
    fgets(line, sizeof(line), netFile);
    fgets(line, sizeof(line), netFile);

    while (fgets(line, sizeof(line), netFile)) {
        char iface[32];
        long rx, tx;
        if (sscanf(line, "%s %ld %*d %*d %*d %*d %*d %*d %ld", iface, &rx, &tx) == 3) {
            if (strstr(iface, "wlan") != NULL || strstr(iface, "eth") != NULL) {
                rxBytes += rx;
                txBytes += tx;
            }
        }
    }
    fclose(netFile);

    jlongArray result = (*env)->NewLongArray(env, 3);
    if (!result) return NULL;
    jlong values[3] = {rxBytes, txBytes, 0};
    (*env)->SetLongArrayRegion(env, result, 0, 3, values);

    LOGD("Network stats: RX=%ld, TX=%ld", rxBytes, txBytes);
    return result;
}

// ==================================================================
// 8. HARDWARE INFORMATION
// ==================================================================

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getDeviceModel(JNIEnv *env, jclass clazz) {
    FILE* modelFile = fopen("/proc/device-tree/model", "r");
    if (modelFile) {
        char model[256];
        if (fgets(model, sizeof(model), modelFile)) {
            fclose(modelFile);
            size_t len = strlen(model);
            if (len > 0 && model[len-1] == '\n') model[len-1] = '\0';
            LOGD("Device model: %s", model);
            return (*env)->NewStringUTF(env, model);
        }
        fclose(modelFile);
    }
    FILE* prodFile = fopen("/sys/class/dmi/id/product_name", "r");
    if (prodFile) {
        char name[256];
        if (fgets(name, sizeof(name), prodFile)) {
            fclose(prodFile);
            size_t len = strlen(name);
            if (len > 0 && name[len-1] == '\n') name[len-1] = '\0';
            return (*env)->NewStringUTF(env, name);
        }
        fclose(prodFile);
    }
    return (*env)->NewStringUTF(env, "Android Device");
}

JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getKernelVersion(JNIEnv *env, jclass clazz) {
    FILE* versionFile = fopen("/proc/version", "r");
    if (!versionFile) {
        return (*env)->NewStringUTF(env, "Unknown");
    }
    char version[256];
    if (fgets(version, sizeof(version), versionFile)) {
        fclose(versionFile);
        size_t len = strlen(version);
        if (len > 0 && version[len-1] == '\n') version[len-1] = '\0';
        LOGD("Kernel: %s", version);
        return (*env)->NewStringUTF(env, version);
    }
    fclose(versionFile);
    return (*env)->NewStringUTF(env, "Unknown");
}

JNIEXPORT jintArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getScreenResolution(JNIEnv *env, jclass clazz) {
    jintArray result = (*env)->NewIntArray(env, 3);
    if (!result) return NULL;
    jint values[3] = {1080, 1920, 420};
    (*env)->SetIntArrayRegion(env, result, 0, 3, values);
    return result;
}


// ==================================================================
// 9. HARDWARE DIAGNOSTICS
// ==================================================================
//  NOTE: All functions BLOCK for the requested duration.
//  Call them from a background thread (never the UI thread).
// ==================================================================

// ------------------- DISPLAY (legacy fbdev) -----------------------
// Fills the framebuffer with a solid test color, holds it for
// durationMs, then restores the previous content.
//   testType: 0=red 1=green 2=blue 3=white 4=black 5=gradient
//
// WARNING: On API 26+ SELinux denies /dev/graphics/fb0 to apps, so
// this returns JNI_FALSE on modern devices. For production display
// tests use a Java SurfaceView overlay instead.
JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testDisplay(JNIEnv* env, jclass clazz,
                                                       jint testType, jint durationMs) {
    int fd = open("/dev/graphics/fb0", O_RDWR);
    if (fd < 0) {
        LOGE("testDisplay: cannot open framebuffer (SELinux/root needed on API 26+)");
        return JNI_FALSE;
    }

    struct fb_var_screeninfo vinfo;
    struct fb_fix_screeninfo finfo;
    if (ioctl(fd, FBIOGET_VSCREENINFO, &vinfo) != 0 ||
        ioctl(fd, FBIOGET_FSCREENINFO, &finfo) != 0) {
        LOGE("testDisplay: framebuffer ioctl failed");
        close(fd);
        return JNI_FALSE;
    }

    long screensize = (long)finfo.line_length * vinfo.yres;
    uint8_t* fb = (uint8_t*)mmap(0, screensize, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (fb == MAP_FAILED) {
        LOGE("testDisplay: mmap failed");
        close(fd);
        return JNI_FALSE;
    }

    uint8_t* saved = (uint8_t*)malloc(screensize);
    if (saved) memcpy(saved, fb, screensize);

    LOGD("testDisplay: %dx%d bpp=%d type=%d", vinfo.xres, vinfo.yres, vinfo.bits_per_pixel, testType);

    for (long i = 0; i < screensize; i += (vinfo.bits_per_pixel / 8)) {
        uint8_t r = 0, g = 0, b = 0;
        switch (testType) {
            case 0: r = 255; break;
            case 1: g = 255; break;
            case 2: b = 255; break;
            case 3: r = g = b = 255; break;
            case 4: r = g = b = 0;   break;
            case 5: { // horizontal gradient
                long pixel = i / (vinfo.bits_per_pixel / 8);
                int x = (int)(pixel % vinfo.xres);
                r = g = b = (uint8_t)((x * 255) / (vinfo.xres > 1 ? vinfo.xres - 1 : 1));
                break;
            }
            default: r = g = b = 255; break;
        }
        if (vinfo.bits_per_pixel == 32) {
            fb[i]     = b;  // most Android fb are BGRA/XBGR
            fb[i + 1] = g;
            fb[i + 2] = r;
            fb[i + 3] = 255;
        } else if (vinfo.bits_per_pixel == 16) {
            uint16_t p = (uint16_t)(((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3));
            fb[i] = p & 0xFF; fb[i + 1] = (p >> 8) & 0xFF;
        } else { // fallback: raw fill
            fb[i] = r;
        }
    }

    usleep((useconds_t)clampInt(durationMs, 100, 60000) * 1000);

    if (saved) {
        memcpy(fb, saved, screensize);
        free(saved);
    }
    munmap(fb, screensize);
    close(fd);
    LOGD("testDisplay: done");
    return JNI_TRUE;
}

// ------------------- AUDIO (OpenSL ES tone generator) -------------
// Generates a pure sine wave at `frequency` Hz and plays it through
// the speaker/headset for durationMs. Fully native, no files, no su.

#define AUDIO_SAMPLE_RATE   44100
#define AUDIO_CHUNK_FRAMES  4096
#define AUDIO_BQ_COUNT      4

typedef struct {
    SLAndroidSimpleBufferQueueItf bq;
    short* pcm;
    int    totalFrames;
    int    nextFrame;
    int    chunkFrames;
} ToneState;

static void toneBufferCallback(SLAndroidSimpleBufferQueueItf bq, void* ctx) {
    ToneState* s = (ToneState*)ctx;
    if (s->nextFrame >= s->totalFrames) return;
    int n = s->chunkFrames;
    if (s->nextFrame + n > s->totalFrames) n = s->totalFrames - s->nextFrame;
    (*s->bq)->Enqueue(s->bq, s->pcm + s->nextFrame, n * (SLuint32)sizeof(short));
    s->nextFrame += n;
}

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testAudio(JNIEnv* env, jclass clazz,
                                                     jint frequency, jint durationMs) {
    if (durationMs <= 0) return JNI_FALSE;
    frequency = clampInt(frequency, 20, 20000);
    durationMs = clampInt(durationMs, 100, 30000);

    SLresult res;
    SLObjectItf engineObj = NULL, mixObj = NULL, playerObj = NULL;
    SLPlayItf playerPlay = NULL;
    short* pcm = NULL;
    jboolean ok = JNI_FALSE;

    // 1. engine
    res = slCreateEngine(&engineObj, 0, NULL, 0, NULL, NULL);
    if (res != SL_RESULT_SUCCESS) { LOGE("testAudio: slCreateEngine failed %d", res); goto done; }
    (*engineObj)->Realize(engineObj, SL_BOOLEAN_FALSE);
    SLEngineItf engine;
    (*engineObj)->GetInterface(engineObj, SL_IID_ENGINE, &engine);

    // 2. output mix
    res = (*engine)->CreateOutputMix(engine, &mixObj, 0, NULL, NULL);
    if (res != SL_RESULT_SUCCESS) { LOGE("testAudio: CreateOutputMix failed %d", res); goto done; }
    (*mixObj)->Realize(mixObj, SL_BOOLEAN_FALSE);

    // 3. PCM player with buffer queue
    SLDataLocator_AndroidSimpleBufferQueue locBq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, AUDIO_BQ_COUNT };
    SLDataFormat_PCM fmt = {
            SL_DATAFORMAT_PCM, 1, AUDIO_SAMPLE_RATE * 1000,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN };
    SLDataSource src = { &locBq, &fmt };
    SLDataLocator_OutputMix locOut = { SL_DATALOCATOR_OUTPUTMIX, mixObj };
    SLDataSink sink = { &locOut, NULL };
    SLInterfaceID ids[] = { SL_IID_BUFFERQUEUE };
    SLboolean req[] = { SL_BOOLEAN_TRUE };
    res = (*engine)->CreateAudioPlayer(engine, &playerObj, &src, &sink, 1, ids, req);
    if (res != SL_RESULT_SUCCESS) { LOGE("testAudio: CreateAudioPlayer failed %d", res); goto done; }
    (*playerObj)->Realize(playerObj, SL_BOOLEAN_FALSE);
    (*playerObj)->GetInterface(playerObj, SL_IID_PLAY, &playerPlay);

    // 4. precompute the whole sine with 5ms fade in/out (avoids clicks)
    int totalFrames = AUDIO_SAMPLE_RATE * durationMs / 1000;
    pcm = (short*)malloc((size_t)totalFrames * sizeof(short));
    if (!pcm) { LOGE("testAudio: OOM"); goto done; }

    double phase = 0.0, phaseInc = 2.0 * PI * frequency / AUDIO_SAMPLE_RATE;
    int fadeFrames = AUDIO_SAMPLE_RATE * 5 / 1000;
    for (int i = 0; i < totalFrames; i++) {
        double amp = 0.5;
        if (i < fadeFrames)                 amp = 0.5 * i / fadeFrames;
        if (i > totalFrames - fadeFrames)   amp = 0.5 * (totalFrames - i) / fadeFrames;
        pcm[i] = (short)(sin(phase) * 32767.0 * amp);
        phase += phaseInc;
        if (phase > 2.0 * PI) phase -= 2.0 * PI;
    }

    // 5. feed the queue via callback, then play
    ToneState state;
    (*playerObj)->GetInterface(playerObj, SL_IID_BUFFERQUEUE, &state.bq);
    state.pcm = pcm; state.totalFrames = totalFrames;
    state.nextFrame = 0; state.chunkFrames = AUDIO_CHUNK_FRAMES;
    (*state.bq)->RegisterCallback(state.bq, toneBufferCallback, &state);
    // pre-fill half the queue, then start
    for (int i = 0; i < AUDIO_BQ_COUNT / 2; i++) toneBufferCallback(state.bq, &state);
    res = (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_PLAYING);
    if (res != SL_RESULT_SUCCESS) { LOGE("testAudio: SetPlayState failed"); goto done; }

    // wait until all frames are consumed (with 1s grace)
    long long deadline = nowMs() + durationMs + 1000;
    while (state.nextFrame < totalFrames && nowMs() < deadline) usleep(5000);
    (*playerPlay)->SetPlayState(playerPlay, SL_PLAYSTATE_STOPPED);

    ok = (state.nextFrame >= totalFrames) ? JNI_TRUE : JNI_FALSE;
    LOGD("testAudio: freq=%d Hz dur=%d ms -> %s", frequency, durationMs,
         ok ? "OK" : "TIMEOUT");

    done:
    if (playerObj) (*playerObj)->Destroy(playerObj);
    if (mixObj)    (*mixObj)->Destroy(mixObj);
    if (engineObj) (*engineObj)->Destroy(engineObj);
    free(pcm);
    return ok;
}

// ------------------- MICROPHONE (OpenSL ES recorder) --------------
// Records from the mic for durationMs and returns:
//   float[0] = RMS level        0.0 .. 1.0
//   float[1] = peak level       0.0 .. 1.0
//   float[2] = mean |amplitude| 0.0 .. 1.0
//   float[3] = frames captured

#define REC_SAMPLE_RATE  16000
#define REC_CHUNK_FRAMES 1024

typedef struct {
    SLAndroidSimpleBufferQueueItf bq;
    short* buf;
    long   capacity;    // frames
    long   written;     // frames
} RecState;

static void recBufferCallback(SLAndroidSimpleBufferQueueItf bq, void* ctx) {
    RecState* s = (RecState*)ctx;
    // append whatever arrived (chunk size is known by the queue item,
    // we sized writes to REC_CHUNK_FRAMES frames)
    long room = s->capacity - s->written;
    long n = room < REC_CHUNK_FRAMES ? room : REC_CHUNK_FRAMES;
    // note: the queue already holds the captured data at the same buffer
    // address we passed in; appending semantics are handled by enqueuing
    // the NEXT buffer from our ring (we just track occupancy here).
    s->written += n;
    if (s->written < s->capacity) {
        (*s->bq)->Enqueue(s->bq, s->buf + s->written, REC_CHUNK_FRAMES * (SLuint32)sizeof(short));
    }
}

JNIEXPORT jfloatArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testMicrophone(JNIEnv* env, jclass clazz,
                                                          jint durationMs) {
    durationMs = clampInt(durationMs, 250, 30000);

    jfloat defaults[4] = {0, 0, 0, 0};
    jfloatArray out = (*env)->NewFloatArray(env, 4);
    if (!out) return NULL;

    SLresult res;
    SLObjectItf engineObj = NULL, recObj = NULL;
    SLRecordItf recorder = NULL;
    short* cap = NULL;

    res = slCreateEngine(&engineObj, 0, NULL, 0, NULL, NULL);
    if (res != SL_RESULT_SUCCESS) goto done;
    (*engineObj)->Realize(engineObj, SL_BOOLEAN_FALSE);
    SLEngineItf engine;
    (*engineObj)->GetInterface(engineObj, SL_IID_ENGINE, &engine);

    // designated initializers: NDK headers differ in the order of the
    // 'deviceID' (SLuint32) and 'device' (SLObjectItf) fields
    SLDataLocator_IODevice locDev = {
            .locatorType = SL_DATALOCATOR_IODEVICE,
            .deviceType  = SL_IODEVICE_AUDIOINPUT,
            .deviceID    = 0,
            .device      = NULL };
    SLDataSource src = { &locDev, NULL };
    SLDataLocator_AndroidSimpleBufferQueue locBq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
    SLDataFormat_PCM fmt = {
            SL_DATAFORMAT_PCM, 1, REC_SAMPLE_RATE * 1000,
            SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN };
    SLDataSink sink = { &locBq, &fmt };
    SLInterfaceID ids[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE };
    SLboolean req[] = { SL_BOOLEAN_TRUE };

    res = (*engine)->CreateAudioRecorder(engine, &recObj, &src, &sink, 1, ids, req);
    if (res != SL_RESULT_SUCCESS) { LOGE("testMicrophone: CreateAudioRecorder %d", res); goto done; }
    (*recObj)->Realize(recObj, SL_BOOLEAN_FALSE);
    (*recObj)->GetInterface(recObj, SL_IID_RECORD, &recorder);

    // pad to a whole number of chunks: the buffer queue always delivers
    // full REC_CHUNK_FRAMES-sized buffers, so the last one must fit exactly
    long capacity = (long)REC_SAMPLE_RATE * durationMs / 1000;
    capacity = ((capacity + REC_CHUNK_FRAMES - 1) / REC_CHUNK_FRAMES) * REC_CHUNK_FRAMES;
    cap = (short*)malloc((size_t)capacity * sizeof(short));
    if (!cap) goto done;

    RecState state;
    (*recObj)->GetInterface(recObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &state.bq);
    state.buf = cap; state.capacity = capacity; state.written = 0;
    (*state.bq)->RegisterCallback(state.bq, recBufferCallback, &state);
    (*state.bq)->Enqueue(state.bq, cap, REC_CHUNK_FRAMES * (SLuint32)sizeof(short));

    res = (*recorder)->SetRecordState(recorder, SL_RECORDSTATE_RECORDING);
    if (res != SL_RESULT_SUCCESS) { LOGE("testMicrophone: SetRecordState failed"); goto done; }

    usleep((useconds_t)durationMs * 1000);
    (*recorder)->SetRecordState(recorder, SL_RECORDSTATE_STOPPED);

    long n = state.written;
    LOGD("testMicrophone: captured %ld frames (%ld ms)", n, n * 1000 / REC_SAMPLE_RATE);
    if (n <= 0) goto done;

    double sumSq = 0.0, sumAbs = 0.0; long peak = 0;
    for (long i = 0; i < n; i++) {
        long a = cap[i] < 0 ? -(long)cap[i] : (long)cap[i];
        if (a > peak) peak = a;
        sumSq  += (double)cap[i] * (double)cap[i];
        sumAbs += (double)a;
    }
    defaults[0] = (jfloat)(sqrt(sumSq / n) / 32768.0);   // RMS
    defaults[1] = (jfloat)(peak / 32768.0);              // peak
    defaults[2] = (jfloat)(sumAbs / n / 32768.0);        // mean abs
    defaults[3] = (jfloat)n;                             // frames

    done:
    if (recObj)    (*recObj)->Destroy(recObj);
    if (engineObj) (*engineObj)->Destroy(engineObj);
    free(cap);
    (*env)->SetFloatArrayRegion(env, out, 0, 4, defaults);
    return out;
}

// ------------------- CAMERA (Camera2 NDK smoke test) --------------
// Opens the camera device, keeps it open for durationMs, then closes.
// Verifies the camera HAL is functional without needing a Surface.

#ifdef HAVE_NDK_CAMERA
static void camDisconnected(void* ctx, ACameraDevice* dev) {
    (void)ctx; LOGD("camera disconnected"); }
static void camError(void* ctx, ACameraDevice* dev, int error) {
    (void)ctx; LOGE("camera error %d", error); }
#endif

JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testCamera(JNIEnv* env, jclass clazz,
                                                      jint cameraId, jint durationMs) {
#ifdef HAVE_NDK_CAMERA
    durationMs = clampInt(durationMs, 500, 10000);

    ACameraManager* mgr = ACameraManager_create();
    if (!mgr) { LOGE("testCamera: ACameraManager_create failed"); return JNI_FALSE; }

    ACameraIdList* idList = NULL;
    if (ACameraManager_getCameraIdList(mgr, &idList) != ACAMERA_OK || !idList) {
        LOGE("testCamera: getCameraIdList failed");
        ACameraManager_delete(mgr);
        return JNI_FALSE;
    }
    LOGD("testCamera: %d camera(s) available", idList->numCameras);
    if (idList->numCameras == 0) {
        ACameraManager_deleteCameraIdList(idList);
        ACameraManager_delete(mgr);
        return JNI_FALSE;
    }

    int idx = clampInt(cameraId, 0, idList->numCameras - 1);
    const char* idStr = idList->cameraIds[idx];

    ACameraDevice_stateCallbacks cbs;
    cbs.context = NULL;
    cbs.onDisconnected = camDisconnected;
    cbs.onError = camError;

    ACameraDevice* device = NULL;
    camera_status_t st = ACameraManager_openCamera(mgr, idStr, &cbs, &device);
    if (st != ACAMERA_OK || !device) {
        LOGE("testCamera: openCamera(%s) failed: %d", idStr, st);
        ACameraManager_deleteCameraIdList(idList);
        ACameraManager_delete(mgr);
        return JNI_FALSE;
    }

    LOGD("testCamera: opened %s, holding for %d ms", idStr, durationMs);
    usleep((useconds_t)durationMs * 1000);

    ACameraDevice_close(device);
    ACameraManager_deleteCameraIdList(idList);
    ACameraManager_delete(mgr);
    LOGD("testCamera: closed OK");
    return JNI_TRUE;
#else
    LOGE("testCamera: requires minSdk 24+");
    return JNI_FALSE;
#endif
}

// ------------------- SENSORS (NDK ASensorManager) -----------------
// Enables the given sensor, polls events for durationMs, returns:
//   float[0] = event count
//   float[1] = average event rate (Hz)
//   float[2..4] = last x/y/z values (vector sensors)

JNIEXPORT jfloatArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testSensor(JNIEnv* env, jclass clazz,
                                                      jint sensorType, jint durationMs) {
    if (sensorType <= 0) sensorType = ASENSOR_TYPE_ACCELEROMETER;
    durationMs = clampInt(durationMs, 500, 30000);

    jfloat results[5] = {0, 0, 0, 0, 0};
    jfloatArray out = (*env)->NewFloatArray(env, 5);
    if (!out) return NULL;

    ASensorManager* mgr = ASensorManager_getInstance();
    if (!mgr) { LOGE("testSensor: ASensorManager null"); goto done; }

    const ASensor* sensor = ASensorManager_getDefaultSensor(mgr, sensorType);
    if (!sensor) {
        LOGE("testSensor: no default sensor type %d", sensorType);
        goto done;
    }
    LOGD("testSensor: %s (vendor %s), resolution %.3f, min delay %d us",
         ASensor_getName(sensor), ASensor_getVendor(sensor),
         ASensor_getResolution(sensor), ASensor_getMinDelay(sensor));

    ALooper* looper = ALooper_prepare(0);
    if (!looper) { LOGE("testSensor: ALooper_prepare failed"); goto done; }

    ASensorEventQueue* queue = ASensorManager_createEventQueue(mgr, looper, 0, NULL, NULL);
    if (!queue) { LOGE("testSensor: createEventQueue failed"); goto done; }

    int rateUs = ASensor_getMinDelay(sensor);
    if (rateUs <= 0) rateUs = 20000;   // 50 Hz default
    if (ASensorEventQueue_enableSensor(queue, sensor) != 0) {
        LOGE("testSensor: enableSensor failed"); goto done_destroy; }
    ASensorEventQueue_setEventRate(queue, sensor, rateUs);

    long long start = nowMs();
    long count = 0;
    ASensorEvent ev;
    while (nowMs() - start < durationMs) {
        int remain = (int)(durationMs - (nowMs() - start));
        ALooper_pollOnce(remain, NULL, NULL, NULL);
        while (ASensorEventQueue_getEvents(queue, &ev, 1) > 0) {
            count++;
            results[2] = ev.vector.x;
            results[3] = ev.vector.y;
            results[4] = ev.vector.z;
        }
    }

    results[0] = (jfloat)count;
    results[1] = count > 0 ? (jfloat)(count * 1000.0 / (nowMs() - start)) : 0;
    LOGD("testSensor: %ld events, %.1f Hz", count, results[1]);

    ASensorEventQueue_disableSensor(queue, sensor);
    done_destroy:
    ASensorManager_destroyEventQueue(mgr, queue);
    done:
    (*env)->SetFloatArrayRegion(env, out, 0, 5, results);
    return out;
}

// ------------------- VIBRATION (timed_output sysfs) ---------------
// Vibrates for durationMs using the classic vibrator sysfs node.
JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testVibration(JNIEnv* env, jclass clazz,
                                                         jint durationMs) {
    durationMs = clampInt(durationMs, 50, 10000);
    const char* vibPaths[] = {
            "/sys/class/timed_output/vibrator/enable",
            "/sys/devices/virtual/timed_output/vibrator/enable",
            "/sys/class/vibrator/vibrator/enable",
            NULL
    };
    char value[16];
    snprintf(value, sizeof(value), "%d", durationMs);

    for (int i = 0; vibPaths[i]; i++) {
        if (access(vibPaths[i], F_OK) == 0) {
            if (writeSysfsOrRoot(vibPaths[i], value) == 0) {
                LOGD("testVibration: wrote %s to %s", value, vibPaths[i]);
                return JNI_TRUE;
            }
        }
    }
    LOGE("testVibration: no vibrator sysfs node found (try VIBRATOR_SERVICE in Java)");
    return JNI_FALSE;
}

// ------------------- FLASHLIGHT (leds sysfs) ----------------------
// Toggles the camera flash LED. Finds the first writable LED node.
JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_testFlashlight(JNIEnv* env, jclass clazz,
                                                          jboolean enable) {
    const char* ledPaths[] = {
            "/sys/class/leds/flashlight/brightness",
            "/sys/class/leds/torch-light/brightness",
            "/sys/class/leds/torch-light0/brightness",
            "/sys/class/leds/flash0/brightness",
            "/sys/class/leds/led:torch_0/brightness",
            NULL
    };

    char value[8];
    snprintf(value, sizeof(value), "%d", enable ? 1 : 0);

    for (int i = 0; ledPaths[i]; i++) {
        if (access(ledPaths[i], F_OK) != 0) continue;

        // use max_brightness when turning on (many nodes are 0..255 or 0..200)
        char maxVal[8] = "1";
        if (enable) {
            char maxPath[256];
            snprintf(maxPath, sizeof(maxPath), "%s", ledPaths[i]);
            char* slash = strrchr(maxPath, '/');
            if (slash) {
                strcpy(slash, "/max_brightness");
                int mfd = open(maxPath, O_RDONLY);
                if (mfd >= 0) {
                    ssize_t r = read(mfd, maxVal, sizeof(maxVal) - 1);
                    if (r > 0) { maxVal[r] = '\0'; }
                    close(mfd);
                    // strip newline
                    char* nl = strchr(maxVal, '\n'); if (nl) *nl = '\0';
                }
            }
            snprintf(value, sizeof(value), "%s", maxVal);
        }

        if (writeSysfsOrRoot(ledPaths[i], value) == 0) {
            LOGD("testFlashlight: wrote %s to %s", value, ledPaths[i]);
            return JNI_TRUE;
        }
    }
    LOGE("testFlashlight: no writable LED node found");
    return JNI_FALSE;
}