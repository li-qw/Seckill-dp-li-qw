package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
@Autowired
private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result list1() {
        String key = "shop:type:list";
        // 1. 从 Redis List 查缓存
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (jsonList != null && !jsonList.isEmpty()) {
            List<ShopType> typeList = jsonList.stream()
                    .map(json -> JSONUtil.toBean(json, ShopType.class))
                    .collect(Collectors.toList());
            return Result.ok(typeList);
        }
        // 2. 缓存未命中，查数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 3. 逐个转为 JSON 写入 Redis List
        List<String> values = typeList.stream()
                .map(JSONUtil::toJsonStr)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, values);
        return Result.ok(typeList);
    }
}
