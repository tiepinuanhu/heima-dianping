



-- 要比较的线程表标识（UUID-ThreadId），要动态传入
local threadId = ARGV[1]
-- 获取当前key的value，value要与线程标识比较
local key  = KEYS[1]
local id = redis.call('get', key)
-- 判断是否相等
if (threadId == id)
then
    -- 相等，则释放分布式锁
    return redis.call("del", key)
end
return 0