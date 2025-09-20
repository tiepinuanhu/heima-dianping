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

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        // 缓存命中
        if (StrUtil.isNotBlank(shopJsonStr)) {
            Shop shop = JSONUtil.toBean(shopJsonStr, Shop.class);
            return Result.ok(shop);
        }
        // 缓存未命中，查询数据库
        Shop shop = this.getById(id);
        if (shop == null) {
            return Result.fail("shop id error");
        }
        // 数据库查询成功，写入redis，返回
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
