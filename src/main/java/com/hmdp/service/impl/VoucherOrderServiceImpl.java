package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.SimpleRedisLock;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


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
     * 1. 查询优惠券信息（开始结束时间，库存）
     * 2. 校验时间和库存
     * 3. 创建订单
     * 4. 扣减库存
     * 5. 返回订单ID
     */
    @Override
    public Result secKillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();
        Integer stock = seckillVoucher.getStock();
        // 判断是否符合优惠券的时间段
        if (LocalDateTime.now().isBefore(beginTime)) {
            return Result.fail("秒杀未开始");
        }
        if (LocalDateTime.now().isAfter(endTime)) {
            return Result.fail("秒杀已结束");
        }
        // 判断优惠券是否有库存
        if (stock <= 0) {
            return Result.fail("优惠券已经抢光");
        }
        Long userId = UserHolder.getUser().getId();
        // 确保事务提交后，再释放锁
        // 否则，事务还没提交，还没写入数据库，就释放了锁
        // 其他线程判断没有订单，从而进行了插入订单，从而导致了并发安全问题
//        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
//        boolean locked = lock.tryLock(1200);

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean locked = lock.tryLock();
        // 获取锁失败，证明该用户有多个线程同时下单，这是不允许的
        if (!locked) {
            return Result.fail("不允许多次下单");
        }
        // 成功获取锁
        try {
            // 获取当前代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
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
}
