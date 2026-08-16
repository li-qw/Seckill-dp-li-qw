package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.core.io.ClassPathResource;

import java.util.Collections;
import java.util.concurrent.TimeUnit;


public class SimpleRedisLock implements ILock {
    //分布式锁实现
    private String name;

    private StringRedisTemplate stringRedisTemplate;
    private static final String s=UUID.randomUUID().toString(true)+"";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        //使用lua脚本实现
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean trylock(long timeoutSec) {
       String threadId =s+Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent("lock:" + name,
                threadId, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unlock() {
        //基于lua脚本实现,实现判断删除操作原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList("lock:" + name),
                s+Thread.currentThread().getId());
        }
//    public void unlock() {
//        String threadId =s+Thread.currentThread().getId();
//        String id = stringRedisTemplate.opsForValue().get("lock:" + name);
//        if (threadId.equals(id)) {
//            stringRedisTemplate.delete("lock:" + name);
//        }
    }
