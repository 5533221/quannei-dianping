package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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


    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public Result queryTypelist() {


        String key = stringRedisTemplate.opsForValue().get("shop:type");

        //查询商品类型缓存
        if (StrUtil.isNotBlank(key)) {
            //存在返回给前端
            List<ShopType> shopType = JSONUtil.toList(key, ShopType.class);
            return Result.ok(shopType);
        }
        //如果不存在，从数据库中查询
        List<ShopType> list = query().orderByAsc("sort").list();
        if (list.size()==0) {
            //查询不存在
            return Result.fail("商品类型不存在");
        }
        //查询存在 存入redis
        stringRedisTemplate.opsForValue()
                .set("shop-type", JSONUtil.toJsonStr(list));

        return Result.ok(list);
    }
}
