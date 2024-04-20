package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO
 * @date 2024/4/18 16:59
 */
@Component
public class RedisIDWorker {

    private Long start_time=1704067200L;

    private Long count_BITS=32L;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIDWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix){
        //生成时间戳
        long end_time = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        long times_tamp= end_time-start_time;

        //2.1以当前的时间  生成序列号
        String now_date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));


        //2.2自增长
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + now_date);

        //减掉的时间戳 然后向左边移 32位  32都是0补充  然后与自增长的数字 相或  只要有一个真就为真
        return times_tamp << count_BITS | count;
    }

}
