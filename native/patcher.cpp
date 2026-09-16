#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <string>
#include <sys/types.h>
#include <unistd.h>

struct Patch {
    const char* name;
    uintptr_t rva;
    uint8_t original[8];
    uint8_t replacement[8];
};

static const uint8_t RETURN_FALSE[8] = {0x00,0x00,0x80,0x52,0xC0,0x03,0x5F,0xD6};
static const uint8_t RETURN_TRUE[8]  = {0x20,0x00,0x80,0x52,0xC0,0x03,0x5F,0xD6};

static Patch patches[] = {
    {"WillEnterFtue", 0xAF08534,
     {0xFE,0x4F,0xBF,0xA9,0x13,0xD1,0x01,0x90},
     {0x00,0x00,0x80,0x52,0xC0,0x03,0x5F,0xD6}},
    {"IsTutorialFinished(int)", 0x9DE0A04,
     {0xFE,0x57,0xBE,0xA9,0xF4,0x4F,0x01,0xA9},
     {0x20,0x00,0x80,0x52,0xC0,0x03,0x5F,0xD6}},
    {"IsTutorialFinished(TutorialType)", 0x9DE3174,
     {0xFE,0x0F,0x1E,0xF8,0xF4,0x4F,0x01,0xA9},
     {0x20,0x00,0x80,0x52,0xC0,0x03,0x5F,0xD6}},
};

static pid_t find_pid(const char* package_name) {
    DIR* proc = opendir("/proc");
    if (!proc) return -1;
    dirent* entry;
    while ((entry = readdir(proc))) {
        char* end = nullptr;
        long candidate = strtol(entry->d_name, &end, 10);
        if (!end || *end != '\0' || candidate <= 0) continue;
        char path[64];
        snprintf(path, sizeof(path), "/proc/%ld/cmdline", candidate);
        int fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) continue;
        char cmdline[256] = {};
        ssize_t n = read(fd, cmdline, sizeof(cmdline) - 1);
        close(fd);
        if (n > 0 && strcmp(cmdline, package_name) == 0) {
            closedir(proc);
            return static_cast<pid_t>(candidate);
        }
    }
    closedir(proc);
    return -1;
}

static uintptr_t module_base(pid_t pid, const char* module) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE* fp = fopen(path, "r");
    if (!fp) return 0;
    char line[1024];
    uintptr_t fallback = 0;
    while (fgets(line, sizeof(line), fp)) {
        if (!strstr(line, module)) continue;
        unsigned long start = 0, file_offset = 0;
        if (sscanf(line, "%lx-%*lx %*4s %lx", &start, &file_offset) != 2) continue;
        if (!fallback) fallback = static_cast<uintptr_t>(start - file_offset);
        if (file_offset == 0) {
            fclose(fp);
            return static_cast<uintptr_t>(start);
        }
    }
    fclose(fp);
    return fallback;
}

static bool same(const uint8_t* a, const uint8_t* b) {
    return memcmp(a, b, 8) == 0;
}

int main(int argc, char** argv) {
    if (argc != 3 || (strcmp(argv[1], "apply") && strcmp(argv[1], "restore"))) {
        fprintf(stderr, "usage: %s <apply|restore> <package>\n", argv[0]);
        return 2;
    }
    const bool apply = strcmp(argv[1], "apply") == 0;
    pid_t pid = find_pid(argv[2]);
    if (pid < 0) {
        puts("PROCESS_NOT_FOUND: buka CODM dulu");
        return 3;
    }
    uintptr_t base = module_base(pid, "libunity.so");
    if (!base) {
        puts("MODULE_NOT_READY: libunity.so belum dimuat");
        return 4;
    }
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
    int mem = open(mem_path, O_RDWR | O_CLOEXEC);
    if (mem < 0) {
        printf("MEM_OPEN_FAILED: %s\n", strerror(errno));
        return 5;
    }
    bool any_changed = false;
    for (const Patch& p : patches) {
        const off64_t address = static_cast<off64_t>(base + p.rva);
        uint8_t current[8];
        if (pread64(mem, current, sizeof(current), address) != sizeof(current)) {
            printf("READ_FAILED %s: %s\n", p.name, strerror(errno));
            close(mem);
            return 6;
        }
        const uint8_t* wanted = apply ? p.replacement : p.original;
        const uint8_t* expected = apply ? p.original : p.replacement;
        if (same(current, wanted)) continue;
        if (!same(current, expected)) {
            printf("BYTE_MISMATCH %s RVA=0x%lX\n", p.name, static_cast<unsigned long>(p.rva));
            close(mem);
            return 7;
        }
        if (pwrite64(mem, wanted, 8, address) != 8) {
            printf("WRITE_FAILED %s: %s\n", p.name, strerror(errno));
            close(mem);
            return 8;
        }
        any_changed = true;
    }
    close(mem);
    if (apply) puts(any_changed ? "PATCH_OK: tutorial dilewati" : "ALREADY_PATCHED: patch sudah aktif");
    else puts(any_changed ? "RESTORE_OK: byte asli dipulihkan" : "ALREADY_ORIGINAL: tidak ada patch aktif");
    return 0;
}

