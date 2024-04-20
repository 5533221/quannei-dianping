package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.UserServiceImpl;
import com.hmdp.utils.RedisClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.*;
import java.util.HashMap;
import java.util.List;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    public RedisClient cacheClient;

    @Test
    void name() throws InterruptedException {

//        shopService.addCacheTime(1L,10L);
        Shop shop = shopService.getById(2L);
        cacheClient.setWithLogicalExpire("cache:shop:2", shop, 10L, TimeUnit.MINUTES);

    }

    @Test
    void testAddUser() throws IOException {

        String login_prefix = "login:token:";
        List<User> Userlist = userService.lambdaQuery().last("limit 1000").list();
        for (User user : Userlist) {

            String token = UUID.randomUUID().toString(true);
            String tokenkey = login_prefix + token;

            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            //把user转换为map 并把userDTO中的所有字段的值改为String类型，因为
            Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO,
                    new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true) // 是否忽略一些空值
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()) //修改字段值
            );

            //获取所有用户的token  以 login:token:开头的
            Set<String> keys = stringRedisTemplate.keys(login_prefix+"*");

            //存入redis
            stringRedisTemplate.opsForHash().putAll(tokenkey, userDTOMap);
            //设置token时间  30分钟
            stringRedisTemplate.expire(tokenkey, 600L, TimeUnit.MINUTES);

            FileWriter writer = new FileWriter(System.getProperty("user.dir") + "\\token.txt");

            BufferedWriter bufferedWriter = new BufferedWriter(writer);

            assert keys != null;
            for (String key : keys) {

                String tokens = key.substring(login_prefix.length());
                String text=tokens+"\n";
                bufferedWriter.write(text);
            }



        }


    }
}
