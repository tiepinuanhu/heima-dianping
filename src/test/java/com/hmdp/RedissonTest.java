package com.hmdp;


import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.runner.RunWith;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = HmDianPingApplication.class)
public class RedissonTest {

    @Autowired
    private RedissonClient redissonClient;
    @Autowired
    private RedissonClient redissonClient2;
    @Autowired
    private RedissonClient redissonClient3;

    private RLock multiLock;

    @Before
    public void setUp() {
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");
        // 创建联锁

        multiLock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }

    @Test
    public void method1() throws InterruptedException {
        // 尝试获取锁

        try {
            boolean isLock = multiLock.tryLock();
            if (!isLock) {
                log.error("获取锁失败 .... 1");
                return;
            }
            log.info("获取锁成功 .... 1");
            method2();
            log.info("开始执行业务 ... 1");
        } finally {
            log.warn("准备释放锁 .... 1");
            multiLock.unlock();
        }
    }
    void method2() {
        // 尝试获取锁
        try {
            boolean isLock = multiLock.tryLock();
            if (!isLock) {
                log.error("获取锁失败 .... 2");
                return;
            }
            log.info("获取锁成功 .... 2");
            log.info("开始执行业务 ... 2");
        } finally {
            log.warn("准备释放锁 .... 2");
            multiLock.unlock();
        }
    }
}

