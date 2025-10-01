package com.hmdp.utils.lock;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.yaml.snakeyaml.events.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的分布式锁的实现
 */
public class SimpleRedisLock implements ILock{


    private StringRedisTemplate stringRedisTemplate;

    private String name;


    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }




    public static final String key_prefix = "lock:";

    public static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    /**
     * 尝试获取锁1次
     * key：业务名称
     * value：UUID - 线程ID 方便释放锁时检查
     * @param timeoutSec 锁持有的超时时间
     * @return
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        String key = key_prefix + name;
        String value = ID_PREFIX + Thread.currentThread().getId();
        // setnx
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(
                key, value,
                timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /**
     * 释放锁
     * 如果key的value等于当前JVM的UUID拼接上当前线程的ID
     * 则可以删除key，否则不能删除
     */
//    @Override
//    public void unlock() {
//        String key = key_prefix + name;
//        String value = stringRedisTemplate.opsForValue().get(key);
//        String currentValue = ID_PREFIX + Thread.currentThread().getId();
//        // 如果判断成功，但是阻塞了，后续也会存在并发安全问题，导致误删key
//        if (value.equals(currentValue)) {
//            stringRedisTemplate.delete(key_prefix + name);
//        }
//    }


    /**
     * 基于Lua脚本实现释放锁
     */
    @Override
    public void unlock() {
        String key = key_prefix + name;
        String currentValue = ID_PREFIX + Thread.currentThread().getId();
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key),
                currentValue);
    }
}
