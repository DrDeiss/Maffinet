/* Adapted from HEV hev-main.c at 4d6c334dbfb68a79d1970c2744e62d09f71df12f.
 * Copyright (c) 2019 - 2023 hev. MIT; see ../licenses/hev-tunnel-LICENSE.
 * Maffinet P01: unwind acquired resources on each initialization failure.
 */
#include <lwip/init.h>
#include <hev-task-system.h>
#include "hev-socks5-misc.h"
#include "hev-socks5-logger.h"
#include "hev-config.h"
#include "hev-logger.h"
#include "hev-socks5-tunnel.h"
#include "hooks.h"

void hev_socks5_tunnel_quit(void) { hev_socks5_tunnel_stop(); }

int maffinet_lab_run(const unsigned char *config, unsigned int length, int fd)
{
    int result, logger = 0, socks_logger = 0, tasks = 0;
    result = hev_config_init_from_str(config, length);
    if (result < 0) return -1;
    hev_socks5_set_connect_timeout(hev_config_get_misc_connect_timeout());
    hev_socks5_set_tcp_timeout(hev_config_get_misc_tcp_read_write_timeout());
    hev_socks5_set_udp_timeout(hev_config_get_misc_udp_read_write_timeout());
    hev_socks5_set_udp_recv_buffer_size(hev_config_get_misc_udp_recv_buffer_size());
    result = -2;
    if (hev_logger_init(hev_config_get_misc_log_level(), hev_config_get_misc_log_file()) < 0) goto done;
    logger = 1;
    result = -3;
    if (hev_socks5_logger_init(hev_config_get_misc_log_level(), hev_config_get_misc_log_file()) < 0) goto done;
    socks_logger = 1;
    result = -4;
    if (hev_task_system_init() < 0) goto done;
    tasks = 1;
    lwip_init();
    result = -5;
    /* HEV init already calls fini for its own partial failure. */
    if (hev_socks5_tunnel_init(fd) < 0) goto done;
    result = hev_socks5_tunnel_run();
    hev_socks5_tunnel_fini();
done:
    if (tasks) hev_task_system_fini();
    if (socks_logger) hev_socks5_logger_fini();
    if (logger) hev_logger_fini();
    hev_config_fini();
    return result;
}
