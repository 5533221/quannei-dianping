package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 27164
 * @version 1.0
 * @description: TODO
 * @date 2024/4/20 10:48
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient getRedisClient(){
        //添加集群地址
        //config.useClusterServers();
        Config config = new Config();
        //配置单一
        config.useSingleServer().setAddress("redis://192.168.183.135:6379");

        return   Redisson.create(config);
    }

}
