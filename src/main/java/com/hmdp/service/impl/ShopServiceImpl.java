package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR= Executors.newFixedThreadPool(10);
@Autowired
private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {

        //用互斥锁解决缓存击穿外加穿透
        //Shop shop = querywithmutex(id);

        //处理缓存穿透
        //Shop shop = querywithPassThrough(id);
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id1 -> getById(id1),
         RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //用逻辑过期解决缓存击穿
        //Shop shop = querywithlogicalExpire(id);
        //Shop shop = cacheClient.querywithlogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, id1 -> getById(id1),
               // RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("商铺不存在");
        }

        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        //1.校验参数
        if (shop.getId() == null) {
            return Result.fail("商铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

//    private boolean trylock(String key) {
//        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return result != null && result;
//    }
//
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }
//
//    private Shop querywithmutex(Long id)  {
//
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        if (shopJson != null) {
//            return null;
//        }
//        // 尝试获取互斥锁
//        Shop shop = null;
//        try {
//            if (!trylock(lockKey)) {
//                // 获取锁失败，休眠后递归重试
//                Thread.sleep(50);
//                return querywithmutex(id);
//            }
//            // 查数据库
//            //拿到锁之后，再查一遍Redis！这就是double check
//            String json = stringRedisTemplate.opsForValue().get(key);
//            if(StrUtil.isNotBlank(json)){
//                return JSONUtil.toBean(json,Shop.class);
//            }
//            //缓存确实没数据，才去查数据库
//            shop = getById(id);
//            if (shop == null) {
//                // 缓存穿透，空值缓存
//                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//                return null;
//            }
//            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
//                    RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(1, 5), TimeUnit.MINUTES);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        } finally {
//            unlock(lockKey);
//        }
//        return shop;
//
//
//    }
//
//    private Shop querywithPassThrough(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        if (StrUtil.isNotBlank(shopJson)) {
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//        //缓存穿透处理
//        if (shopJson != null) {
//            return null;
//        }
//        Shop shop = getById(id);
//        if (shop == null) {
//            // 缓存穿透，空值缓存
//            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return null;
//        }
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),
//                RedisConstants.CACHE_SHOP_TTL + RandomUtil.randomInt(1, 5), TimeUnit.MINUTES);
//        return shop;
//
//    }
//    private Shop querywithlogicalExpire(Long id) {
//        String key = RedisConstants.CACHE_SHOP_KEY + id;
//        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        // 不处理缓存穿透：单纯演示逻辑过期方案0
//        if (StrUtil.isBlank(shopJson)) {
//
//                return null;
//
//        }
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Object data = redisData.getData();
//        Shop shop = JSONUtil.toBean((JSONObject) data, Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        if(expireTime.isAfter(LocalDateTime.now())){
//           return shop;
//        }
//        if(trylock(lockKey)){
//            // Double Check：确认缓存是否已被其他线程重建
//            shopJson = stringRedisTemplate.opsForValue().get(key);
//            if (StrUtil.isNotBlank(shopJson)) {
//                RedisData newRedisData = JSONUtil.toBean(shopJson, RedisData.class);
//                if (newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
//                    unlock(lockKey);
//                    return JSONUtil.toBean((JSONObject) newRedisData.getData(), Shop.class);
//                }
//            }
//            //开启独立线程，缓存重建
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    this.saveShop(id,30L);
//                }
//                catch (Exception e)
//                {
//                   throw new RuntimeException(e);
//                }
//                finally {
//                    unlock(lockKey);
//                }
//
//            });
//        }
//
//        return shop;
//
//    }
//    public void saveShop(Long id,Long ttl) {
//        //逻辑过期封装
//        Shop shop = getById(id);
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusMinutes(ttl));
//        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,
//                JSONUtil.toJsonStr(redisData));
//
//    }
}
