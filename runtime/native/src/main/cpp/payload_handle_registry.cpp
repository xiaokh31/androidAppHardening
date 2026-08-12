#include "payload_handle_registry.hpp"

#include <array>
#include <cstddef>
#include <cstdint>
#include <mutex>

namespace ah::handles {
namespace {

constexpr std::size_t kMaxHandles = 32;
constexpr std::uint64_t kIndexMask = 0xffU;
constexpr std::uint64_t kMaxGeneration = (std::uint64_t{1} << 55U) - 1U;

struct Slot {
    memory::PayloadHandle payload{};
    payload::AuthenticatedMetadata metadata{};
    std::uint64_t generation{};
    bool occupied{};
};

std::array<Slot, kMaxHandles> g_slots{};
std::mutex g_mutex{};

bool decode(std::uint64_t handle, std::size_t* index, std::uint64_t* generation) noexcept {
    if (index == nullptr || generation == nullptr || handle == 0) {
        return false;
    }
    const std::uint64_t encoded_index = handle & kIndexMask;
    const std::uint64_t encoded_generation = handle >> 8U;
    if (encoded_index == 0 || encoded_index > kMaxHandles || encoded_generation == 0) {
        return false;
    }
    *index = static_cast<std::size_t>(encoded_index - 1U);
    *generation = encoded_generation;
    return true;
}

}  // namespace

Status install(
    memory::PayloadHandle* payload_handle,
    const payload::AuthenticatedMetadata& metadata,
    std::uint64_t* handle) noexcept {
    if (handle != nullptr) {
        *handle = 0;
    }
    if (payload_handle == nullptr || handle == nullptr || payload_handle->size() == 0) {
        return Status::kInvalidArgument;
    }
    const std::lock_guard<std::mutex> lock(g_mutex);
    for (std::size_t index = 0; index < g_slots.size(); ++index) {
        Slot& slot = g_slots[index];
        if (slot.occupied || slot.payload.size() != 0) {
            continue;
        }
        std::uint64_t next_generation = slot.generation + 1U;
        if (next_generation == 0 || next_generation > kMaxGeneration) {
            next_generation = 1;
        }
        if (payload_handle->transferTo(&slot.payload) != memory::Status::kSuccess) {
            return Status::kInvalidArgument;
        }
        slot.metadata = metadata;
        slot.generation = next_generation;
        slot.occupied = true;
        *handle = (next_generation << 8U) | (index + 1U);
        return Status::kSuccess;
    }
    return Status::kExhausted;
}

Status consume(
    std::uint64_t handle,
    SnapshotConsumer consumer,
    void* context) noexcept {
    std::size_t index = 0;
    std::uint64_t generation = 0;
    if (consumer == nullptr || !decode(handle, &index, &generation)) {
        return Status::kInvalidArgument;
    }
    const std::lock_guard<std::mutex> lock(g_mutex);
    Slot& slot = g_slots[index];
    if (!slot.occupied || slot.generation != generation) {
        return Status::kUnknownHandle;
    }
    const Snapshot snapshot{&slot.payload, &slot.metadata};
    return consumer(snapshot, context) ? Status::kSuccess : Status::kInvalidArgument;
}

Status applyProfile(
    std::uint64_t handle,
    memory::Profile profile,
    memory::Capabilities* capabilities) noexcept {
    std::size_t index = 0;
    std::uint64_t generation = 0;
    if (capabilities == nullptr || !decode(handle, &index, &generation)) {
        return Status::kInvalidArgument;
    }
    const std::lock_guard<std::mutex> lock(g_mutex);
    Slot& slot = g_slots[index];
    if (!slot.occupied || slot.generation != generation) {
        return Status::kUnknownHandle;
    }
    return slot.payload.applyProfile(profile, capabilities) == memory::Status::kSuccess
               ? Status::kSuccess
               : Status::kInvalidArgument;
}

Status close(std::uint64_t handle) noexcept {
    std::size_t index = 0;
    std::uint64_t generation = 0;
    if (!decode(handle, &index, &generation)) {
        return Status::kInvalidArgument;
    }
    const std::lock_guard<std::mutex> lock(g_mutex);
    Slot& slot = g_slots[index];
    if (!slot.occupied || slot.generation != generation) {
        return Status::kUnknownHandle;
    }
    slot.occupied = false;
    slot.metadata = payload::AuthenticatedMetadata{};
    return slot.payload.close() == memory::Status::kSuccess
               ? Status::kSuccess
               : Status::kCleanupFailed;
}

}  // namespace ah::handles
