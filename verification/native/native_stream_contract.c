/* Linux socket regression for the production tunnel, timers and TLS rewriting.
 * No external network, privileged sockets, TLS server or phone is required.
 * send faults are injected only on the proxy's outbound TCP fd; successful
 * writes still pass through the real loopback socket and production event loop.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <signal.h>
#include <fcntl.h>
#include <sys/select.h>

#include "extend.c"

extern int parse_args(int argc, char **argv);
extern int init(void);
extern void clear_params(char *line, char **argv);
extern struct eval *next_event_tv(struct poolhd *, int *, int *);
extern ssize_t __real_send(int, const void *, size_t, int);

#define REQUIRE(exp) do { if (!(exp)) { \
    fprintf(stderr, "stream contract failed at %s:%d: %s\n", __FILE__, __LINE__, #exp); \
    exit(1); } } while (0)

static int fault_fd = -1, fault_kind, fault_count, zero_sends, timer_events;

ssize_t __wrap_send(int fd, const void *data, size_t length, int flags)
{
    if (fd == fault_fd) {
        if (!length) zero_sends++;
        if (length && fault_kind) {
            int kind = fault_kind;
            fault_kind = 0;
            fault_count++;
            if (kind == 1) { errno = EAGAIN; return -1; }
            if (length > 73) length = 73;
        }
    }
    return __real_send(fd, data, length, flags);
}

static void ready(int fd, int writing)
{
    fd_set set;
    FD_ZERO(&set);
    FD_SET(fd, &set);
    struct timeval timeout = { .tv_sec = 2 };
    REQUIRE(select(fd + 1, writing ? 0 : &set, writing ? &set : 0, 0, &timeout) == 1);
}

static void write_all(int fd, const void *data, size_t length)
{
    const char *bytes = data;
    while (length) {
        ssize_t n = __real_send(fd, bytes, length, 0);
        if (n < 0 && errno == EAGAIN) { ready(fd, 1); continue; }
        REQUIRE(n > 0);
        bytes += n;
        length -= n;
    }
}

static void read_exact(int fd, void *data, size_t length)
{
    char *bytes = data;
    while (length) {
        ready(fd, 0);
        ssize_t n = recv(fd, bytes, length, 0);
        REQUIRE(n > 0);
        bytes += n;
        length -= n;
    }
}

static int listener(union sockaddr_u *address)
{
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(fd > 0);
    memset(address, 0, sizeof(*address));
    address->in.sin_family = AF_INET;
    address->in.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    REQUIRE(bind(fd, &address->sa, sizeof(address->in)) == 0);
    REQUIRE(listen(fd, 4) == 0);
    socklen_t size = sizeof(address->in);
    REQUIRE(getsockname(fd, &address->sa, &size) == 0);
    return fd;
}

static void nonblocking(int fd)
{
    REQUIRE(fcntl(fd, F_SETFL, fcntl(fd, F_GETFL) | O_NONBLOCK) == 0);
    int one = 1;
    REQUIRE(setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof(one)) == 0);
}

static int accept_local(int fd)
{
    ready(fd, 0);
    int result = accept(fd, 0, 0);
    REQUIRE(result > 0);
    nonblocking(result);
    return result;
}

static void channel(int *writer, int *reader, int *listen_fd, union sockaddr_u *address)
{
    *listen_fd = listener(address);
    *writer = socket(AF_INET, SOCK_STREAM, 0);
    REQUIRE(*writer > 0);
    REQUIRE(connect(*writer, &address->sa, sizeof(address->in)) == 0);
    *reader = accept_local(*listen_fd);
    nonblocking(*writer);
}

struct tunnel {
    struct poolhd *pool;
    struct eval *client;
    int tester, backend, backend_listener;
};

static struct tunnel tunnel_open(void)
{
    struct tunnel t = {0};
    int client_fd, ignored_listener, remote_fd;
    union sockaddr_u client_addr, backend_addr;
    channel(&t.tester, &client_fd, &ignored_listener, &client_addr);
    close(ignored_listener);
    channel(&remote_fd, &t.backend, &t.backend_listener, &backend_addr);
    t.pool = init_pool(8);
    REQUIRE(t.pool != 0);
    t.client = add_event(t.pool, &on_tunnel, client_fd, POLLIN);
    struct eval *remote = add_event(t.pool, &on_tunnel, remote_fd, POLLIN);
    REQUIRE(t.client && remote);
    t.client->pair = remote;
    remote->pair = t.client;
    t.client->flag = FLAG_S5;
    remote->flag = FLAG_CONN;
    remote->addr = backend_addr;
    t.client->dp = params.dp;
    fault_fd = remote_fd;
    return t;
}

static void tunnel_close(struct tunnel *t)
{
    fault_fd = -1;
    destroy_pool(t->pool);
    close(t->tester);
    close(t->backend);
    close(t->backend_listener);
}

static void drive_pending(struct tunnel *t)
{
    int offset = -1, type = -1, callbacks = 0;
    while (t->client->buff) {
        REQUIRE(++callbacks < 100);
        struct eval *ev = next_event_tv(t->pool, &offset, &type);
        REQUIRE(ev != 0);
        if (type == POLLTIMEOUT) timer_events++;
        REQUIRE(ev->cb(t->pool, ev, type) == 0);
    }
}

static void put16(unsigned char *p, size_t n)
{
    p[0] = n >> 8;
    p[1] = n;
}

/* A well-framed large ClientHello with SNI at a chosen offset. Its cipher/key
 * bytes need not negotiate TLS: this test checks the exact forwarded handshake
 * bytes and record framing, rather than a second TLS implementation's behavior.
 */
static void hello(unsigned char *data, size_t length, size_t host_pos, const char *host)
{
    REQUIRE(length >= 1500 && host_pos >= 99);
    memset(data, 0, length);
    data[0] = 0x16; data[1] = 3; data[2] = 3;
    put16(data + 3, length - 5);
    data[5] = 1;
    size_t handshake = length - 9;
    data[6] = handshake >> 16; data[7] = handshake >> 8; data[8] = handshake;
    data[9] = 3; data[10] = 3;
    data[43] = 32; /* Session ID, followed by four cipher-suite bytes. */
    put16(data + 76, 4);
    data[78] = 0x13; data[79] = 1; data[80] = 0x13; data[81] = 2;
    data[82] = 1; data[83] = 0;
    put16(data + 84, length - 86);
    size_t filler = host_pos - 99;
    put16(data + 86, 0x2a2a); put16(data + 88, filler);
    size_t ext = 90 + filler, host_len = strlen(host);
    REQUIRE(ext + 9 + host_len + 4 <= length);
    put16(data + ext, 0); put16(data + ext + 2, host_len + 5);
    put16(data + ext + 4, host_len + 3); data[ext + 6] = 0;
    put16(data + ext + 7, host_len);
    memcpy(data + ext + 9, host, host_len);
    size_t padding = ext + 9 + host_len;
    put16(data + padding, 0x15); put16(data + padding + 2, length - padding - 4);
    char *parsed = 0;
    REQUIRE(parse_tls((char *)data, length, &parsed) == (int)host_len);
    REQUIRE(parsed == (char *)data + host_pos);
}

static void check_records(const unsigned char *wire, size_t wire_size,
        const unsigned char *original, size_t original_size, int expected_records)
{
    unsigned char payload[16384];
    size_t offset = 0, written = 0;
    int records = 0;
    while (offset < wire_size) {
        REQUIRE(wire_size - offset >= 5);
        REQUIRE(wire[offset] == 0x16 && wire[offset + 1] == 3);
        size_t length = ((size_t)wire[offset + 3] << 8) | wire[offset + 4];
        REQUIRE(length > 0 && length <= wire_size - offset - 5);
        REQUIRE(written + length <= sizeof(payload));
        memcpy(payload + written, wire + offset + 5, length);
        written += length;
        offset += length + 5;
        records++;
    }
    REQUIRE(records == expected_records);
    REQUIRE(written == original_size - 5);
    REQUIRE(!memcmp(payload, original + 5, written));
}

static void receive_hello(struct tunnel *t, const unsigned char *original,
        size_t original_size, int records)
{
    unsigned char wire[16384];
    size_t size = original_size + (records - 1) * 5;
    read_exact(t->backend, wire, size);
    check_records(wire, size, original, original_size, records);
    REQUIRE(recv(t->backend, wire, sizeof(wire), MSG_DONTWAIT) < 0 && errno == EAGAIN);
}

static void first_read(struct tunnel *t, const void *data, size_t length)
{
    write_all(t->tester, data, length);
    ready(t->client->fd, 0);
    REQUIRE(on_tunnel(t->pool, t->client, POLLIN) == 0);
}

static void early_response(struct tunnel *t)
{
    const unsigned char response[] = {0x16, 3, 3, 0, 1, 2};
    unsigned char received[sizeof(response)];
    REQUIRE(t->client->buff != 0);
    write_all(t->backend, response, sizeof(response));
    ready(t->client->pair->fd, 0);
    /* Explicitly replay the queued read-before-pacing-completion ordering from
     * the phone log; the bytes and callbacks are real TCP/production code. */
    REQUIRE(on_tunnel(t->pool, t->client->pair, POLLIN) == 0);
    read_exact(t->tester, received, sizeof(received));
    REQUIRE(!memcmp(received, response, sizeof(response)));
}

static void encrypted_tail(struct tunnel *t)
{
    const unsigned char tail[] = {0x17, 3, 3, 0, 4, 0x55, 0x66, 0x77, 0x88};
    unsigned char received[sizeof(tail)];
    REQUIRE(t->client->round_sent == 0);
    first_read(t, tail, sizeof(tail));
    REQUIRE(t->client->round_count == 2);
    REQUIRE(t->client->buff == 0);
    read_exact(t->backend, received, sizeof(received));
    REQUIRE(!memcmp(received, tail, sizeof(tail)));
}

static void check_scope(void)
{
    REQUIRE(params.dp_n >= 2);
    REQUIRE(params.dp->group_pacing == 20 && params.dp->next->group_pacing == 0);
    REQUIRE(params.dp->out_type == MODE_TCP && params.dp->next->out_type == 0);
    REQUIRE(params.dp->proto == (IS_TCP | IS_HTTPS) && params.dp->next->proto == 0);
    REQUIRE(params.dp->pf[0] == htons(443) && params.dp->next->pf[0] == 0);
    REQUIRE(params.dp->rounds[0] == 1 && params.dp->rounds[1] == 1);
    REQUIRE(params.dp->next->rounds[0] == 0 && params.dp->next->rounds[1] == 0);
    REQUIRE(!params.wait_send && params.await_int == 10 && !params.delay_conn);
}

static void check_legacy_scope(void)
{
    REQUIRE(params.wait_send && params.await_int == 7 && params.delay_conn);
    REQUIRE(params.dp->out_type == MODE_TCP && params.dp->next->out_type == 0);
    REQUIRE(params.dp->group_pacing == 0 && params.dp->next->group_pacing == 0);
}

static void server_first(void)
{
    check_scope();
    struct tunnel t = tunnel_open();
    struct eval *old = t.client->pair;
    union sockaddr_u destination = old->addr;
    old->pair = 0;
    t.client->pair = 0;
    del_event(t.pool, old);
    /* This is a normal filtered group on a port outside the redirect's -V443.
     * No client bytes are sent, as with SSH and other server-first protocols. */
    REQUIRE(ntohs(destination.in.sin_port) != 443);
    REQUIRE(connect_hook(t.pool, t.client, &destination, &on_connect) == 0);
    REQUIRE(t.client->pair != 0);
    REQUIRE(t.client->dp == params.dp->next);
    close(t.backend);
    t.backend = accept_local(t.backend_listener);
    struct eval *remote = t.client->pair;
    ready(remote->fd, 1);
    REQUIRE(on_connect(t.pool, remote, POLLOUT) == 0);
    unsigned char reply[10];
    read_exact(t.tester, reply, sizeof(reply));
    REQUIRE(reply[0] == 5 && reply[1] == 0);
    const char banner[] = "SSH-2.0-local-fixture\r\n";
    char copied[sizeof(banner)];
    write_all(t.backend, banner, sizeof(banner));
    ready(remote->fd, 0);
    REQUIRE(on_tunnel(t.pool, remote, POLLIN) == 0);
    read_exact(t.tester, copied, sizeof(copied));
    REQUIRE(!memcmp(copied, banner, sizeof(banner)));
    tunnel_close(&t);
}

int main(int argc, char **argv)
{
    REQUIRE(argc >= 3);
    alarm(10);
    signal(SIGPIPE, SIG_IGN);
    const char *mode = argv[1];
    REQUIRE(parse_args(argc - 2, argv + 2) == 0);
    REQUIRE(init() == 0);
    if (!strcmp(mode, "parse-only")) { /* A successful parse is sufficient. */ }
    else if (!strcmp(mode, "scope")) { check_scope(); }
    else if (!strcmp(mode, "legacy-scope")) { check_legacy_scope(); }
    else if (!strcmp(mode, "server-first")) { server_first(); }
    else {
        int partial = !strcmp(mode, "partial");
        int fallback = !strcmp(mode, "fallback");
        int replay = !strcmp(mode, "replay");
        int race = !strcmp(mode, "race") || !strcmp(mode, "legacy-race")
            || !strcmp(mode, "disorder");
        size_t length = partial ? 2020 : 1796;
        unsigned char original[2020];
        hello(original, length, partial ? 125 : 1420,
            fallback ? "www.example.org" : "www.linkedin.com");
        if (replay) params.auto_reconnect = true;
        struct tunnel t = tunnel_open();
        if (!strcmp(mode, "again")) fault_kind = 1;
        if (!strcmp(mode, "part-short") || !strcmp(mode, "rest-short")) fault_kind = 2;
        first_read(&t, original, partial ? 1732 : length);
        if (race) early_response(&t);
        if (replay) {
            REQUIRE(t.client->buff && t.client->sq_buff);
            REQUIRE(reconnect(t.pool, t.client->pair) == 0);
            close(t.backend);
            t.backend = accept_local(t.backend_listener);
            fault_fd = t.client->pair->fd;
        }
        drive_pending(&t);
        if (partial) {
            first_read(&t, original + 1732, length - 1732);
            drive_pending(&t);
        }
        receive_hello(&t, original, length, fallback ? 1 : 2);
        if (race) encrypted_tail(&t);
        REQUIRE(zero_sends == 0);
        if (!strcmp(mode, "again") || !strcmp(mode, "part-short") || !strcmp(mode, "rest-short"))
            REQUIRE(fault_count == 1);
        if (race || replay || partial || !strcmp(mode, "part-short")) REQUIRE(timer_events > 0);
        if (fallback) REQUIRE(timer_events == 0 && t.client->dp == params.dp->next);
        tunnel_close(&t);
    }
    clear_params(0, 0);
    printf("native stream contract: %s passed (timers=%d, injected faults=%d, zero sends=%d)\n",
        mode, timer_events, fault_count, zero_sends);
    return 0;
}
