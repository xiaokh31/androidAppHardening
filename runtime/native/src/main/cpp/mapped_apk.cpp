#include "mapped_apk.hpp"

#include <cerrno>
#include <cstddef>
#include <cstdint>
#include <limits>

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

namespace ah::apk {

ReadOnlyMapping::~ReadOnlyMapping() noexcept {
    (void) close();
}

Status ReadOnlyMapping::openAbsolute(const char* absolute_path) noexcept {
    if (absolute_path == nullptr || absolute_path[0] != '/' || data_ != nullptr ||
        descriptor_ != -1) {
        return Status::kInvalidArgument;
    }
    descriptor_ = ::open(absolute_path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (descriptor_ < 0) {
        descriptor_ = -1;
        return Status::kOpenFailed;
    }
    struct stat attributes {};
    if (fstat(descriptor_, &attributes) != 0 || !S_ISREG(attributes.st_mode)) {
        (void) close();
        return Status::kNotRegular;
    }
    if (attributes.st_size <= 0 ||
        static_cast<std::uint64_t>(attributes.st_size) >
            static_cast<std::uint64_t>(std::numeric_limits<std::uint32_t>::max())) {
        (void) close();
        return Status::kLength;
    }
    size_ = static_cast<std::size_t>(attributes.st_size);
    void* mapped = mmap(nullptr, size_, PROT_READ, MAP_PRIVATE, descriptor_, 0);
    if (mapped == MAP_FAILED) {
        size_ = 0;
        (void) close();
        return Status::kMapFailed;
    }
    data_ = static_cast<const std::uint8_t*>(mapped);
    return Status::kSuccess;
}

Status ReadOnlyMapping::close() noexcept {
    bool failed = false;
    if (data_ != nullptr) {
        failed = munmap(const_cast<std::uint8_t*>(data_), size_) != 0;
        data_ = nullptr;
        size_ = 0;
    }
    if (descriptor_ != -1) {
        failed = (::close(descriptor_) != 0) || failed;
        descriptor_ = -1;
    }
    return failed ? Status::kCleanupFailed : Status::kSuccess;
}

}  // namespace ah::apk
