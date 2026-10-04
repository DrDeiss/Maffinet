/* Maffinet P01 experiment. Single joinable worker; Java retains its original TUN.
 * All callbacks copy addresses; no lwIP pcb or packet pointer crosses JNI.
 */
#include <jni.h>
#include <errno.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include "hev-main.h"
#include "hooks.h"

static JavaVM *vm;
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t changed = PTHREAD_COND_INITIALIZER;
static pthread_t thread;
static jobject owner;
static jmethodID protect_method, tuple_method;
static unsigned char *configuration;
static unsigned int configuration_len;
static int tun = -1, active, joinable, ready, cancelled, result;
static JNIEnv *worker_env;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *value, void *unused)
{
    vm = value;
    return JNI_VERSION_1_6;
}

int maffinet_lab_protect_loopback(int fd)
{
    /* HEV can only connect to our fixed loopback SOCKS endpoint.
     * Its external TCP/UDP relays use the Java protect+physical-bind factory. */
    if (!worker_env) return -1;
    jboolean ok = (*worker_env)->CallBooleanMethod(worker_env, owner, protect_method, fd);
    if ((*worker_env)->ExceptionCheck(worker_env)) {
        (*worker_env)->ExceptionClear(worker_env);
        return -1;
    }
    return ok ? 0 : -1;
}

void maffinet_lab_tuple(int protocol, const ip_addr_t *local, unsigned short lp,
                       const ip_addr_t *remote, unsigned short rp)
{
    if (!worker_env || IP_GET_TYPE(local) != IP_GET_TYPE(remote)) return;
    int bytes = IP_IS_V6(local) ? 16 : 4;
    const void *l = IP_IS_V6(local) ? (void *)ip_2_ip6(local)->addr : (void *)&ip_2_ip4(local)->addr;
    const void *r = IP_IS_V6(remote) ? (void *)ip_2_ip6(remote)->addr : (void *)&ip_2_ip4(remote)->addr;
    jbyteArray la = (*worker_env)->NewByteArray(worker_env, bytes);
    jbyteArray ra = (*worker_env)->NewByteArray(worker_env, bytes);
    if (la && ra) {
        (*worker_env)->SetByteArrayRegion(worker_env, la, 0, bytes, l);
        (*worker_env)->SetByteArrayRegion(worker_env, ra, 0, bytes, r);
        (*worker_env)->CallVoidMethod(worker_env, owner, tuple_method, protocol, bytes == 16 ? 6 : 4, la, lp, ra, rp);
    }
    if ((*worker_env)->ExceptionCheck(worker_env)) (*worker_env)->ExceptionClear(worker_env);
    if (la) (*worker_env)->DeleteLocalRef(worker_env, la);
    if (ra) (*worker_env)->DeleteLocalRef(worker_env, ra);
}

void maffinet_lab_ready(void)
{
    pthread_mutex_lock(&lock);
    ready = 1;
    if (cancelled) hev_socks5_tunnel_quit();
    pthread_cond_broadcast(&changed);
    pthread_mutex_unlock(&lock);
}

void maffinet_lab_stopping(void)
{
    /* Disable STOP before HEV closes its event pipe; protects against fd reuse. */
    pthread_mutex_lock(&lock);
    ready = 0;
    pthread_mutex_unlock(&lock);
}

static void *run(void *unused)
{
    int attached = (*vm)->AttachCurrentThread(vm, (void **)&worker_env, NULL) == JNI_OK;
    int outcome = attached ? maffinet_lab_run(configuration, configuration_len, tun) : -6;
    if (attached) (*vm)->DetachCurrentThread(vm);
    worker_env = NULL;
    close(tun); /* HEV does not close an externally supplied fd. */
    pthread_mutex_lock(&lock);
    tun = -1;
    ready = 0;
    result = outcome;
    active = 0;
    pthread_cond_broadcast(&changed);
    pthread_mutex_unlock(&lock);
    return NULL;
}

JNIEXPORT jint JNICALL Java_io_maffinet_lab_transport_NativeTransport_start
  (JNIEnv *env, jobject self, jstring config, jint fd)
{
    pthread_mutex_lock(&lock);
    if (joinable) { pthread_mutex_unlock(&lock); return -10; }
    jclass cls = (*env)->GetObjectClass(env, self);
    protect_method = (*env)->GetMethodID(env, cls, "protectLoopback", "(I)Z");
    tuple_method = (*env)->GetMethodID(env, cls, "originalTuple", "(II[BI[BI)V");
    (*env)->DeleteLocalRef(env, cls);
    if (!protect_method || !tuple_method) { pthread_mutex_unlock(&lock); return -11; }
    const char *text = (*env)->GetStringUTFChars(env, config, NULL);
    if (!text) { pthread_mutex_unlock(&lock); return -12; }
    configuration = (unsigned char *)strdup(text);
    (*env)->ReleaseStringUTFChars(env, config, text);
    tun = dup(fd);
    owner = (*env)->NewGlobalRef(env, self);
    if (!configuration || tun < 0 || !owner) goto fail;
    configuration_len = strlen((char *)configuration);
    active = 1; cancelled = 0; ready = 0; result = 0;
    if (pthread_create(&thread, NULL, run, NULL) != 0) goto fail;
    joinable = 1;
    pthread_mutex_unlock(&lock);
    return 0; /* Accepted, NOT Running. Query status for readiness/failure. */
fail:
    if (tun >= 0) close(tun);
    tun = -1;
    if (owner) (*env)->DeleteGlobalRef(env, owner);
    owner = NULL;
    free(configuration); configuration = NULL;
    active = 0;
    pthread_mutex_unlock(&lock);
    return -13;
}

JNIEXPORT jint JNICALL Java_io_maffinet_lab_transport_NativeTransport_status
  (JNIEnv *env, jobject self)
{
    pthread_mutex_lock(&lock);
    int value = active ? (cancelled ? 3 : ready ? 2 : 1) : result;
    pthread_mutex_unlock(&lock);
    return value;
}

JNIEXPORT jint JNICALL Java_io_maffinet_lab_transport_NativeTransport_stop
  (JNIEnv *env, jobject self)
{
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 1;
    pthread_mutex_lock(&lock);
    if (!joinable) { pthread_mutex_unlock(&lock); return 0; }
    if (!cancelled) {
        cancelled = 1;
        /* Early STOP is retained until ready, with no wait inside HEV quit. */
        if (ready) hev_socks5_tunnel_quit();
    }
    while (active) {
        if (pthread_cond_timedwait(&changed, &lock, &deadline) == ETIMEDOUT) {
            pthread_mutex_unlock(&lock);
            return -20; /* Still owns worker/fd; start remains forbidden. */
        }
    }
    pthread_join(thread, NULL);
    (*env)->DeleteGlobalRef(env, owner); owner = NULL;
    free(configuration); configuration = NULL;
    joinable = 0;
    int outcome = result;
    pthread_mutex_unlock(&lock);
    return outcome;
}
