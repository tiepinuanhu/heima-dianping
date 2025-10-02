local key = KEYS[i]; -- 锁的key
local threadId = ARGV[i]; -- 线程唯一标识
local releaseTime = ARGV[2]; -- 锁的自动释放时间
-- 判断是否存在（首次获取锁）
if (redis.call('exists', key) == 0) then
    -- 不存在，获取锁
    redis.call('hset', key, threadId,'1');
    -- 设置有效期
    redis.call('expire', key, releaseTime);
    --返回结果
    return 1;
end;
--锁已经存在，判断thread工d是否是自己（再次获取锁）
if(redis.call('hexists', key, threadId) == 1) then
    --不存在，获取锁，重入次数+1
    redis.call('hincrby', key, threadId, 'i');
    --设置有效期
    redis.call('expire', key, releaseTime);
    return 1; -- 返回结果
end;
return 0; -- 代码走到这里，说明获取锁的不是自己，获取锁失败



