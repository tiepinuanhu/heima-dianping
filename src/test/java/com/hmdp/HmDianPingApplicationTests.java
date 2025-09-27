package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = HmDianPingApplication.class)
public class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;


    @Resource
    CacheClient cacheClient;


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
}
