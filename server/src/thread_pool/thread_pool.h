#ifndef THREAD_POOL_H
#define THREAD_POOL_H

#include <thread>
#include <vector>
#include <queue>
#include <functional>
#include <mutex>
#include <condition_variable>
#include <atomic>

namespace im {

class ThreadPool {
public:
    explicit ThreadPool(size_t numThreads = std::thread::hardware_concurrency());
    ~ThreadPool();
    
    /**
     * 提交任务
     * 
     * @param task 任务函数
     */
    template<typename F>
    void submit(F&& task);
    
    /**
     * 停止线程池
     */
    void stop();

private:
    std::vector<std::thread> workers_;
    std::queue<std::function<void()>> tasks_;
    std::mutex queueMutex_;
    std::condition_variable condition_;
    std::atomic<bool> stop_;
    
    void worker();
};

template<typename F>
void ThreadPool::submit(F&& task) {
    if (stop_) {
        return;  // 线程池已停止，拒绝新任务
    }
    {
        std::unique_lock<std::mutex> lock(queueMutex_);
        if (stop_) {
            return;  // 双重检查
        }
        tasks_.emplace(std::forward<F>(task));
    }
    condition_.notify_one();
}

}  // namespace im

#endif  // THREAD_POOL_H

