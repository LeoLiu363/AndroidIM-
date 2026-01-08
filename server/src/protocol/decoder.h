#ifndef DECODER_H
#define DECODER_H

#include "message.h"
#include <vector>
#include <queue>

namespace im {

class MessageDecoder {
public:
    /**
     * 添加数据到缓冲区
     * 
     * @param data 接收到的数据
     * @return 解码出的消息列表
     */
    std::queue<Packet> addData(const std::vector<uint8_t>& data);
    
    /**
     * 清空缓冲区
     */
    void clear();

private:
    std::vector<uint8_t> buffer_;
    
    /**
     * 解码消息
     */
    std::queue<Packet> decodeMessages();
};

}  // namespace im

#endif  // DECODER_H

