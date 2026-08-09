#include "zip_assets.hpp"

#include <cstddef>
#include <cstdint>
#include <cstring>
#include <limits>

namespace ah::zip {
namespace {

constexpr std::uint32_t kEocdSignature = 0x06054b50U;
constexpr std::uint32_t kCentralSignature = 0x02014b50U;
constexpr std::uint32_t kLocalSignature = 0x04034b50U;
constexpr std::size_t kEocdBytes = 22;
constexpr std::size_t kCentralBytes = 46;
constexpr std::size_t kLocalBytes = 30;
constexpr std::size_t kMaxCommentBytes = 65'535;
constexpr std::uint16_t kEncryptedFlag = 1U;
constexpr std::uint16_t kDataDescriptorFlag = 1U << 3U;
constexpr std::uint16_t kStoredMethod = 0;
constexpr std::uint16_t kMaxEntries = 4'096;
constexpr std::uint32_t kMaxCentralBytes = 16U * 1024U * 1024U;
constexpr std::uint64_t kMaxPayloadBytes = 2'147'483'647ULL;
constexpr char kConfigName[] = "assets/ah/runtime/config.bin";
constexpr char kPayloadName[] = "assets/ah/runtime/payload.ahdc";

struct Directory {
    std::size_t offset;
    std::size_t size;
    std::uint16_t count;
};

struct Entry {
    std::size_t local_offset{};
    std::size_t data_offset{};
    std::size_t size{};
    std::uint32_t crc32{};
    const char* expected_name{};
    std::size_t expected_name_size{};
    bool found{};
};

bool valid(container::ByteView view) noexcept {
    return view.data != nullptr && view.size != 0;
}

std::uint16_t u16(container::ByteView bytes, std::size_t offset) noexcept {
    return static_cast<std::uint16_t>(bytes.data[offset]) |
           static_cast<std::uint16_t>(bytes.data[offset + 1]) << 8U;
}

std::uint32_t u32(container::ByteView bytes, std::size_t offset) noexcept {
    return static_cast<std::uint32_t>(bytes.data[offset]) |
           static_cast<std::uint32_t>(bytes.data[offset + 1]) << 8U |
           static_cast<std::uint32_t>(bytes.data[offset + 2]) << 16U |
           static_cast<std::uint32_t>(bytes.data[offset + 3]) << 24U;
}

bool add(std::size_t left, std::size_t right, std::size_t* output) noexcept {
    if (output == nullptr || left > std::numeric_limits<std::size_t>::max() - right) {
        return false;
    }
    *output = left + right;
    return true;
}

std::uint32_t crc32(container::ByteView bytes) noexcept {
    std::uint32_t crc = 0xffffffffU;
    for (std::size_t index = 0; index < bytes.size; ++index) {
        crc ^= bytes.data[index];
        for (unsigned bit = 0; bit < 8; ++bit) {
            const std::uint32_t mask = 0U - (crc & 1U);
            crc = (crc >> 1U) ^ (0xedb88320U & mask);
        }
    }
    return ~crc;
}

Status findDirectory(container::ByteView apk, Directory* output) noexcept {
    if (apk.size < kEocdBytes) {
        return Status::kFormat;
    }
    const std::size_t first = apk.size > kEocdBytes + kMaxCommentBytes
                                  ? apk.size - kEocdBytes - kMaxCommentBytes
                                  : 0;
    bool found = false;
    Directory result{};
    for (std::size_t offset = apk.size - kEocdBytes;; --offset) {
        if (u32(apk, offset) == kEocdSignature) {
            const std::uint16_t comment = u16(apk, offset + 20);
            std::size_t end = 0;
            if (add(offset, kEocdBytes + comment, &end) && end == apk.size) {
                if (found) {
                    return Status::kFormat;
                }
                const std::uint16_t disk = u16(apk, offset + 4);
                const std::uint16_t central_disk = u16(apk, offset + 6);
                const std::uint16_t disk_count = u16(apk, offset + 8);
                const std::uint16_t total_count = u16(apk, offset + 10);
                const std::uint32_t central_size = u32(apk, offset + 12);
                const std::uint32_t central_offset = u32(apk, offset + 16);
                if (disk != 0 || central_disk != 0 || disk_count != total_count ||
                    total_count == 0 || total_count == 0xffffU || total_count > kMaxEntries ||
                    central_size > kMaxCentralBytes || central_offset == 0xffffffffU ||
                    central_size == 0xffffffffU ||
                    static_cast<std::uint64_t>(central_offset) + central_size != offset) {
                    return Status::kUnsupported;
                }
                result = {central_offset, central_size, total_count};
                found = true;
            }
        }
        if (offset == first) {
            break;
        }
    }
    if (!found) {
        return Status::kFormat;
    }
    *output = result;
    return Status::kSuccess;
}

bool matches(container::ByteView apk, std::size_t offset, std::size_t size,
             const char* expected, std::size_t expected_size) noexcept {
    return size == expected_size && std::memcmp(apk.data + offset, expected, size) == 0;
}

Status parseLocal(container::ByteView apk, std::size_t central_offset,
                  std::uint16_t flags, std::uint16_t method, std::uint32_t crc,
                  std::uint32_t size, std::size_t local_offset,
                  const char* name, std::size_t name_size, Entry* output) noexcept {
    std::size_t header_end = 0;
    if (!add(local_offset, kLocalBytes, &header_end) || header_end > central_offset ||
        u32(apk, local_offset) != kLocalSignature || u16(apk, local_offset + 6) != flags ||
        u16(apk, local_offset + 8) != method || u32(apk, local_offset + 14) != crc ||
        u32(apk, local_offset + 18) != size || u32(apk, local_offset + 22) != size) {
        return Status::kFormat;
    }
    const std::uint16_t local_name_size = u16(apk, local_offset + 26);
    const std::uint16_t extra_size = u16(apk, local_offset + 28);
    std::size_t data_offset = 0;
    if (local_name_size != name_size ||
        !add(header_end, static_cast<std::size_t>(local_name_size) + extra_size, &data_offset) ||
        data_offset > central_offset || data_offset % 4096U != 0 ||
        !matches(apk, header_end, local_name_size, name, name_size) ||
        size > central_offset - data_offset) {
        return Status::kFormat;
    }
    output->local_offset = local_offset;
    output->data_offset = data_offset;
    output->size = size;
    output->crc32 = crc;
    output->expected_name = name;
    output->expected_name_size = name_size;
    output->found = true;
    return Status::kSuccess;
}

Status inspectTarget(container::ByteView apk, std::size_t central_offset,
                     std::uint16_t flags, std::uint16_t method,
                     std::uint32_t crc, std::uint32_t compressed_size,
                     std::uint32_t uncompressed_size, std::uint32_t local_offset,
                     const char* name, std::size_t name_size, Entry* target) noexcept {
    if (target->found) {
        return Status::kDuplicate;
    }
    if ((flags & (kEncryptedFlag | kDataDescriptorFlag)) != 0 || method != kStoredMethod ||
        compressed_size != uncompressed_size || uncompressed_size == 0) {
        return Status::kUnsupported;
    }
    if ((name == kConfigName && uncompressed_size != container::kConfigBytes) ||
        (name == kPayloadName && (uncompressed_size < container::kHeaderBytes ||
                                  uncompressed_size > kMaxPayloadBytes))) {
        return Status::kFormat;
    }
    return parseLocal(apk, central_offset, flags, method, crc, uncompressed_size,
                      local_offset, name, name_size, target);
}

bool overlaps(const Entry& left, const Entry& right) noexcept {
    const std::size_t left_end = left.data_offset + left.size;
    const std::size_t right_end = right.data_offset + right.size;
    return left.local_offset < right_end && right.local_offset < left_end;
}

}  // namespace

Status locateFixedAssets(container::ByteView apk, FixedAssets* output) noexcept {
    if (output == nullptr || !valid(apk) || apk.size > 0xffffffffULL) {
        return Status::kInvalidArgument;
    }
    *output = FixedAssets{};
    Directory directory{};
    Status status = findDirectory(apk, &directory);
    if (status != Status::kSuccess) {
        return status;
    }
    Entry config{};
    Entry payload{};
    std::size_t cursor = directory.offset;
    const std::size_t end = directory.offset + directory.size;
    for (std::uint16_t index = 0; index < directory.count; ++index) {
        if (cursor > end || end - cursor < kCentralBytes || u32(apk, cursor) != kCentralSignature) {
            return Status::kFormat;
        }
        const std::uint16_t flags = u16(apk, cursor + 8);
        const std::uint16_t method = u16(apk, cursor + 10);
        const std::uint32_t crc = u32(apk, cursor + 16);
        const std::uint32_t compressed = u32(apk, cursor + 20);
        const std::uint32_t uncompressed = u32(apk, cursor + 24);
        const std::uint16_t name_size = u16(apk, cursor + 28);
        const std::uint16_t extra_size = u16(apk, cursor + 30);
        const std::uint16_t comment_size = u16(apk, cursor + 32);
        const std::uint16_t disk_start = u16(apk, cursor + 34);
        const std::uint32_t local_offset = u32(apk, cursor + 42);
        std::size_t next = 0;
        if (name_size == 0 || disk_start != 0 || compressed == 0xffffffffU ||
            uncompressed == 0xffffffffU || local_offset == 0xffffffffU ||
            !add(cursor, kCentralBytes + static_cast<std::size_t>(name_size) +
                             extra_size + comment_size, &next) || next > end) {
            return Status::kUnsupported;
        }
        const std::size_t name_offset = cursor + kCentralBytes;
        Entry* target = nullptr;
        const char* expected = nullptr;
        std::size_t expected_size = 0;
        if (matches(apk, name_offset, name_size, kConfigName, sizeof(kConfigName) - 1)) {
            target = &config;
            expected = kConfigName;
            expected_size = sizeof(kConfigName) - 1;
        } else if (matches(apk, name_offset, name_size, kPayloadName, sizeof(kPayloadName) - 1)) {
            target = &payload;
            expected = kPayloadName;
            expected_size = sizeof(kPayloadName) - 1;
        }
        if (target != nullptr) {
            status = inspectTarget(apk, directory.offset, flags, method, crc, compressed,
                                   uncompressed, local_offset, expected, expected_size, target);
            if (status != Status::kSuccess) {
                return status;
            }
        }
        cursor = next;
    }
    if (cursor != end) {
        return Status::kFormat;
    }
    if (!config.found || !payload.found) {
        return Status::kMissing;
    }
    if (overlaps(config, payload)) {
        return Status::kFormat;
    }
    const container::ByteView config_view{apk.data + config.data_offset, config.size};
    const container::ByteView payload_view{apk.data + payload.data_offset, payload.size};
    if (crc32(config_view) != config.crc32 || crc32(payload_view) != payload.crc32) {
        return Status::kCrcMismatch;
    }
    output->config = config_view;
    output->payload = payload_view;
    return Status::kSuccess;
}

}  // namespace ah::zip
