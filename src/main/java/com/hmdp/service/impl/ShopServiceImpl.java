package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    StringRedisTemplate stringRedisTemplate;



    @Resource
    CacheClient cacheClient;



    /**
     * 1. 在redis中查询商铺
     * 2.
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id)  {
//        Shop shop = queryByIdWithLogicalExpire(id);

//        Shop shop = cacheClient.queryByIdWithPassThrough(
//                CACHE_SHOP_KEY, id, Shop.class,
//                this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryByIdWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
                this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        if (shop == null) {
            shop = this.getById(id);
            cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + id, shop, CACHE_SHOP_TTL, TimeUnit.SECONDS);
        }
        return Result.ok(shop);
    }







    /**
     * 考虑缓存击穿进行查询
     * @param id
     * @return
     */
//    public Shop queryByIdWithMutex(Long id)  {
//        // 查询缓存
//        String shopJsonStr = stringRedisTemplate.opsForValue()
//                .get(CACHE_SHOP_KEY + id);
//        // 缓存命中（命中非空值）
//        if (StrUtil.isNotBlank(shopJsonStr)) {
//            Shop shop = JSONUtil.toBean(shopJsonStr, Shop.class);
//            return shop;
//        }
//        // 缓存未命中
//        // 如果命中空值，不查询数据库
//        if (shopJsonStr != null) {
//            return null;
//        }
//        // 尝试获取锁，进行缓存重建
//
//        String lockKey = LOCK_SHOP_KEY + id;
//        Shop shop = null;
//        try {
//            // 每个店铺id对应一把锁
//            if (!tryLock(lockKey)) {
//                Thread.sleep(50);
//                // 递归
//                return queryByIdWithMutex(id);
//            }
//            shop = this.getById(id);
//            if (shop == null) {
//                // 缓存和数据库都未命中，发生缓存穿透
//                // 将空对象缓存，并设置短的有效期
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            // 数据库查询成功，写入redis，返回
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
//                    CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unLock(lockKey);
//        }
//        return shop;
//    }


//    /**
//     * 将shop信息写入redis
//     * 1. 获取shop信息
//     * 2. 添加过期时间
//     * 3. 保存到redis
//     * @param shopId
//     * @param expireSeconds expireSeconds后过期
//     */
//    public void saveShop2Redis(Long shopId, Long expireSeconds)  {
//        Shop shop = this.getById(shopId);
//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now()
//                .plusSeconds(expireSeconds));
//        String jsonStr = JSONUtil.toJsonStr(redisData);
//        // 永久有效的key，真正的过期时间是value中字段设置的时间
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + shopId, jsonStr);
//    }



    /**
     * 1. 修改数据库
     * 2. 删除redis缓存
     * 声明为事务
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        if (shop.getId() == null) {
            return Result.fail("fail");
        }
        boolean b = updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}
