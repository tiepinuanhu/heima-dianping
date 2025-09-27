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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;



    @Resource
    RedisIdWorker redisIdWorker;

    /**
     * 1. 查询优惠券信息（开始结束时间，库存）
     * 2. 校验时间和库存
     * 3. 创建订单
     * 4. 扣减库存
     * 5. 返回订单ID
     * 设计多张表的操作，所以加上事务
     */
    @Override
    @Transactional
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
        // 扣减库存
        boolean updated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", seckillVoucher.getVoucherId())
                .update();
        if (!updated) {
            return Result.fail("优惠券已经抢光");
        }
        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 使用Id生成器生成orderID
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());
        voucherOrder.setUserId(UserHolder.getUser().getId());

        this.save(voucherOrder);
        return Result.ok(orderId);
    }
}
