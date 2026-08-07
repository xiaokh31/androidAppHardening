#ifndef AH_RUNTIME_PAYLOAD_HANDLE_REGISTRY_HPP
#define AH_RUNTIME_PAYLOAD_HANDLE_REGISTRY_HPP

#include "authenticated_payload.hpp"

#include <cstdint>

namespace ah::handles {

enum class Status : std::uint8_t {
    kSuccess = 0,
    kInvalidArgument = 1,
    kExhausted = 2,
    kUnknownHandle = 3,
    kCleanupFailed = 4,
};

struct Snapshot {
    const memory::PayloadHandle* payload;
    const payload::AuthenticatedMetadata* metadata;
};

using SnapshotConsumer = bool (*)(const Snapshot&, void*) noexcept;

Status install(
    memory::PayloadHandle* payload,
    const payload::AuthenticatedMetadata& metadata,
    std::uint64_t* handle) noexcept;

Status consume(
    std::uint64_t handle,
    SnapshotConsumer consumer,
    void* context) noexcept;

Status close(std::uint64_t handle) noexcept;

}  // namespace ah::handles

#endif
