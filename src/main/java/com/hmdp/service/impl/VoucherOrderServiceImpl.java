package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.annotation.security.RunAs;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;

    @Resource
    StringRedisTemplate stringRedisTemplate;


    @Resource
    RedissonClient redissonClient;

    @Resource
    RedisIdWorker redisIdWorker;


    /**
     * 加载Lua脚本
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill-check.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    /**
     * 1. 查询优惠券信息（开始结束时间，库存）
     * 2. 校验时间和库存
     * 3. 创建订单
     * 4. 扣减库存
     * 5. 返回订单ID
     */
//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//        LocalDateTime beginTime = seckillVoucher.getBeginTime();
//        LocalDateTime endTime = seckillVoucher.getEndTime();
//        Integer stock = seckillVoucher.getStock();
//        // 判断是否符合优惠券的时间段
//        if (LocalDateTime.now().isBefore(beginTime)) {
//            return Result.fail("秒杀未开始");
//        }
//        if (LocalDateTime.now().isAfter(endTime)) {
//            return Result.fail("秒杀已结束");
//        }
//        // 判断优惠券是否有库存
//        if (stock <= 0) {
//            return Result.fail("优惠券已经抢光");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 确保事务提交后，再释放锁
//        // 否则，事务还没提交，还没写入数据库，就释放了锁
//        // 其他线程判断没有订单，从而进行了插入订单，从而导致了并发安全问题
////        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
////        boolean locked = lock.tryLock(1);
//
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean locked = lock.tryLock();
//        // 获取锁失败，证明该用户有多个线程同时下单，这是不允许的
//        if (!locked) {
//            return Result.fail("不允许多次下单");
//        }
//        // 成功获取锁
//        try {
//            // 获取当前代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//    }


//    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR
            = Executors.newSingleThreadExecutor();



    /**
     * 在类加载完成后执行初始化线程池，开始执行任务
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private void handelVoucherOrder(VoucherOrder voucherOrder) {
        // 调用该方法的线程时main的子线程，无法与main共享ThreadLocal的用户信息
        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = lock.tryLock();
        // 获取锁失败，证明该用户有多个线程同时下单，这是不允许的
        if (!locked) {
            log.error("不允许重复下单");
            return;
        }
        // 成功获取锁
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }


    /**
     * @Title 消费线程改造
     * @Date 2025-10-03 11:17:17
     * 1. 从消息队列中获取消息，而不是阻塞队列
     * 2. 判断消息获取是否成功
     * 3. 创建订单
     * 4. 返回ACK
     */
    private class VoucherOrderHandler implements Runnable {
        private String queueName = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("group1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 成功获取了消息，将消息解析为订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 下单
                    createVoucherOrder(voucherOrder);
                    // ACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "group1", record.getId());
                } catch (Exception e) {
                    // 出现异常，则没有返回ACK，从pending list取消息消费
                    log.error("处理订单异常");
                    handelPendingList();
                }
            }
        }

        private void handelPendingList() {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("group1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    // 没有异常消息，则结束
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    // 成功获取了消息，将消息解析为订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 下单
                    createVoucherOrder(voucherOrder);
                    // ACK
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "group1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
    }
    /**
     * 创建订单任务要执行的动作
     */
//    private class VoucherOrderHandler implements Runnable {
//
//        @Override
//        public void run() {
//            while (true) {
//                try {
//                    // 1. 获取订单信息
//                    VoucherOrder order = orderTasks.take();
//                    // 2. 创建订单
//                    handelVoucherOrder(order);
//                } catch (Exception e) {
//                    log.error("处理订单异常");
//                    throw new RuntimeException(e);
//                }
//            }
//        }
//    }
    private IVoucherOrderService proxy;
    /**
     * @DateTime 2025-10-02 20:11:19
     * @param voucherId
     * @return
     * 1. 执行Lua脚本，校验用户下单资格
     *
     *
     *
     * 返回订单ID
     */
//    @Override
//    public Result secKillVoucher(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        // 1. 执行Lua脚本
//        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
//                        Collections.emptyList(),
//                        voucherId.toString(), userId.toString());
//        if (result != 0) {
//            return result == 1?
//                    Result.fail("优惠券已抢光"): Result.fail("已经下单");
//        }
//        long orderId = redisIdWorker.nextId("order");
//
//
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setVoucherId(voucherId);
//        voucherOrder.setUserId(userId);
//        // 通过校验，后将下单信息发送到消息队列
//        orderTasks.add(voucherOrder);
//
//
//        proxy = (IVoucherOrderService) AopContext.currentProxy();
//
//        // 下单成功，返回订单id
//        return Result.ok(orderId);
//    }


    /**
     * @Date 2025-10-03 11:13:58
     * @param voucherId
     * @return
     * 在Lua脚本中将消息发送给消息队列
     */
    @Override
    public Result secKillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1. 执行Lua脚本: 判断秒杀资格，发送订单信息到消息队列
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId));
        if (result != 0) {
            return result == 1?
                    Result.fail("优惠券已抢光"): Result.fail("已经下单");
        }
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 下单成功，返回订单id
        return Result.ok(orderId);
    }

    /**
     * 给创建订单的方法加synchronized
     * 应该是对每个用户加锁，而不是加到方法上
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 检查用户是否已经买过该商品了
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        // 用户已经买过该商品了
        if (count > 0) {
            return Result.fail("该用户已经购买过了");
        }
        // 扣减库存
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!updated) {
            return Result.fail("优惠券已经抢光");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 使用Id生成器生成orderID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        this.save(voucherOrder);
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        // 检查用户是否已经买过该商品了
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId).count();
        // 用户已经买过该商品了
        if (count > 0) {
            log.error("已下单");
            return;
        }
        // 扣减库存
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!updated) {
            log.error("库存不足");
            return;
        }
        this.save(voucherOrder);
    }
}
