package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;


//    发送验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {

        //验证手机号 是否符合规则
        if (RegexUtils.isPhoneInvalid(phone)) {

            return Result.fail("请输入正确的手机号码!!!!!");
        }
        //生成验证码  使用hutool的RandomUtil  6位验证码
        String code = RandomUtil.randomNumbers(6);

        //存入Redis 并设置过期时间 2分钟有效
        stringRedisTemplate.opsForValue().set("login:code:"+phone,code,2L, TimeUnit.MINUTES);


        //发送验证码
        log.debug(("发送验证码成功,验证码:"+code));

        return Result.ok();
    }


    //登录功能
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //校验手机号码是否符合
        if (RegexUtils.isPhoneInvalid(phone)) {

            return Result.fail("请填写正确的手机号");
        }

        //校验验证码 与 Redis传递的是否一致
        String code = loginForm.getCode();

       String cacheCode =stringRedisTemplate.opsForValue()
               .get("login:code:"+phone);

        //不一致报错
        if ( cacheCode=="" || !code.equals(cacheCode)) {
            return Result.fail("验证码错误!!");
        }
        //一致  根据手机号码查询用户
        User user = query().eq("phone",phone).one();

        if (user == null) {
        //不存在 创建新用户  保存到数据库
           user= createUser(phone);
           save(user);
        }

        //生成token  随机数
        String token = UUID.randomUUID().toString(true);
        String tokenkey = "login:token:" + token;


        //转为 DTO返回给前端  不需要把整个用户的信息返回
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //把user转换为map 并把userDTO中的所有字段的值改为String类型，因为
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO,
                new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true) // 是否忽略一些空值
                        //fieldName：字段名
                        //fieldValue：字段值
                        //返回值：修改后的字段值
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()) //修改字段值
        );


        //存入redis
        stringRedisTemplate.opsForHash().putAll(tokenkey,userDTOMap);

        //设置token时间  30分钟
        stringRedisTemplate.expire(tokenkey,1000L,TimeUnit.MINUTES);

        log.error(token);


        //返回  token
        return Result.ok(token);
    }

    //创建用户
    private User createUser(String phone) {

        User user1 = new User();
        user1.setPhone(phone);
        user1.setNickName("user_1"+RandomUtil.randomNumbers(6));

        return user1;
    }
}
