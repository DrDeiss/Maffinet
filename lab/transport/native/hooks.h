#ifndef MAFFINET_LAB_HOOKS_H
#define MAFFINET_LAB_HOOKS_H
#include <lwip/ip_addr.h>
int maffinet_lab_protect_loopback(int fd);
void maffinet_lab_tuple(int protocol, const ip_addr_t *local, unsigned short local_port,
                       const ip_addr_t *remote, unsigned short remote_port);
void maffinet_lab_ready(void);
void maffinet_lab_stopping(void);
int maffinet_lab_run(const unsigned char *config, unsigned int length, int tun_fd);
#endif
