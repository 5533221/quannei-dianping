package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.simpleLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;



    //注入id自增器
    @Resource
    private RedisIDWorker redisIDWorker;

    @Autowired
    private RedissonClient redissonClient;

    @Override
    public Result BuySeckillVoucher(Long voucherId) {

        //根据id查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {

            //未开始 返回异常结果
            return Result.fail("秒杀还未开始！");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        //是 判断是否有库存
        if (voucher.getStock() < 1) {
            //无库存  返回异常
            return Result.fail("优惠券暂无库存");
        }

        Long userId = UserHolder.getUser().getId();
        //获取锁
        //simpleLock lock = new simpleLock(stringRedisTemplate,"order:"+userId);

        //使用Redisson获取锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean tryLock = lock.tryLock();
        if(!tryLock){
            //为获取锁
            return Result.fail("不允许重复下单");
        }

        try {

            //添加事务  使用Aop 使用Spring的事务
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();

            return proxy.CreateVoucherOrder(voucherId);
        } catch (IllegalStateException e) {

            throw new RuntimeException(e);
        } finally {
            //释放锁
            lock.unlock();
        }
    }


    //创建订单
    @Transactional
    public Result CreateVoucherOrder(Long voucherId) {


        //获取用户
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();

        //实现一人一单
        //根据优惠券id和用户id查询订单 判断订单是否存在
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        //存在 返回异常
        if (count > 0) {
            return Result.fail("你已经抢到了过一单!");
        }

        //不存在
        //有库存  库存   减1
        boolean isAlter = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!isAlter) {

            return Result.fail("暂无库存！");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置订单id  使用redisIdworker

        long id = redisIDWorker.nextId("order");
        voucherOrder.setId(id);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        save(voucherOrder);
        //返回订单id
        return Result.ok(id);

    }

}
