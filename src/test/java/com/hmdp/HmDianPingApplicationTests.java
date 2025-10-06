package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = HmDianPingApplication.class)
public class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;


    @Resource
    CacheClient cacheClient;


    @Resource
    StringRedisTemplate stringRedisTemplate;


    @Resource
    RedisIdWorker redisIdWorker;


    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    public void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + Long.toHexString(id));
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time =  " + (end - start));
    }



    @Test
    public void testShop2Redis() {
//        shopService.saveShop2Redis(1L, 10L);

        Shop shop = shopService.getById(1);


        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


    @Test
    public void loadShopDataToRedis() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            String key = SHOP_GEO_KEY + typeId;
//            for (Shop shop : shops) {
//                stringRedisTemplate.opsForGeo()
//                        .add(
//                            key,
//                            new Point(shop.getX(), shop.getY()),
//                            shop.getId().toString());
//            }
            List<RedisGeoCommands.GeoLocation<String>> locations
                    = new ArrayList<>(shops.size());
            for (Shop shop : shops) {
                RedisGeoCommands.GeoLocation<String> geoLocation
                        = new RedisGeoCommands.GeoLocation<String>
                        (shop.getId().toString(), new Point(shop.getX(), shop.getY()));
                locations.add(geoLocation);
            }
            // 只发一次请求，保存多组数据
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}
