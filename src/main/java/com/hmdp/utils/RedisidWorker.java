package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisidWorker {
    //全局唯一id生成器
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP=1640995200L;
    private static final int TIME_BITS=32; //时间戳占的位数
    public long getId(String keyPrefix) {
        //1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);
        long diffSeconds = nowSeconds - BEGIN_TIMESTAMP;
        //2.生成序列号
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        //3.拼接返回

        return diffSeconds<<TIME_BITS|count;
    }
}
