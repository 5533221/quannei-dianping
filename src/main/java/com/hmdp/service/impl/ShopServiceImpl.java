package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.injector.methods.Update;
import com.baomidou.mybatisplus.core.injector.methods.UpdateById;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisClient redisClient;

    //根据id查询商铺  缓存
    @Override
    public Result queryByid(Long id) {
        //解决缓存穿透的问题
        Shop shop = queryWithPassThrough(id);

        //使用 互斥锁 解决缓存击穿问题
        //Shop shop = queryWithMutex(id);
        //使用 逻辑过期 解决缓存击穿问题
        //Shop shop = queryLogicalCache(id);

        //Shop shop = redisClient.queryWithPassThrough("cache:shop:",id,Shop.class,this::getById,10L,TimeUnit.MINUTES);

        //缓存击穿的测试
//        Shop shop = redisClient.queryWithLogicalExpire("cache:shop:", id, Shop.class, this::getById, 20L, TimeUnit.MINUTES);
        if (shop==null) {
            return Result.fail("店铺不存在");
        }


        return Result.ok(shop);
    }

    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR=
            Executors.newFixedThreadPool(10);

    //使用逻辑锁解决缓存击穿
    public Shop queryLogicalCache(Long id){

        String key = "cache:shop:"+id;
        //1.获取redis中的缓存
        String cache = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(cache)){
            //3.存在直接返回
            return null;
        }
        //4.将redis中的数据 反序列化
        RedisData redisData = JSONUtil.toBean(cache, RedisData.class);
        //转为jsonObject  再调用JsonUtil的toBean方法
        Shop shop =JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime localDateTime = redisData.getExpireTime();

        //5.判断缓存时间是否过期
        if (localDateTime.isAfter(LocalDateTime.now())) {
            //5.1未过期  直接返回信息
            return shop;
        }
        //5.2已过期 缓存重建
        // 获取互斥锁
        String LockKey="lock:shop:" + id;
        boolean isLock = tryLock(LockKey);

        if (isLock) {
            // 有互斥锁 开启新的线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //重建缓存
                    this.addCacheTime(id,20L);

                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unLock(LockKey);
                }

            });

        }
        // 无互斥锁 返回过期的商铺信息
        return shop;

    }

    //添加 缓存时间
    public void addCacheTime(Long id,Long expireSeconds) throws InterruptedException {

        //获取店铺的信息
        Shop shop = getById(id);
        Thread.sleep(200);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        //设置过期时间  当前时间加上 设定的秒数
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //存入redis

        stringRedisTemplate.opsForValue().set("cache:shop:"+id,JSONUtil.toJsonStr(redisData));

    }





    //解决缓存击穿问题
    public Shop queryWithMutex(Long id){

        String key = "cache:shop" + id;
        //1.从redis中查询商铺缓存
        String cache = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        //2.1命中，返回商铺的信息
        if(StrUtil.isNotBlank(cache)){
            //3.json转为bean
            return JSONUtil.toBean(cache,Shop.class);
        }
        //防止缓存穿透  如果当前的缓存为空字符
        if("".equals(cache)){

            return null;
        }

        String shopKey = null;
        Shop shop = null;
        try {
            shopKey = "lock:shop:" + id;
            //4.不命中
            //4.1  尝试获取互斥锁
            boolean isLock = tryLock(shopKey);
            //4.2  没有获取到互斥锁 休眠一段时间 重新查询缓存 在判断
            if(!isLock){
                //休眠
                Thread.sleep(200);
                //递归  查询缓存 判断
                return queryWithMutex(id);
            }
            //4.3  获取到互斥锁，根据id查询数据库，
            shop = getById(id);

            //模拟线程 阻塞
            Thread.sleep(200);

            //防止缓存穿透
            //判断商铺是否存在
            //不存在直接报错404
            if (shop==null) {
                //将空值存入redis
                stringRedisTemplate.opsForValue().set(key,"",2L,TimeUnit.MINUTES);
                return null;
            }

            //4.4 将商铺的数据存入redis  设置超时时间 30分钟
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);

        } catch (InterruptedException e) {

          throw new RuntimeException(e);

        } finally {

            //4.4  释放互斥锁
            unLock(shopKey);
        }

        //3.2.1 返回商铺信息
        return shop;
    }

    //尝试获取锁 sennx key ...
    public boolean tryLock(String key) {

        //得到互斥锁
        Boolean absent = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        return BooleanUtil.isTrue(absent);
    }

    //释放锁  删除掉redis中的key
    public void unLock(String key) {

        stringRedisTemplate.delete(key);
    }


    //缓存穿透的解决
    public Shop queryWithPassThrough(Long id){
        String key = "cache:shop" + id;
        //1.从redis中查询商铺缓存
        String cache = stringRedisTemplate.opsForValue().get(key);

        //2.判断缓存是否命中
        //2.1命中，返回商铺的信息
        if(StrUtil.isNotBlank(cache)){
            //json转为bean
            return JSONUtil.toBean(cache,Shop.class);
        }
        //防止缓存穿透
        //2.2不命中 查询数据库
        if("".equals(cache)){

            return null;
        }
        Shop shop = getById(id);


        //防止缓存穿透
        //3判断商铺是否存在
        //3.1不存在直接报错404
        if (shop==null) {

            //将空值存入redis
            stringRedisTemplate.opsForValue().set(key,"",2L,TimeUnit.MINUTES);
            return null;

        }
        //3.2存在 将商铺存入redis  设置超时时间 30分钟
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);
        //3.2.1 返回商铺信息
        return shop;
    }



    /**
     * 更新完数据之后  删除redis的缓存
     * */
    @Override
    @Transactional
    public Result updateByid(Shop shop) {

        Long id = shop.getId();
        if (id==null) {

            return Result.fail("修改失败,无id");
        }
        //修改商品
        updateById(shop);

        //删除缓存
        stringRedisTemplate.delete("cache:shop" + id);

        return Result.ok();
    }
}
