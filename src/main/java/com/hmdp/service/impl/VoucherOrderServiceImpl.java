package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisidWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private ISeckillVoucherService seckillService;
    @Autowired
    private RedisidWorker redisidWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @Override

    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //1.查询优惠券
        SeckillVoucher voucher = seckillService.getById(voucherId);
        //2.判断秒杀是否开始或结束
        if (voucher.getBeginTime().isAfter
                (LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //3.判断库存是否足够
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
       // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock(1L,TimeUnit.SECONDS);
        if (!isLock) {
            return Result.fail("不能重复购买");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //4.判断用户是否有购买过该优惠券
        Long userId = UserHolder.getUser().getId();

        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("你已经购买过该优惠券");
        }
        //5.扣减库存
        boolean success = seckillService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //6.创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisidWorker.getId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        save(order);
        //7.返回订单id

        return Result.ok(orderId);
    }
}
