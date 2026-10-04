/* Automatic Access uses real SOCKS greetings, loopback TCP destinations and
 * production connect/tunnel callbacks. Only port 443 is translated by connect
 * to unprivileged local fixture ports; no packet leaves the host. */
#define main stream_fixture_unused_main
#include "native_stream_contract.c"
#undef main

extern int __real_connect(int, const struct sockaddr *, socklen_t);
static int original_port, route_port, connect_count, selected_address;
static bool test_connections;
static int observed;
static char observed_host[ACCESS_HOST_MAX + 1];

int __wrap_connect(int fd, const struct sockaddr *address, socklen_t length)
{
    union sockaddr_u copy = {0};
    REQUIRE(length <= sizeof(copy));
    memcpy(&copy, address, length);
    if (test_connections) {
        connect_count++;
        if (copy.sa.sa_family == AF_INET) selected_address = ntohl(copy.in.sin_addr.s_addr) & 255;
        else {
            REQUIRE(IN6_IS_ADDR_V4MAPPED(&copy.in6.sin6_addr));
            selected_address = copy.in6.sin6_addr.s6_addr[15];
        }
        if (ntohs(copy.in.sin_port) == 443) {
            REQUIRE(selected_address == 34 || selected_address == 35 || selected_address == 1);
            copy.in.sin_port = htons(selected_address == 35 ? route_port : original_port);
        }
        /* Exercise production public-address guards; translate only inside
         * the test's syscall boundary to actual loopback fixture sockets. */
        if (selected_address == 34 || selected_address == 35) {
            struct in_addr loopback;
            inet_pton(AF_INET, selected_address == 35 ? "127.0.0.2" : "127.0.0.1", &loopback);
            if (copy.sa.sa_family == AF_INET) copy.in.sin_addr = loopback;
            else memcpy(copy.in6.sin6_addr.s6_addr + 12, &loopback, sizeof(loopback));
        }
    }
    return __real_connect(fd, &copy.sa, length);
}

static void observe(uint64_t epoch, const char *host, const char *ip, int port)
{
    REQUIRE(epoch == 77 && port == 443 && !strcmp(ip, "93.184.216.34"));
    observed++;
    strcpy(observed_host, host);
    /* Reentrant update proves that observer dispatch does not hold route lock.
     * A new route from this notification must affect only later connections. */
    REQUIRE(access_update_route(epoch, "observer-lock.example", 443, "93.184.216.35", 10));
}

static int route_listener(void)
{
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(fd > 0);
    struct sockaddr_in addr = {.sin_family = AF_INET};
    REQUIRE(inet_pton(AF_INET, "127.0.0.2", &addr.sin_addr) == 1);
    REQUIRE(bind(fd, (struct sockaddr *)&addr, sizeof(addr)) == 0 && listen(fd, 4) == 0);
    socklen_t size = sizeof(addr);
    REQUIRE(getsockname(fd, (struct sockaddr *)&addr, &size) == 0);
    route_port = ntohs(addr.sin_port);
    return fd;
}

static struct tunnel pending_socks(bool probe, bool other_port, bool private_original)
{
    struct tunnel t = tunnel_open();
    struct eval *old = t.client->pair;
    union sockaddr_u original = old->addr;
    original_port = ntohs(original.in.sin_port);
    old->pair = 0; t.client->pair = 0;
    del_event(t.pool, old);
    close(t.backend); t.backend = -1;
    fault_fd = -1;
    t.client->dp = 0;
    t.client->flag = 0;
    t.client->cb = &on_request;
    test_connections = true;
    const unsigned char greeting[] = {5, 2, 0, 0x80};
    const unsigned char ordinary[] = {5, 1, 0};
    write_all(t.tester, probe ? greeting : ordinary, probe ? sizeof(greeting) : sizeof(ordinary));
    ready(t.client->fd, 0);
    REQUIRE(on_request(t.pool, t.client, POLLIN) == 0);
    unsigned char auth[2]; read_exact(t.tester, auth, sizeof(auth));
    REQUIRE(auth[0] == 5 && auth[1] == 0 && t.client->auto_probe == probe);
    unsigned char request[] = {5, 1, 0, 1, 93, 184, 216, 34, 1, 0xbb};
    if (private_original) { request[4] = 127; request[5] = request[6] = 0; request[7] = 1; }
    if (other_port) put16(request + 8, original_port);
    write_all(t.tester, request, sizeof(request));
    ready(t.client->fd, 0);
    REQUIRE(on_request(t.pool, t.client, POLLIN) == 0);
    if (!other_port) {
        unsigned char reply[10]; read_exact(t.tester, reply, sizeof(reply));
        REQUIRE(reply[0] == 5 && reply[1] == 0);
        REQUIRE(!t.client->pair && t.client->buff && t.client->tv_ms);
        REQUIRE(connect_count == 0);
    }
    return t;
}

static void collect(struct tunnel *t, const void *data, size_t size)
{
    write_all(t->tester, data, size);
    ready(t->client->fd, 0);
    REQUIRE(t->client->cb(t->pool, t->client, POLLIN) == 0);
}

static void expire_collection(struct tunnel *t)
{
    int offset = -1, type = -1;
    struct eval *ev = next_event_tv(t->pool, &offset, &type);
    REQUIRE(ev == t->client && type == POLLTIMEOUT);
    timer_events++;
    REQUIRE(ev->cb(t->pool, ev, type) == 0);
}

static void server_reply(struct tunnel *t)
{
    const unsigned char response[] = {0x16, 3, 3, 0, 1, 2};
    unsigned char received[sizeof(response)];
    write_all(t->backend, response, sizeof(response));
    ready(t->client->pair->fd, 0);
    REQUIRE(on_tunnel(t->pool, t->client->pair, POLLIN) == 0);
    read_exact(t->tester, received, sizeof(received));
    REQUIRE(!memcmp(response, received, sizeof(response)));
}

static void map_contract(void)
{
    union sockaddr_u result = {0}, public_peer = {0};
    public_peer.in.sin_family = AF_INET;
    inet_pton(AF_INET, "192.0.2.1", &public_peer.in.sin_addr);
    REQUIRE(!access_is_loopback(&public_peer));
    REQUIRE(!access_public_destination(&public_peer));
    public_peer.in6.sin6_family = AF_INET6;
    inet_pton(AF_INET6, "::ffff:127.0.0.1", &public_peer.in6.sin6_addr);
    REQUIRE(!access_public_destination(&public_peer));
    inet_pton(AF_INET6, "2606:4700::1111", &public_peer.in6.sin6_addr);
    REQUIRE(access_public_destination(&public_peer));
    inet_pton(AF_INET6, "2001:db8::1", &public_peer.in6.sin6_addr);
    REQUIRE(!access_public_destination(&public_peer));
    REQUIRE(access_update_route(77, "UNLISTED.example.", 443, "93.184.216.35", 10));
    REQUIRE(access_lookup_route(77, "unlisted.example", 443, &result));
    REQUIRE(!access_lookup_route(77, "second-shared-ip.example", 443, &result));
    REQUIRE(!access_update_route(76, "unlisted.example", 443, "93.184.216.34", 10));
    REQUIRE(!access_update_route(77, "unlisted.example", 80, "93.184.216.34", 10));
    REQUIRE(!access_update_route(77, "bad host", 443, "93.184.216.34", 10));
    REQUIRE(!access_update_route(77, "unlisted.example", 443, "invalid", 10));
    REQUIRE(!access_update_route(77, "unlisted.example", 443, "127.0.0.1", 10));
    REQUIRE(access_update_route(77, "not-present.example", 443, NULL, 0));
    access_set_epoch(78);
    REQUIRE(!access_lookup_route(77, "unlisted.example", 443, &result));
    REQUIRE(!access_lookup_route(78, "unlisted.example", 443, &result));
    REQUIRE(!access_update_route(77, "unlisted.example", 443, "93.184.216.35", 10));
    for (int i = 0; i < ACCESS_ROUTE_MAX + 10; i++) {
        char host[64]; snprintf(host, sizeof(host), "host-%d.example", i);
        REQUIRE(access_update_route(78, host, 443, "93.184.216.35", 1));
    }
    REQUIRE(access_lookup_route(78, "host-137.example", 443, &result));
    struct timespec pause = {.tv_sec = 1, .tv_nsec = 10000000};
    nanosleep(&pause, 0);
    REQUIRE(!access_lookup_route(78, "host-137.example", 443, &result));
    access_set_epoch(0);
    REQUIRE(!access_update_route(78, "host.example", 443, "93.184.216.35", 10));
}

int main(int argc, char **argv)
{
    REQUIRE(argc >= 3);
    alarm(12); signal(SIGPIPE, SIG_IGN);
    REQUIRE(parse_args(argc - 2, argv + 2) == 0 && init() == 0 && params.auto_access);
    const char *mode = argv[1];
    access_set_epoch(77); access_set_observer(&observe);
    if (!strcmp(mode, "map")) map_contract();
    else {
        bool probe = !strcmp(mode, "probe");
        bool other = !strcmp(mode, "other-port");
        bool timeout = !strcmp(mode, "timeout");
        bool oversize = !strcmp(mode, "oversize");
        bool plaintext = !strcmp(mode, "plaintext");
        bool retry = !strcmp(mode, "hello-retry");
        bool early_data = !strcmp(mode, "early-data");
        bool fragmented = !strcmp(mode, "partial");
        bool records = !strcmp(mode, "tls-records");
        bool second_host = !strcmp(mode, "shared-ip");
        bool private_original = !strcmp(mode, "private-original");
        bool ech = !strcmp(mode, "ech");
        bool empty_timeout = !strcmp(mode, "empty-timeout");
        bool upstream_error = !strcmp(mode, "upstream-error");
        bool silent_response = !strcmp(mode, "silent-response");
        bool server_eof = !strcmp(mode, "server-eof");
        bool late_application = !strcmp(mode, "late-application");
        const char *host = second_host ? "second-shared-ip.example" : "unlisted.example";
        REQUIRE(access_update_route(77, "unlisted.example", 443, "93.184.216.35", 10));
        int alternate = route_listener();
        struct tunnel t = pending_socks(probe, other, private_original);
        unsigned char original[1796], wire[4096];
        hello(original, sizeof(original), 1420, host);
        size_t sent_size = sizeof(original);
        memcpy(wire, original, sent_size);
        if (ech) {
            size_t padding = 1420 + strlen(host);
            put16(original + padding, 0xfe0d);
            memcpy(wire, original, sent_size);
        }
        if (empty_timeout) {
            expire_collection(&t);
            REQUIRE(t.client->pair && !t.client->buff && observed == 0 && selected_address == 34);
            t.backend = accept_local(t.backend_listener);
            ready(t.client->pair->fd, 1);
            REQUIRE(on_connect(t.pool, t.client->pair, POLLOUT) == 0);
            const char banner[] = "server-first after bounded wait";
            char copied[sizeof(banner)];
            write_all(t.backend, banner, sizeof(banner));
            ready(t.client->pair->fd, 0);
            REQUIRE(on_tunnel(t.pool, t.client->pair, POLLIN) == 0);
            read_exact(t.tester, copied, sizeof(copied));
            REQUIRE(!memcmp(banner, copied, sizeof(banner)));
            REQUIRE(recv(t.tester, copied, sizeof(copied), MSG_DONTWAIT) < 0 && errno == EAGAIN);
        } else if (other) {
            REQUIRE(t.client->pair && !t.client->tv_ms && !t.client->buff);
            t.backend = accept_local(t.backend_listener);
            ready(t.client->pair->fd, 1);
            REQUIRE(on_connect(t.pool, t.client->pair, POLLOUT) == 0);
            unsigned char reply[10]; read_exact(t.tester, reply, sizeof(reply));
            REQUIRE(reply[0] == 5 && reply[1] == 0);
            const char banner[] = "server-first banner";
            char copied[sizeof(banner)];
            write_all(t.backend, banner, sizeof(banner));
            ready(t.client->pair->fd, 0);
            REQUIRE(on_tunnel(t.pool, t.client->pair, POLLIN) == 0);
            read_exact(t.tester, copied, sizeof(copied));
            REQUIRE(!memcmp(banner, copied, sizeof(banner)) && observed == 0);
        } else {
            if (records) {
                put16(wire + 3, 700);
                memmove(wire + 710, original + 705, sizeof(original) - 705);
                wire[705] = 0x16; wire[706] = 3; wire[707] = 3;
                put16(wire + 708, sizeof(original) - 705);
                sent_size += 5;
            }
            if (early_data) {
                const unsigned char app[] = {0x17, 3, 3, 0, 4, 1, 2, 3, 4};
                memcpy(wire + sent_size, app, sizeof(app)); sent_size += sizeof(app);
            }
            if (plaintext) {
                const char post[] = "POST /submit HTTP/1.1\r\nHost: unlisted.example\r\nContent-Length: 4\r\n\r\nonce";
                sent_size = sizeof(post) - 1; memcpy(wire, post, sent_size);
            }
            if (oversize) { sent_size = 5; wire[3] = 0x7f; wire[4] = 0xff; }
            if (fragmented || timeout) {
                collect(&t, wire, 100);
                REQUIRE(!t.client->pair && t.client->buff->lock == 100 && observed == 0);
                if (timeout) { expire_collection(&t); sent_size = 100; }
                else collect(&t, wire + 100, sent_size - 100);
            } else collect(&t, wire, sent_size);
            bool routed = !probe && !timeout && !oversize && !plaintext && !second_host && !private_original && !ech;
            REQUIRE(t.client->pair && selected_address == (routed ? 35 : private_original ? 1 : 34));
            REQUIRE(observed == ((!probe && !timeout && !oversize && !plaintext && !private_original && !ech) ? 1 : 0));
            if (observed) REQUIRE(!strcmp(observed_host, host));
            t.backend = accept_local(routed ? alternate : t.backend_listener);
            if (upstream_error) {
                free_first_req(t.pool, t.client);
                REQUIRE(on_connect(t.pool, t.client->pair, POLLERR) == -1);
                unsigned char extra[32];
                REQUIRE(recv(t.tester, extra, sizeof(extra), MSG_DONTWAIT) < 0 && errno == EAGAIN);
                tunnel_close(&t); close(alternate);
                goto done;
            }
            drive_pending(&t);
            if (plaintext || timeout || oversize) {
                unsigned char copied[4096]; read_exact(t.backend, copied, sent_size);
                REQUIRE(!memcmp(copied, wire, sent_size) && t.client->auto_passthrough);
            } else {
                if (early_data) {
                    unsigned char handshake[sizeof(original) + 5];
                    read_exact(t.backend, handshake, sizeof(handshake));
                    check_records(handshake, sizeof(handshake), original, sizeof(original), 2);
                    unsigned char copied[9]; read_exact(t.backend, copied, sizeof(copied));
                    REQUIRE(!memcmp(copied, wire + sizeof(original), sizeof(copied)));
                } else receive_hello(&t, original, sizeof(original), private_original ? 1 : records ? 3 : 2);
            }
            union sockaddr_u key = t.client->pair->addr;
            REQUIRE(cache_get(&key) == 0);
            if (plaintext || early_data) {
                REQUIRE(!t.client->pair->auto_wait_response);
                int before = connect_count;
                REQUIRE(on_trigger(DETECT_TORST, t.pool, t.client->pair, true) == -1);
                REQUIRE(connect_count == before && cache_get(&key) == 0);
            } else if (retry || silent_response || server_eof) {
                REQUIRE(t.client->pair->auto_wait_response && t.client->pair->tv_ms);
                if (silent_response) {
                    /* Backend has read and TCP-ACKed every ClientHello byte,
                     * but sends no TLS response. Only the production timer
                     * may advance to the next strategy. */
                    int offset = -1, type = -1;
                    struct eval *ev = next_event_tv(t.pool, &offset, &type);
                    REQUIRE(ev == t.client->pair && type == POLLTIMEOUT);
                    REQUIRE(ev->cb(t.pool, ev, type) == 0);
                } else if (server_eof) {
                    close(t.backend); t.backend = -1;
                    ready(t.client->pair->fd, 0);
                    REQUIRE(on_tunnel(t.pool, t.client->pair, POLLIN) == 0);
                } else REQUIRE(on_trigger(DETECT_TORST, t.pool, t.client->pair, true) == 0);
                close(t.backend);
                t.backend = accept_local(routed ? alternate : t.backend_listener);
                drive_pending(&t);
                receive_hello(&t, original, sizeof(original), 1);
                REQUIRE(observed == 1 && cache_get(&key) == 0 && connect_count == 2);
            } else if (late_application) {
                REQUIRE(t.client->pair->auto_wait_response && t.client->pair->tv_ms);
                const unsigned char app[] = {0x17, 3, 3, 0, 4, 1, 2, 3, 4};
                first_read(&t, app, sizeof(app));
                drive_pending(&t);
                unsigned char copied[sizeof(app)]; read_exact(t.backend, copied, sizeof(copied));
                REQUIRE(!memcmp(copied, app, sizeof(app)));
                REQUIRE(!t.client->pair->auto_wait_response && !t.client->pair->tv_ms);
                int before = connect_count;
                REQUIRE(on_trigger(DETECT_TORST, t.pool, t.client->pair, true) == -1);
                REQUIRE(connect_count == before);
            } else if (!timeout && !oversize) {
                /* Replacing/clearing the learned route cannot move an existing
                 * TLS connection or replay its encrypted application bytes. */
                int before = connect_count;
                REQUIRE(access_update_route(77, host, 443, NULL, 0));
                server_reply(&t);
                encrypted_tail(&t);
                REQUIRE(connect_count == before);
            }
        }
        tunnel_close(&t); close(alternate);
    }
done:
    access_set_observer(NULL); access_set_epoch(0);
    clear_params(0, 0);
    printf("native automatic access contract: %s passed (observations=%d, connects=%d)\n",
        mode, observed, connect_count);
    return 0;
}
