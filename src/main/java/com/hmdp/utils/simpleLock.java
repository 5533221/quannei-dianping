package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO  简单锁的实现
 * @date 2024/4/19 16:19
 */
public class simpleLock implements ILock{

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //业务
    private String name;
    private static final String key_prefix="Lock:";

    public simpleLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeOut) {
        //获取锁  key为lock:业务名称  value为 线程的id
        long id = Thread.currentThread().getId();

        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key_prefix + name, id + "", timeOut, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isLock);
    }

    @Override
    public void unLock() {
        stringRedisTemplate.delete(key_prefix+name);
    }
}
