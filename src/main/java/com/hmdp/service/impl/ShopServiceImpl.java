package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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

    /**
     * 1. 在redis中查询商铺
     * 2.
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String shopJsonStr = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        // 缓存命中（命中非空值）
        if (StrUtil.isNotBlank(shopJsonStr)) {
            Shop shop = JSONUtil.toBean(shopJsonStr, Shop.class);
            return Result.ok(shop);
        }
        // 缓存未命中
        // 如果命中空值，不查询数据库
        if (shopJsonStr != null) {
            return Result.fail("");
        }
        Shop shop = this.getById(id);
        if (shop == null) {
            // 缓存和数据库都未命中，发生缓存穿透
            // 将空对象缓存，并设置短的有效期
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("shop id error");
        }
        // 数据库查询成功，写入redis，返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                                            CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }

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
