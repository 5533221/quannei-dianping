package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO  简单锁的实现
 * @date 2024/4/19 16:19
 *
 */
public class simpleLock implements ILock {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //业务
    private String name;
    private static final String key_prefix = "Lock:";
    //生成不同的uuid
    private static final String id_prefix = UUID.randomUUID().toString(true) + "-";

    public simpleLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    //定义RedisScript
    private static final DefaultRedisScript<Long> Un_Script;

    static {

        Un_Script = new DefaultRedisScript();
        Un_Script.setLocation(new ClassPathResource("unlock.lua"));
        Un_Script.setResultType(Long.class);
    }


    @Override
    public boolean tryLock(Long timeOut) {
        //获取线程标识   key为lock:业务名称  value为 线程的id
        String ThreadId = id_prefix + Thread.currentThread().getId();


        Boolean isLock = stringRedisTemplate.opsForValue()
                .setIfAbsent(key_prefix + name, ThreadId, timeOut, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(isLock);

    }

    @Override
    public void unLock() {

        stringRedisTemplate
                .execute(Un_Script,
                        Collections.singletonList(key_prefix + name),
                        id_prefix + Thread.currentThread().getId()
                );
    }


    //使用下面的锁 容易线程争抢锁的问题
    //解决 使用Lua脚本  保证原子性
//    @Override
//    public void unLock() {
//
//        //获取线程标识   key为lock:业务名称  value为 线程的id
//        String ThreadId =id_prefix+ Thread.currentThread().getId();
//
//        //获取当前redis中的线程id
//        String id = stringRedisTemplate.opsForValue().get(key_prefix + name);
//
//        //只要当前线程中的id和redis中的id相等  就可以删除key 让其他的线程加入
//        if (ThreadId.equals(id)) {
//            stringRedisTemplate.delete(key_prefix+name);
//        }
//    }
}
