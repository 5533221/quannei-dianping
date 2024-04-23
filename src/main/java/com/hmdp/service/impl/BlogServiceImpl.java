package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private BlogServiceImpl blogService;

    @Override
    public Result queryByid(Long id) {
        //查询笔记
        Blog blog = getById(id);
        if (blog==null) {
            return Result.fail("笔记不存在");
        }
        //根据用户的id查询用户信息
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());

        //查询blog是否被当前用户点赞

        this.IsBlogLikeed(blog);
        return Result.ok(blog);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 保存探店博文
        blogService.save(blog);

        // 返回id
        return Result.ok(blog.getId());
    }


    public void IsBlogLikeed(Blog blog){

        String key="like:count:" + blog.getId();
        //从redis中判断 用户是否点赞  将点赞过的用户 set命令存起来 如果取消点赞 对应的赞也要减1
        UserDTO userDTO = UserHolder.getUser();
        if(userDTO==null){

            return;
        }

        Long UserId = userDTO.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, UserId.toString());
        //TODO
        if(score==null){
            return;
        }
        //设置当前为点过赞
        blog.setIsLike(true);
    }

    @Override
    public Result updateLikeCount(Long id) {


        String key="like:count:" + id;
        //从redis中判断 用户是否点赞  将点赞过的用户 set命令存起来 如果取消点赞 对应的赞也要减1
        UserDTO user = UserHolder.getUser();
        if (user==null){
            return  Result.fail("当前未登录");
        }
        Long UserId = user.getId();


        Double score = stringRedisTemplate.opsForZSet().score(key, UserId.toString());

        //说明有分数 点过赞  删除key
        if(score != null){

            //存在  删除该用户 在该笔记key 的对应的  值
//            stringRedisTemplate.opsForSet().remove(key, UserId.toString());
            //使用zSet
            stringRedisTemplate.opsForZSet().remove(key, UserId.toString());

            //修改数据库  减去1
            boolean isSuccess = blogService.update().setSql("liked = liked - 1").eq("id", id).update();

            if(isSuccess){
                return Result.ok();
            }

        }else{

            //未点赞  加一
//            stringRedisTemplate.opsForSet().add(key, UserId.toString());
            stringRedisTemplate.opsForZSet().add(key, UserId.toString(),System.currentTimeMillis());

            // 写入数据库修改点赞数量
            blogService.update()
                    .setSql("liked = liked + 1").eq("id", id).update();
        }

        return Result.ok();
    }

    @Override
    public Result queryHotList(Integer current) {

        // 根据用户查询
        Page<Blog> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());

            //查询当前用户是否点赞
            this.IsBlogLikeed(blog);
        });

        return Result.ok(records);
    }

    //查询笔记详情的 点赞列表  并且  top5 排序
    @Override
    public Result queryBloglikes(Long id) {


        String key="like:count:" + id;
        //从redis查询  top
        Set<String> set = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(set==null || set.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //将set集合中  遍历出userid 转为Long型
        List<Long> userids = set.stream().map(Long::valueOf).collect(Collectors.toList());
        //userids按逗号分割
        String idstr = StrUtil.join(",", userids);

        //查询所有点赞的用户 转为userDto 返回给前端，order by field (userids)  按照指定的顺序查询
       //转为DTO
        List<UserDTO> userDTOS = userService.query().in("id", userids).last("order by field(id," + idstr + ")").list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());


        return Result.ok(userDTOS);
    }
}
