package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;


    /**
     * 用于逻辑过期，获取锁成功后，重建缓存的线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }


    /**
     * 普通的Set方法
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, timeUnit);
    }

    /**
     * 带逻辑过期的Set方法
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {

        LocalDateTime expireTime
                = LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time));

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(expireTime);

        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
    }

    /**
     * 考虑缓存穿透进行查询
     * @param id
     * @return
     */
    public <T, ID> T queryByIdWithPassThrough(String keyPrefix, ID id,
                                              Class<T> type,
                                              Function<ID,T> dbFallBack,
                                              Long time, TimeUnit timeUnit) {
        // 查询缓存
        String key = keyPrefix + id;
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 缓存命中（命中非空值）
        if (StrUtil.isNotBlank(jsonStr)) {
            T bean = JSONUtil.toBean(jsonStr, type);
            return bean;
        }
        // 如果命中空值，不查询数据库
        if (jsonStr != null) {
            return null;
        }
        // 我们不知道要查哪个数据库
        // 调用调用者传入的函数，
        // 查询数据库的逻辑由调用者实现
        // 调用者就要考虑查询失败的情况
        T data = dbFallBack.apply(id);
        if (data == null) {
            // 缓存和数据库都未命中，发生缓存穿透
            // 将空对象缓存，并设置短的有效期
            stringRedisTemplate.opsForValue()
                    .set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 数据库查询成功，写入redis，返回
        this.set(key, data, time, timeUnit);
        return data;
    }


    /**
     * 对Redis的某个key进行加锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",
                LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    /**
     * 释放锁
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 使用逻辑过期解决缓存穿透问题
     * 1. 查询缓存
     * 2. 命中，查看是否过期
     * 3. 未过期，直接返回； 过期，则获取锁
     * 4. 获取成功，启动新线程进行查数据库, 返回shop信息； 获取失败，返回shop数据
     * @param id
     * @return
     */
    public <T, ID> T queryByIdWithLogicalExpire(String keyPrefix, ID id,
                                                Class<T> type,
                                                Function<ID,T> dbFallBack,
                                                Long time, TimeUnit timeUnit)  {

        String key = keyPrefix + id;
        // 查询缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(key);
        // 缓存未命中
        if (StrUtil.isBlank(jsonStr)) {
            return null;
        }
        // 缓存命中

        RedisData redisData = JSONUtil.toBean(jsonStr, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        T bean = JSONUtil.toBean(jsonObject, type);
        LocalDateTime expireTime = redisData.getExpireTime();


        // 缓存未过期，返回数据
        if (expireTime.isAfter(LocalDateTime.now())) {
            return bean;
        }
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 获取锁成功
        if (isLock) {
            log.debug("locked");

            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 执行重建
                    T data = dbFallBack.apply(id);
                    setWithLogicalExpire(key, data, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 任务完成后再释放锁
                    unLock(lockKey);
                }
            });

        }
        // 没有获取锁成功，就返回旧数据
        return bean;
    }
}
