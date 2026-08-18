package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisidWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;
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
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        //使用lua脚本实现
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);
    private static final ExecutorService SECKILL_ORDER_EXECUTOR =
            Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                VoucherOrder order = null;
                try {
                    order = orderTasks.take();
                    handleVoucherOrder(order);
                } catch (InterruptedException e) {
                    // 线程被中断（如应用关闭），退出消费循环
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // 业务异常不能杀死消费者线程，记录后继续处理下一个订单
                    log.error("处理秒杀订单异常: {}");
                }

            }
        }

        private void handleVoucherOrder(VoucherOrder order) throws InterruptedException {
            Long userId = order.getUserId();
            //创建锁对象
            RLock lock = redissonClient.getLock("lock:order:" + userId);
            boolean isLock = lock.tryLock(1L, TimeUnit.SECONDS);
            if (!isLock) {
                log.error("秒杀失败，用户已下单");
                return;
            }
            try {

                 proxy.createVoucherOrder(order);
            } finally {
                lock.unlock();
            }
        }
    }

    private volatile IVoucherOrderService proxy;

    @Override
//异步秒杀抢购
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        //0.校验秒杀时间窗口与优惠券存在性
        SeckillVoucher voucher = seckillService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("优惠券不存在");
        }
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        //1.执行lua脚本，
        Long userId = UserHolder.getUser().getId();
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //2.判断结果是否为0,不为0，没有购买资格，为0，把下单信息保存到阻塞队列
        int i = result.intValue();
        if (i != 0) {
            return Result.fail(i == 1 ? "库存不足" : "不能重复下单");
        }
        VoucherOrder order = new VoucherOrder();
        long orderId = redisidWorker.getId("order");
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        orderTasks.put(order);
        //3.返回下单结果id
        return Result.ok(orderId);
    }

    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
        //4.判断用户是否有购买过该优惠券
        Long userId = order.getUserId();
        Long voucherId = order.getVoucherId();

        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            log.error("用户已下单，不允许重复下单");
            // 下单失败，回滚 Redis 预减的库存和用户记录，避免 Redis 与 DB 库存漂移
            rollbackSeckillStock(voucherId, userId);
            return;
        }
        //5.扣减库存
        boolean success = seckillService.update().setSql("stock=stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!success) {
            log.error("库存不足");
            // 下单失败，回滚 Redis 预减的库存和用户记录，避免 Redis 与 DB 库存漂移
            rollbackSeckillStock(voucherId, userId);
            return;
        }
        //6.创建订单
        save(order);
    }

    /**
     * 回滚 Lua 脚本中已预减的 Redis 库存和用户购买记录
     */
    private void rollbackSeckillStock(Long voucherId, Long userId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + voucherId, 1);
        stringRedisTemplate.opsForSet().remove("seckill:order:" + voucherId, userId.toString());
    }
}
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillService.getById(voucherId);
//        //2.判断秒杀是否开始或结束
//        if (voucher.getBeginTime().isAfter
//                (LocalDateTime.now())) {
//            return Result.fail("秒杀尚未开始");
//        }
//        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//        //3.判断库存是否足够
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //创建锁对象
//       // SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock(1L,TimeUnit.SECONDS);
//        if (!isLock) {
//            return Result.fail("不能重复购买");
//        }
//        try {
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            lock.unlock();
//        }
//
//    }


