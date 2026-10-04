#include <string.h>

#include <jni.h>
#include <getopt.h>
#include <signal.h>
#include <setjmp.h>
#include <stdlib.h>

#include "error.h"
#include "main.h"
#include "automatic_access.h"

extern int server_fd;
static int g_proxy_running = 0;
static JavaVM *g_vm;
static jclass g_access_controller;
static jmethodID g_access_observer;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    g_vm = vm;
    return JNI_VERSION_1_6;
}

static void notify_access_host(uint64_t epoch, const char *host,
        const char *original_ip, int port) {
    JNIEnv *env = NULL;
    if (!g_vm || !g_access_controller || !g_access_observer ||
            (*g_vm)->GetEnv(g_vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return;
    jstring jhost = (*env)->NewStringUTF(env, host);
    jstring jip = (*env)->NewStringUTF(env, original_ip);
    if (jhost && jip) (*env)->CallStaticVoidMethod(env, g_access_controller,
        g_access_observer, (jlong)epoch, jhost, jip, (jint)port);
    if (jhost) (*env)->DeleteLocalRef(env, jhost);
    if (jip) (*env)->DeleteLocalRef(env, jip);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

static void prepare_access_observer(JNIEnv *env) {
    if (!g_access_controller) {
        jclass local = (*env)->FindClass(env,
            "io/maffinet/android/core/access/AutomaticAccessController");
        if (local) {
            g_access_controller = (*env)->NewGlobalRef(env, local);
            (*env)->DeleteLocalRef(env, local);
        }
        if (g_access_controller) g_access_observer = (*env)->GetStaticMethodID(env,
            g_access_controller, "onNativeHostObserved", "(JLjava/lang/String;Ljava/lang/String;I)V");
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    }
    access_set_observer(g_access_observer ? &notify_access_host : NULL);
}

JNIEXPORT void JNICALL
Java_io_maffinet_android_core_dpibypass_ByeDpiProxy_jniSetAccessEpoch(
        JNIEnv *env, jobject thiz, jlong epoch) {
    (void)env; (void)thiz;
    access_set_epoch(epoch > 0 ? (uint64_t)epoch : 0);
}

JNIEXPORT jboolean JNICALL
Java_io_maffinet_android_core_dpibypass_ByeDpiProxy_jniUpdateHostRoute(
        JNIEnv *env, jobject thiz, jlong epoch, jstring host, jint port,
        jstring ipv4, jint ttl_seconds) {
    (void)thiz;
    if (!host || epoch <= 0) return JNI_FALSE;
    const char *name = (*env)->GetStringUTFChars(env, host, NULL);
    if (!name) return JNI_FALSE;
    const char *ip = ipv4 ? (*env)->GetStringUTFChars(env, ipv4, NULL) : NULL;
    bool updated = (!ipv4 || ip) && access_update_route((uint64_t)epoch,
        name, port, ip, ttl_seconds);
    if (ip) (*env)->ReleaseStringUTFChars(env, ipv4, ip);
    (*env)->ReleaseStringUTFChars(env, host, name);
    return updated ? JNI_TRUE : JNI_FALSE;
}

struct params default_params = {
        .await_int = 10,
        .ipv6 = 1,
        .resolve = 1,
        .udp = 1,
        .max_open = 512,
        .bfsize = 16384,
        .baddr = {
            .in6 = { .sin6_family = AF_INET6 }
        },
        .laddr = {
            .in = { .sin_family = AF_INET }
        },
        .debug = 0
};

void reset_params(void) {
    clear_params(NULL, NULL);
    params = default_params;
}

JNIEXPORT jint JNICALL
Java_io_maffinet_android_core_dpibypass_ByeDpiProxy_jniStartProxy(JNIEnv *env, __attribute__((unused)) jobject thiz, jobjectArray args) {
    if (g_proxy_running) {
        LOG(LOG_S, "proxy already running");
        return -1;
    }

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc, sizeof(char *));

    if (!argv) {
        LOG(LOG_S, "failed to allocate memory for argv");
        return -1;
    }

    for (int i = 0; i < argc; i++) {
        jstring arg = (jstring) (*env)->GetObjectArrayElement(env, args, i);

        if (!arg) {
            argv[i] = NULL;
            continue;
        }

        const char *arg_str = (*env)->GetStringUTFChars(env, arg, 0);
        argv[i] = arg_str ? strdup(arg_str) : NULL;

        if (arg_str) (*env)->ReleaseStringUTFChars(env, arg, arg_str);

        (*env)->DeleteLocalRef(env, arg);
    }
    
    LOG(LOG_S, "starting proxy with %d args", argc);
    prepare_access_observer(env);
    reset_params();
    g_proxy_running = 1;
    optind = 1;

    int result = main(argc, argv);

    LOG(LOG_S, "proxy return code %d", result);
    g_proxy_running = 0;

    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);

    return result;
}

JNIEXPORT jint JNICALL
Java_io_maffinet_android_core_dpibypass_ByeDpiProxy_jniStopProxy(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "send shutdown to proxy");

    if (!g_proxy_running) {
        LOG(LOG_S, "proxy is not running");
        return -1;
    }

    shutdown(server_fd, SHUT_RDWR);
    g_proxy_running = 0;

    return 0;
}

JNIEXPORT jint JNICALL
Java_io_maffinet_android_core_dpibypass_ByeDpiProxy_jniForceClose(__attribute__((unused)) JNIEnv *env, __attribute__((unused)) jobject thiz) {
    LOG(LOG_S, "closing server socket (fd: %d)", server_fd);

    if (close(server_fd) == -1) {
        LOG(LOG_S, "failed to close server socket (fd: %d)", server_fd);
        return -1;
    }

    LOG(LOG_S, "proxy socket force close");
    g_proxy_running = 0;

    return 0;
}
