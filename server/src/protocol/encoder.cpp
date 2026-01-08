#include "encoder.h"
#include <arpa/inet.h>
#include <cstring>

namespace im {

std::vector<uint8_t> MessageEncoder::encode(MessageType type, const std::string& jsonData) {
    std::vector<uint8_t> packet;
    
    // Magic (4 bytes)
    uint32_t magic = htonl(MAGIC);
    packet.insert(packet.end(), 
                  reinterpret_cast<uint8_t*>(&magic),
                  reinterpret_cast<uint8_t*>(&magic) + 4);
    
    // Type (2 bytes)
    uint16_t msgType = htons(static_cast<uint16_t>(type));
    packet.insert(packet.end(),
                  reinterpret_cast<uint8_t*>(&msgType),
                  reinterpret_cast<uint8_t*>(&msgType) + 2);
    
    // Length (4 bytes)
    uint32_t length = htonl(static_cast<uint32_t>(jsonData.length()));
    packet.insert(packet.end(),
                  reinterpret_cast<uint8_t*>(&length),
                  reinterpret_cast<uint8_t*>(&length) + 4);
    
    // Data
    packet.insert(packet.end(), jsonData.begin(), jsonData.end());
    
    return packet;
}

}  // namespace im

