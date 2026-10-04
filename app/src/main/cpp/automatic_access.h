#ifndef MAFFINET_AUTOMATIC_ACCESS_H
#define MAFFINET_AUTOMATIC_ACCESS_H

#include <stddef.h>
#include <stdint.h>
#include <stdbool.h>
#include "params.h"

#define ACCESS_HOST_MAX 253
#define ACCESS_HELLO_MAX 16384
#define ACCESS_HELLO_WAIT_MS 2000
#define ACCESS_RESPONSE_WAIT_MS 2000
#define ACCESS_ROUTE_MAX 128

typedef void (*access_observer_fn)(uint64_t epoch, const char *host,
        const char *original_ip, int port);

void access_set_observer(access_observer_fn observer);
void access_set_epoch(uint64_t epoch);
uint64_t access_epoch(void);
bool access_update_route(uint64_t epoch, const char *host, int port,
        const char *ipv4, int ttl_seconds);
bool access_lookup_route(uint64_t epoch, const char *host, int port,
        union sockaddr_u *destination);
void access_observe(uint64_t epoch, const char *host, const union sockaddr_u *original);
bool access_is_loopback(const union sockaddr_u *address);
bool access_public_destination(const union sockaddr_u *address);
bool access_probe_marker(int fd, const char *greeting, size_t length);

enum access_hello_status { ACCESS_MORE, ACCESS_READY, ACCESS_PASSTHROUGH };
enum access_hello_status access_client_hello(const char *wire, size_t length,
        char host[ACCESS_HOST_MAX + 1]);
bool access_tls_replayable(const char *wire, size_t length);

#endif
