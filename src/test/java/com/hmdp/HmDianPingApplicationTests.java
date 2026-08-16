package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisidWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {

@Autowired
private ShopServiceImpl shopService;
@Autowired
private RedisidWorker redisidWorker;
    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    void contextLoads() {
       shopService.saveShop(1L,10L);
    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisidWorker.getId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        // 创建300个任务提交线程池
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }

        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
}}
