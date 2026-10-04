#define _POSIX_C_SOURCE 200809L
#include "automatic_access.h"
#include <pthread.h>
#include <string.h>
#include <ctype.h>
#include <time.h>

struct access_route {
    char host[ACCESS_HOST_MAX + 1];
    int port;
    struct in_addr ip;
    uint64_t expires;
};

static pthread_mutex_t access_lock = PTHREAD_MUTEX_INITIALIZER;
static uint64_t current_epoch;
static access_observer_fn observer_callback;
static struct access_route routes[ACCESS_ROUTE_MAX];

static uint64_t monotonic_ms(void)
{
    struct timespec t;
    #ifdef CLOCK_BOOTTIME
    clock_gettime(CLOCK_BOOTTIME, &t);
    #else
    clock_gettime(CLOCK_MONOTONIC, &t);
    #endif
    return (uint64_t)t.tv_sec * 1000 + t.tv_nsec / 1000000;
}

static bool normalize_host(const char *host, char output[ACCESS_HOST_MAX + 1])
{
    if (!host) return false;
    size_t length = strnlen(host, ACCESS_HOST_MAX + 2);
    if (length && host[length - 1] == '.') length--;
    if (!length || length > ACCESS_HOST_MAX) return false;
    size_t label = 0;
    for (size_t i = 0; i < length; i++) {
        unsigned char c = host[i];
        if (c == '.') {
            if (!label || label > 63 || output[i - 1] == '-') return false;
            label = 0;
        } else {
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-')) return false;
            if (!label && c == '-') return false;
            label++;
        }
        output[i] = (char)tolower(c);
    }
    if (!label || label > 63 || output[length - 1] == '-') return false;
    output[length] = 0;
    return true;
}

static bool public_ipv4(const struct in_addr *address)
{
    uint32_t ip = ntohl(address->s_addr);
    unsigned int a = ip >> 24, b = (ip >> 16) & 255, c = (ip >> 8) & 255;
    if (!a || a == 10 || a == 127 || a >= 224) return false;
    if ((a == 100 && b >= 64 && b <= 127) || (a == 169 && b == 254)
            || (a == 172 && b >= 16 && b <= 31)) return false;
    if ((a == 192 && (b == 168 || (b == 0 && (c == 0 || c == 2))
            || (b == 88 && c == 99))) || (a == 198 && (b == 18 || b == 19
            || (b == 51 && c == 100))) || (a == 203 && b == 0 && c == 113)) return false;
    return true;
}

bool access_public_destination(const union sockaddr_u *address)
{
    if (address->sa.sa_family == AF_INET) return public_ipv4(&address->in.sin_addr);
    if (address->sa.sa_family != AF_INET6) return false;
    const unsigned char *ip = address->in6.sin6_addr.s6_addr;
    if (IN6_IS_ADDR_V4MAPPED(&address->in6.sin6_addr)) {
        struct in_addr v4; memcpy(&v4, ip + 12, sizeof(v4));
        return public_ipv4(&v4);
    }
    /* Conservative global-unicast gate; reject local/special/documentation
     * destinations even if a matching hostname already has a public route. */
    if ((ip[0] & 0xe0) != 0x20) return false;
    if (ip[0] == 0x20 && ip[1] == 1
            && ((ip[2] == 0x0d && ip[3] == 0xb8) || (!ip[2] && ip[3] < 0x20))) return false;
    return true;
}

void access_set_observer(access_observer_fn observer)
{
    pthread_mutex_lock(&access_lock);
    observer_callback = observer;
    pthread_mutex_unlock(&access_lock);
}

void access_set_epoch(uint64_t epoch)
{
    pthread_mutex_lock(&access_lock);
    current_epoch = epoch;
    memset(routes, 0, sizeof(routes));
    pthread_mutex_unlock(&access_lock);
}

uint64_t access_epoch(void)
{
    pthread_mutex_lock(&access_lock);
    uint64_t epoch = current_epoch;
    pthread_mutex_unlock(&access_lock);
    return epoch;
}

bool access_update_route(uint64_t epoch, const char *host, int port,
        const char *ipv4, int ttl_seconds)
{
    char normalized[ACCESS_HOST_MAX + 1];
    struct in_addr address;
    if (!normalize_host(host, normalized) || port != 443) return false;
    if (ipv4 && (ttl_seconds < 1 || ttl_seconds > 3600
            || inet_pton(AF_INET, ipv4, &address) != 1 || !public_ipv4(&address))) return false;
    uint64_t now = monotonic_ms();
    pthread_mutex_lock(&access_lock);
    if (!epoch || epoch != current_epoch) {
        pthread_mutex_unlock(&access_lock);
        return false;
    }
    int slot = -1, free_slot = -1, oldest = 0;
    for (int i = 0; i < ACCESS_ROUTE_MAX; i++) {
        if (!strcmp(routes[i].host, normalized) && routes[i].port == port) slot = i;
        if (!routes[i].host[0] || routes[i].expires <= now) free_slot = i;
        if (routes[i].expires < routes[oldest].expires) oldest = i;
    }
    if (!ipv4) {
        if (slot >= 0) memset(&routes[slot], 0, sizeof(routes[slot]));
    } else {
        if (slot < 0) slot = free_slot >= 0 ? free_slot : oldest;
        strcpy(routes[slot].host, normalized);
        routes[slot].port = port;
        routes[slot].ip = address;
        routes[slot].expires = now + (uint64_t)ttl_seconds * 1000;
    }
    pthread_mutex_unlock(&access_lock);
    return true;
}

bool access_lookup_route(uint64_t epoch, const char *host, int port,
        union sockaddr_u *destination)
{
    char normalized[ACCESS_HOST_MAX + 1];
    if (!normalize_host(host, normalized) || port != 443) return false;
    uint64_t now = monotonic_ms();
    bool found = false;
    pthread_mutex_lock(&access_lock);
    if (epoch && epoch == current_epoch) {
        for (int i = 0; i < ACCESS_ROUTE_MAX; i++) {
            if (routes[i].expires > now && routes[i].port == port
                    && !strcmp(routes[i].host, normalized)) {
                memset(destination, 0, sizeof(*destination));
                destination->in.sin_family = AF_INET;
                destination->in.sin_port = htons(port);
                destination->in.sin_addr = routes[i].ip;
                found = true;
                break;
            }
        }
    }
    pthread_mutex_unlock(&access_lock);
    return found;
}

bool access_is_loopback(const union sockaddr_u *address)
{
    if (address->sa.sa_family == AF_INET)
        return (ntohl(address->in.sin_addr.s_addr) >> 24) == 127;
    if (address->sa.sa_family != AF_INET6) return false;
    if (IN6_IS_ADDR_LOOPBACK(&address->in6.sin6_addr)) return true;
    return IN6_IS_ADDR_V4MAPPED(&address->in6.sin6_addr)
        && address->in6.sin6_addr.s6_addr[12] == 127;
}

bool access_probe_marker(int fd, const char *greeting, size_t length)
{
    if (length < 4 || (unsigned char)greeting[0] != 5
            || (unsigned char)greeting[1] != length - 2) return false;
    bool marked = false, anonymous = false;
    for (size_t i = 2; i < length; i++) {
        marked |= (unsigned char)greeting[i] == 0x80;
        anonymous |= greeting[i] == 0;
    }
    union sockaddr_u peer;
    socklen_t size = sizeof(peer);
    return marked && anonymous && !getpeername(fd, &peer.sa, &size)
        && access_is_loopback(&peer);
}

void access_observe(uint64_t epoch, const char *host, const union sockaddr_u *original)
{
    char normalized[ACCESS_HOST_MAX + 1], ip[INET6_ADDRSTRLEN];
    if (!normalize_host(host, normalized) || !access_public_destination(original)) return;
    const void *bytes;
    int family = original->sa.sa_family;
    if (family == AF_INET) bytes = &original->in.sin_addr;
    else if (family == AF_INET6 && IN6_IS_ADDR_V4MAPPED(&original->in6.sin6_addr)) {
        bytes = &original->in6.sin6_addr.s6_addr[12]; family = AF_INET;
    } else if (family == AF_INET6) bytes = &original->in6.sin6_addr;
    else return;
    if (!inet_ntop(family, bytes, ip, sizeof(ip))) return;
    pthread_mutex_lock(&access_lock);
    access_observer_fn callback = epoch && epoch == current_epoch ? observer_callback : 0;
    pthread_mutex_unlock(&access_lock);
    /* Never call Java/the controller while holding the route mutex. */
    if (callback) callback(epoch, normalized, ip, ntohs(original->in.sin_port));
}

static size_t be16(const unsigned char *p) { return ((size_t)p[0] << 8) | p[1]; }

static bool extract_sni(const unsigned char *hello, size_t size,
        char host[ACCESS_HOST_MAX + 1])
{
    if (size < 39) return false;
    size_t p = 39 + hello[38];
    if (p + 2 > size) return false;
    p += 2 + be16(hello + p);
    if (p + 1 > size) return false;
    p += 1 + hello[p];
    if (p == size) return true; /* TLS without extensions. */
    if (p + 2 > size) return false;
    size_t end = p + 2 + be16(hello + p);
    p += 2;
    if (end != size) return false;
    bool found_sni = false, ech = false;
    while (p + 4 <= end) {
        size_t type = be16(hello + p), length = be16(hello + p + 2);
        p += 4;
        if (length > end - p) return false;
        if (type == 0xfe0d) ech = true; /* RFC 9849, including conservative GREASE. */
        if (type == 0) {
            if (found_sni) return false;
            found_sni = true;
            if (length < 2 || be16(hello + p) != length - 2) return false;
            size_t name = p + 2, names_end = p + length;
            while (name + 3 <= names_end) {
                size_t n = be16(hello + name + 1);
                unsigned char kind = hello[name];
                name += 3;
                if (n > names_end - name) return false;
                if (!kind) {
                    if (!n || n > ACCESS_HOST_MAX || memchr(hello + name, 0, n)) return false;
                    char raw[ACCESS_HOST_MAX + 1];
                    memcpy(raw, hello + name, n); raw[n] = 0;
                    if (host[0] || !normalize_host(raw, host)) return false;
                }
                name += n;
            }
            if (name != names_end) return false;
        }
        p += length;
    }
    if (ech) host[0] = 0;
    return p == end;
}

enum access_hello_status access_client_hello(const char *wire, size_t length,
        char host[ACCESS_HOST_MAX + 1])
{
    host[0] = 0;
    if (!length) return ACCESS_MORE;
    const unsigned char *data = (const unsigned char *)wire;
    if (data[0] != 0x16 || length > ACCESS_HELLO_MAX) return ACCESS_PASSTHROUGH;
    unsigned char hello[ACCESS_HELLO_MAX];
    size_t p = 0, assembled = 0, needed = 0;
    while (p < length) {
        if (length - p < 5) return ACCESS_MORE;
        if (data[p] != 0x16 || data[p + 1] != 3) return ACCESS_PASSTHROUGH;
        size_t record = be16(data + p + 3);
        if (!record || record > ACCESS_HELLO_MAX - 5) return ACCESS_PASSTHROUGH;
        size_t available = length - p - 5;
        if (available > record) available = record;
        if (available > sizeof(hello) - assembled) return ACCESS_PASSTHROUGH;
        memcpy(hello + assembled, data + p + 5, available);
        assembled += available;
        if (assembled >= 4 && !needed) {
            if (hello[0] != 1) return ACCESS_PASSTHROUGH;
            needed = 4 + ((size_t)hello[1] << 16) + ((size_t)hello[2] << 8) + hello[3];
            if (needed > sizeof(hello) - 5 || needed < 39) return ACCESS_PASSTHROUGH;
        }
        if (needed && assembled >= needed)
            return extract_sni(hello, needed, host) ? ACCESS_READY : ACCESS_PASSTHROUGH;
        if (available < record) return ACCESS_MORE;
        p += record + 5;
    }
    return length == ACCESS_HELLO_MAX ? ACCESS_PASSTHROUGH : ACCESS_MORE;
}

bool access_tls_replayable(const char *wire, size_t length)
{
    char host[ACCESS_HOST_MAX + 1];
    if (access_client_hello(wire, length, host) != ACCESS_READY) return false;
    const unsigned char *data = (const unsigned char *)wire;
    size_t p = 0;
    while (p < length) {
        if (length - p < 5 || data[p + 1] != 3) return false;
        size_t record = be16(data + p + 3);
        if (!record || record > length - p - 5) return false;
        /* ClientHello/handshake and compatibility CCS may be replayed before
         * application data; TLS application records (including 0-RTT) may not. */
        if (data[p] != 0x16 && !(data[p] == 0x14 && record == 1 && data[p + 5] == 1))
            return false;
        p += record + 5;
    }
    return p == length;
}
