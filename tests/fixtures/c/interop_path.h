#ifndef NIPC_TEST_INTEROP_PATH_H
#define NIPC_TEST_INTEROP_PATH_H

#include <limits.h>
#if defined(_WIN32) && !defined(__MSYS__)
#include <ctype.h>
#else
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#endif
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

#ifndef PATH_MAX
#define PATH_MAX 4096
#endif

static int nipc_test_path_has_parent_component(const char *path)
{
    if (strcmp(path, "..") == 0)
        return 1;
    if (strncmp(path, "../", 3) == 0)
        return 1;
    if (strncmp(path, "..\\", 3) == 0)
        return 1;
    if (strstr(path, "/../") != NULL)
        return 1;
    if (strstr(path, "\\..\\") != NULL)
        return 1;
    size_t len = strlen(path);
    return len >= 3 &&
           (strcmp(path + len - 3, "/..") == 0 ||
            strcmp(path + len - 3, "\\..") == 0);
}

#if defined(_WIN32) && !defined(__MSYS__)
static int nipc_test_path_is_absolute(const char *path)
{
    unsigned char drive = (unsigned char)path[0];
    if (path[0] == '/' || path[0] == '\\')
        return 1;
    return isalpha(drive) && path[1] == ':' &&
           (path[2] == '/' || path[2] == '\\');
}
#else
static int nipc_test_path_is_absolute(const char *path)
{
    return path[0] == '/';
}
#endif

static int nipc_test_resolve_run_dir(const char *input,
                                     char *resolved,
                                     size_t resolved_len)
{
    if (!input || !resolved || resolved_len == 0) {
        fprintf(stderr, "invalid run directory argument\n");
        return -1;
    }

    if (!nipc_test_path_is_absolute(input) ||
        nipc_test_path_has_parent_component(input)) {
        fprintf(stderr, "run directory must be an absolute path without '..': %s\n",
                input);
        return -1;
    }

#if defined(_WIN32) && !defined(__MSYS__)
    struct stat st;
    if (stat(input, &st) != 0 || !S_ISDIR(st.st_mode)) {
        fprintf(stderr, "run directory is not a directory: %s\n", input);
        return -1;
    }
#else
    struct stat lst;
    if (lstat(input, &lst) != 0 || S_ISLNK(lst.st_mode)) {
        fprintf(stderr, "run directory must exist and not be a symlink: %s\n", input);
        return -1;
    }

    struct stat st;
    if (stat(input, &st) != 0 || !S_ISDIR(st.st_mode)) {
        fprintf(stderr, "run directory is not a directory: %s\n", input);
        return -1;
    }

    if (st.st_uid != geteuid()) {
        fprintf(stderr, "run directory is not owned by this user: %s\n", input);
        return -1;
    }
    if ((st.st_mode & (S_IWGRP | S_IWOTH)) != 0) {
        fprintf(stderr, "run directory is group/world writable: %s\n", input);
        return -1;
    }
#endif

    size_t n = strlen(input) + 1;
    if (n > resolved_len) {
        fprintf(stderr, "run directory path is too long\n");
        return -1;
    }

    memcpy(resolved, input, n);
    return 0;
}

static int nipc_test_open_run_dir(const char *input,
                                  char *resolved,
                                  size_t resolved_len)
{
    if (nipc_test_resolve_run_dir(input, resolved, resolved_len) != 0)
        return -1;

#if defined(_WIN32) && !defined(__MSYS__)
    return 0;
#else
    DIR *dir = opendir(resolved);
    if (!dir) {
        fprintf(stderr, "cannot open run directory: %s\n", resolved);
        return -1;
    }

    int raw_fd = dirfd(dir);
    int fd = raw_fd >= 0 ? fcntl(raw_fd, F_DUPFD_CLOEXEC, 0) : -1;
    closedir(dir);
    if (fd < 0)
        fprintf(stderr, "cannot duplicate run directory fd: %s\n", resolved);
    return fd;
#endif
}

#endif
