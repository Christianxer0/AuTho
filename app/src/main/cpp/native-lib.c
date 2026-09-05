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

#define LOG_TAG "AuThoNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 1. CPU INFORMATION

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

    prevTotal =total;
    prevIdle = idleTotal;

    if(diffTotal == 0) return 0;
    int usage = (int)(100.0f * (diffTotal - diffIdle) / diffTotal);
    if(usage <  0) usage = 0;
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
    LOGD("CPU temp:  %.1f°C", celsius);
    return celsius;
}

// 2. RAM INFORMATION

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

//3. BATTERY INFORMATION

JNIEXPORT jintArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getBatteryInfo(JNIEnv *env, jclass clazz) {
    jintArray result = (*env)->NewIntArray(env, 6);
    if(!result) return NULL;
        jint values[6] = {75,2, 1, 0, 37, 4200};
    (*env)->SetIntArrayRegion(env, result, 0, 6, values);
    return result;
}

// 4. STORAGE INFORMATION




JNIEXPORT jlongArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getInternalStorageInfo(JNIEnv *env, jclass clazz) {
    struct statvfs stat;
    if(statvfs("/data", &stat) != 0) {
        LOGE("statvfs failed");
        return NULL;
    }

    unsigned long total = stat.f_blocks * stat.f_frsize;
    unsigned long free = stat.f_bfree * stat.f_frsize;
    unsigned long used = total / free;

    jlongArray result = (*env)->NewLongArray(env, 3);
    if(!result) return NULL;
    jlong values[3] = {(jlong)total, (jlong)free, (jlong)used};
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
            unsigned long free = stat.f_bfree * stat.f_frsize;
            unsigned long used = total - free;
            jlongArray  result = (*env)->NewLongArray(env, 3);
            if(!result) return NULL;
            jlong values[3] = {(jlong)total, (jlong)used, (jlong)free};
            (*env)->SetLongArrayRegion(env, result, 0, 3, values);
            LOGD("External storage found at %s total= %lu, used=%lu, free=%lu", paths[i],
                 total, used, free);
        }
    }
    LOGE("External storage not found");
    return NULL;
}

// 5. NETWORK SCANNER

JNIEXPORT jobjectArray JNICALL
Java_com_yourteam_autho_utils_NativeHelper_scanNetworkDevices(JNIEnv *env, jclass clazz,
                                                              jint timeout_ms) {
    LOGD("Scanning network devices (timeout: %d ms)", timeout_ms);

    char ipPrefix[16] = "192.168.1.";

    struct ifaddrs *ifaddr, *ifa;
    if(getifaddrs(&ifaddr) == 0) {
        for(ifa = ifaddr; ifa != NULL; ifa = ifa->ifa_next) {
            if(ifa->ifa_addr == NULL) continue;
            if(ifa->ifa_addr->sa_family == AF_INET &&
            (strcmp(ifa->ifa_name, "wlan0")) == 0 || strcmp(ifa->ifa_name, "eth0") == 0) {
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
    char* devices[250];

    for(int i = 1; i <= 254; i++) {
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
    for(ifa = ifaddr; ifa != NULL; ifa->ifa_next) {
        if(ifa->ifa_addr == NULL) continue;
        if(ifa->ifa_addr == AF_INET) {
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
        LOGE("Failed to open /pro/net/route");
        return (*env)->NewStringUTF(env, "192.168.1.1");
    }
    char line[256];
    char iface[16];
    unsigned  int dest, gateway;
    char gatewayIP[INET_ADDRSTRLEN] = "0.0.0.0";
    while(fgets(line, sizeof(line), routeFile)) {
        if(sscanf(line, "%s %x %x", iface, &dest, &gateway) == 3){
            if(dest == 0 && (strcmp(iface, "wlan0") || strcmp(iface, "eth0") == 0)) {
                struct in_addr addr;
                addr.s_addr = gateway;
                strcpy(gatewayIP, inet_ntoa(addr));
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
    LOGD("Ping %s: %s", ip, result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

// 6. ROOT OPERATIONS



JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_isDeviceRooted(JNIEnv *env, jclass clazz) {
    const char* suPath[] = {
      "/system/bin/su",
      "/system/xbin/su",
      "/system/sbin/su",
      "/sbin/su",
      "/data/local/xbin/su",
      "/data/local/bin/su",
      "/data/local/su",
      NULL
    };
    for(int i = 0; suPath[i] != NULL; i++)
    {
        if(access(suPath[i], F_OK) == 0) {
            LOGD("Root Detected: %s", suPath[i]);
            return JNI_TRUE;
        }
    }

    const char* rootPackages[] = {
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            NULL
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
    LOGD("Root command result: %s", result);
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
    // Kill all background processes (system command)
    system("su -c 'am kill-all'");
    // We can't easily count killed processes, return a placeholder
    LOGD("Killed background processes");
    return 1;
}


JNIEXPORT jboolean JNICALL
Java_com_yourteam_autho_utils_NativeHelper_clearSystemCache(JNIEnv *env, jclass clazz) {
    int result = system("su -c 'sync && echo 3 > /proc/sys/vm/drop_caches'");
    LOGD("Clear system cache: %s", result == 0 ? "SUCCESS" : "FAILED");
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

// 7. PERFORMANCE & PROCESS LIST


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

    // Skip header line
    fgets(line, sizeof(line), psFile);

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
    // Skip header lines
    fgets(line, sizeof(line), netFile);
    fgets(line, sizeof(line), netFile);

    while (fgets(line, sizeof(line), netFile)) {
        char iface[32];
        long rx, tx;
        // Parse: iface: rx_bytes rx_packets rx_errors ... tx_bytes
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
    jlong values[3] = {rxBytes, txBytes, 0}; // speed placeholder
    (*env)->SetLongArrayRegion(env, result, 0, 3, values);

    LOGD("Network stats: RX=%ld, TX=%ld", rxBytes, txBytes);
    return result;
}

// 8. HARDWARE INFORMATION


JNIEXPORT jstring JNICALL
Java_com_yourteam_autho_utils_NativeHelper_getDeviceModel(JNIEnv *env, jclass clazz) {
    FILE* modelFile = fopen("/proc/device-tree/model", "r");
    if (modelFile) {
        char model[256];
        if (fgets(model, sizeof(model), modelFile)) {
            fclose(modelFile);
            // Remove newline
            size_t len = strlen(model);
            if (len > 0 && model[len-1] == '\n') model[len-1] = '\0';
            LOGD("Device model: %s", model);
            return (*env)->NewStringUTF(env, model);
        }
        fclose(modelFile);
    }
    // Fallback: read from /sys/class/dmi/id/product_name (if available)
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
        // Remove newline
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