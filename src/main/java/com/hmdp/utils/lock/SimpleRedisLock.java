package com.hmdp.utils.lock;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{


    private StringRedisTemplate stringRedisTemplate;

    private String name;
    public static final String key_prefix = "lock:";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    /**
     * 尝试获取锁1次
     * key：业务名称
     * value：线程ID
     * @param timeoutSec 锁持有的超时时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        long threadId = Thread.currentThread().getId();
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key_prefix + name,
                threadId + "",
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        stringRedisTemplate.delete(key_prefix + name);
    }
}
