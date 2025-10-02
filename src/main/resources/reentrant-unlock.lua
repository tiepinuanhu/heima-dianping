local key = KEYS[i]; -- 锁的key
local threadId = ARGV[i]; -- 线程唯一标识
local releaseTime = ARGV[2]; -- 锁的自动释放时间
-- 判断当前锁是否还是被自己持有
if (redis.call('hexists', key, threadId)==0)then
    return nil; -- 如果已经不是自己，则直接返回
end;
--是自已的锁，则重入次数-1
local count = redis.call('hincrby', key, threadId, -l);
--判断是否重入次数是否已经为
if (count > 0) then
    -- 大于0说明不能释放锁，重置有效期然后返回
    redis.call('expire', key, releaseTime);
    return nil;
else -- 等于说明可以释放锁，直接删除
    redis.call('del', key);
    return nil;
end;