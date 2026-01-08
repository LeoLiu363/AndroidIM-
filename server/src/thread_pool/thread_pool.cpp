#include "thread_pool.h"
#include <chrono>

namespace im {

ThreadPool::ThreadPool(size_t numThreads) : stop_(false) {
    for (size_t i = 0; i < numThreads; ++i) {
        workers_.emplace_back([this] { worker(); });
    }
}

ThreadPool::~ThreadPool() {
    stop();
}

void ThreadPool::stop() {
    if (stop_.exchange(true)) {
        return;  // 已经停止
    }
    
    // 通知所有等待的线程
    condition_.notify_all();
    
    // 等待所有线程退出
    // 注意：由于 socket 是非阻塞的，任务中的 recv 不会无限阻塞
    for (auto& worker : workers_) {
        if (worker.joinable()) {
            worker.join();
        }
    }
    workers_.clear();
}

void ThreadPool::worker() {
    while (true) {
        std::function<void()> task;
        
        {
            std::unique_lock<std::mutex> lock(queueMutex_);
            condition_.wait(lock, [this] { return stop_ || !tasks_.empty(); });
            
            if (stop_ && tasks_.empty()) {
                return;
            }
            
            task = std::move(tasks_.front());
            tasks_.pop();
        }
        
        task();
    }
}

}  // namespace im

