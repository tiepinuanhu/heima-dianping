package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;


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


}
