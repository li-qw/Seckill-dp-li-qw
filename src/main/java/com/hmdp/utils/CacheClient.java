package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);

    public void set(String key, Object value, long timeout,TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), timeout, timeUnit);
    }
    public void setWithLogicalExpire(String key, Object value, long timeout,TimeUnit Unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(Unit.toSeconds(timeout)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T,ID> T queryWithPassThrough(String keyPrefix, ID id, Class<T> clazz,
                                         Function<ID,T> queryFunc, long timeout,TimeUnit Unit)  {
        String key = keyPrefix + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(Json)) {
            return JSONUtil.toBean(Json, clazz);
        }
        //缓存穿透处理
        if (Json != null) {
            return null;
        }
        T t = queryFunc.apply(id);
        if (t == null) {
            // 缓存穿透，空值缓存
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
       this.set(key,t,timeout,Unit);
        return t;

    }
    public <R,ID>R querywithlogicalExpire(String keyPrefix,ID id,Class<R> clazz,Function<ID,R> queryFunc
            , long timeout,TimeUnit Unit) {
        String key = keyPrefix + id;
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        String Json = stringRedisTemplate.opsForValue().get(key);
        // 不处理缓存穿透：单纯演示逻辑过期方案0
        if (StrUtil.isBlank(Json)) {

            return null;

        }
        RedisData redisData = JSONUtil.toBean(Json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject)redisData.getData(), clazz);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        if(trylock(lockKey)){
            //开启独立线程，缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    R r1 = queryFunc.apply(id);
                    this.setWithLogicalExpire(key,r1,timeout,Unit);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
                finally {
                    unlock(lockKey);
                }

            });
        }

        return r;

    }
    private boolean trylock(String key) {
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return result != null && result;
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
