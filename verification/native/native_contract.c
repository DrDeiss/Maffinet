/* Test fixture around the pinned, unchanged native parser and group selector. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* Including the production translation unit exposes its static find_dp and
 * check functions to this fixture without rewriting their implementation. */
#include "extend.c"

extern int parse_args(int argc, char **argv);
extern void clear_params(char *line, char **argv);

int main(int argc, char **argv)
{
    if (argc < 5) return 2;
    const char *mode = argv[1], *host = argv[2];
    if (parse_args(argc - 3, argv + 3)) return 3;

    union sockaddr_u destination = { 0 };
    destination.in.sin_family = AF_INET;
    destination.in.sin_port = htons(443);
    inet_pton(AF_INET, "192.0.2.1", &destination.in.sin_addr);
    struct desync_params *selected = 0;

    if (!strcmp(mode, "udp")) {
        /* Exercise udp_hook itself while sending only to a local socketpair. */
        int local[2];
        if (socketpair(AF_UNIX, SOCK_DGRAM, 0, local)) return 4;
        struct eval client = { .round_count = 1 }, link = { .pair = &client };
        struct eval remote = { .fd = local[0], .pair = &link };
        char payload[] = "ordinary datagram";
        ssize_t length = sizeof(payload) - 1;
        if (udp_hook(&remote, payload, length, &destination) != length) return 5;
        char received[64];
        if (recv(local[1], received, sizeof(received), MSG_DONTWAIT) != length ||
            memcmp(received, payload, length)) return 6;
        if (recv(local[1], received, sizeof(received), MSG_DONTWAIT) >= 0) return 7;
        selected = client.dp;
        close(local[0]);
        close(local[1]);
    } else {
        char packet[2048];
        ssize_t length;
        if (strstr(mode, "tls")) {
            length = sizeof(tls_data);
            memcpy(packet, tls_data, length);
            if (change_tls_sni(host, packet, length, length)) return 8;
        } else if (!strcmp(mode, "opaque")) {
            memcpy(packet, "opaque binary data", 18);
            length = 18;
        } else {
            length = snprintf(packet, sizeof(packet),
                "GET / HTTP/1.1\r\nHost: %s\r\n\r\n", host);
        }
        struct eval client = { .dp = params.dp };
        if (!strcmp(mode, "retry-tls")) {
            client.dp_mask = params.dp->bit;
            client.dp = params.dp->next;
            client.detect = DETECT_TORST;
        }
        selected = find_dp(&client, packet, length, &destination);
    }
    if (!selected) return 9;
    printf("contract %d %d %d %d\n", selected->id, selected->parts_n,
        selected->udp_fake_count, params.dp_n);
    clear_params(0, 0);
    return 0;
}
