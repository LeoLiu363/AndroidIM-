#ifndef ENCODER_H
#define ENCODER_H

#include "message.h"
#include <vector>

namespace im {

class MessageEncoder {
public:
    /**
     * 编码消息
     * 
     * @param type 消息类型
     * @param jsonData JSON 数据
     * @return 编码后的字节数组
     */
    static std::vector<uint8_t> encode(MessageType type, const std::string& jsonData);
};

}  // namespace im

#endif  // ENCODER_H

