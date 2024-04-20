package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO
 * @date 2024/4/16 21:17
 */
@Component
public class RedisClient {

    @Autowired
    private final StringRedisTemplate stringRedisTemplate;

    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    //设置逻辑过期
    public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit){

        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(time));

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData),time,unit);
    }

    //缓存 穿透 null
    public <R,ID> R queryWithPassThrough(String keyPrefix,
                                         ID id,
                                         Class<R> type,
                                         Function<ID,R> function,
                                         Long time,
                                         TimeUnit unit){
        //根据key查询redis 是否有缓存
        String key=keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //不为空 直接返回值
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        //为空    防止缓存穿透
        if("".equals(json)){
            return null;
        }
        //查询数据库
        R r= function.apply(id);
        if(r==null){
            //将空值存入Redis
            stringRedisTemplate.opsForValue().set(key,"", 2L,TimeUnit.MINUTES);
            return null;
        }
        //存在 存入Redis
        this.set(key,JSONUtil.toJsonStr(r),time,unit);

        return r;
    }


    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR=
            Executors.newFixedThreadPool(10);
    //缓存击穿
    public <R,ID> R queryWithLogicalExpire(String keyPrefix,
                                           ID id,
                                           Class<R> type,
                                           Function<ID,R> function,
                                           Long time,
                                           TimeUnit unit
                                           ){

        String key=keyPrefix+id;
        //获取redis中的缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //如果为空  直接返回null
        if(StrUtil.isBlank(json)){
            return null;
        }

       RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //转为对象
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();



        //判断缓存时间是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //未过期 直接返回数据
            return r;
        }
        //不为空  获取互斥锁
        boolean isLock = tryLock(key);
        if (isLock) {
            //开启线程
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R r1 = function.apply(id);
                    //存入redis
                    this.setWithLogicalExpire(key,r1,time,unit);

                } catch (Exception e) {

                    throw new RuntimeException(e);

                } finally {
                    unLock(key);
                }
            });
        }
        return r;
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






}
