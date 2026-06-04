#ifndef NETIPC_PROTOCOL_INTERNAL_H
#define NETIPC_PROTOCOL_INTERNAL_H

#include "netipc/netipc_protocol.h"

#include <stddef.h>
#include <string.h>

/*
 * Safe multiplication check: returns true if count * entry_size would
 * overflow size_t. Portable across 32-bit and 64-bit without triggering
 * -Wtype-limits.
 */
static inline bool mul_would_overflow(size_t count, size_t entry_size)
{
    return entry_size != 0 && count > SIZE_MAX / entry_size;
}

/* Cgroups item wire header (internal, 32 bytes) */
typedef struct {
    uint16_t layout_version;
    uint16_t flags;
    uint32_t hash;
    uint32_t options;
    uint32_t enabled;
    uint32_t name_offset;
    uint32_t name_length;
    uint32_t path_offset;
    uint32_t path_length;
} nipc_cgroups_item_wire_t;

typedef struct {
    uint16_t layout_version;
    uint16_t flags;
    uint32_t item_count;
    uint32_t reserved0;
    uint32_t reserved1;
} nipc_lookup_req_header_wire_t;

typedef struct {
    uint16_t layout_version;
    uint16_t flags;
    uint32_t item_count;
    uint64_t generation;
} nipc_lookup_resp_header_wire_t;

typedef struct {
    uint32_t pid;
    uint32_t reserved;
} nipc_apps_lookup_key_wire_t;

typedef struct {
    uint16_t layout_version;
    uint16_t status;
    uint16_t orchestrator;
    uint16_t reserved0;
    uint32_t path_offset;
    uint32_t path_length;
    uint32_t name_offset;
    uint32_t name_length;
    uint16_t label_count;
    uint16_t reserved1;
} nipc_cgroups_lookup_item_wire_t;

typedef struct {
    uint16_t layout_version;
    uint16_t status;
    uint16_t orchestrator;
    uint16_t cgroup_status;
    uint32_t pid;
    uint32_t ppid;
    uint32_t uid;
    uint32_t reserved0;
    uint64_t starttime;
    uint32_t comm_offset;
    uint32_t comm_length;
    uint32_t cgroup_path_offset;
    uint32_t cgroup_path_length;
    uint32_t cgroup_name_offset;
    uint32_t cgroup_name_length;
    uint16_t label_count;
    uint16_t reserved1;
} nipc_apps_lookup_item_wire_t;

#endif /* NETIPC_PROTOCOL_INTERNAL_H */
