package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
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


    private static final DefaultRedisScript<Long> seckillScript;

    static {
        seckillScript = new DefaultRedisScript();
        seckillScript.setLocation(new ClassPathResource("seckill.lua"));
        //返回类型
        seckillScript.setResultType(Long.class);
    }

    //阻塞队列
    private BlockingQueue<VoucherOrder> blockingQueue =
            new ArrayBlockingQueue(1024 * 1024);

    //事务
    private IVoucherOrderService proxy;

    //开启线程池
    private static final ExecutorService hander_Seckill = Executors.newSingleThreadExecutor();

    //使用stream结构 处理   实现runable
    public class handlerSeckill implements Runnable {

        String queueName="stream.orders";
        @Override
        public void run() {

            //执行下单操作
            while (true) {
                try {
                    //在主线程中执行了Lua脚本  将(用户id 订单id 优惠券id)-->也就是voucherOrder类的属性，为了更好保存在数据库 存入了redis队列，
                    // 此时只要在这个线程中 获取消息队列中是否有信息，他会将信息转为list集合

                    //获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams stream.orders >
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    //2.判断消息是否获取成功
                    if (recordList==null || recordList.isEmpty()) {
                        //如果获取失败，说明没有消息 继续往下走
                        continue;
                    }

                    //3.说明有消息  将获取的list转为voucherOrder
                    MapRecord<String, Object, Object> record = recordList.get(0);
                    Map<Object, Object> valueMap = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(valueMap,new VoucherOrder(), true);
                    //4.获取信息成功 创建订单
                    HandlerVoucherOrder(voucherOrder);

                    //5.确认ack
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());


                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //处理异常
                    handlerPendingList();
                }

            }
        }

        //处理pending-list
        private void handlerPendingList() {

            while (true){

                try {
                    //获取pending-list中的订单信息 xreadgroup group g1 c1 count 1  streams stream.orders 0
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //判断是否成功
                    //2.判断消息是否获取成功
                    if (recordList==null || recordList.isEmpty()) {

                        //如果获取失败，说明没有消息 继续往下走
                        continue;

                    }
                    //解析订单的信息
                    MapRecord<String, Object, Object> record = recordList.get(0);

                    Map<Object, Object> map = record.getValue();

                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map,new VoucherOrder(), true);
                    //4.获取成功 创建订单
                    HandlerVoucherOrder(voucherOrder);

                    //5.确认ack
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {

                    log.error("处理pending-list订单异常",e);

                    try {
                        Thread.sleep(20);

                    } catch (InterruptedException ex) {

                        ex.printStackTrace();
                    }
                }

            }

        }

    }



    //处理创建订单的逻辑
    private void HandlerVoucherOrder(VoucherOrder voucherOrder) {

        //获取用户id
        Long userId = voucherOrder.getUserId();
        //判断是否有锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            //无锁 打印
            log.error("不能重复下单");
            return;
        }
        //有锁  添加事务
        try {
            proxy.CreateVoucherOrder(voucherOrder);

        } catch (Exception e) {
            e.printStackTrace();

        } finally {

            lock.unlock();
        }
    }

    //初始化时 加载线程
    @PostConstruct
    public void init() {

        hander_Seckill.submit(new handlerSeckill());
    }



    //使用stream结构和Lua脚本
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

        //使用Lua脚本 判断 库存 和 是否符合购买资格(一人一单)
        Long userid = UserHolder.getUser().getId();
        //生成orderId 使用id生成器
        long orderId = redisIDWorker.nextId("order");

        //在主线程中初始化事务-->线程中不能事务  代理对象 为了事务
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //执行Lua脚本
        Long result = stringRedisTemplate.execute(seckillScript,
                Collections.emptyList(),
                voucherId.toString(),
                userid.toString(),
                String.valueOf(orderId)
        );
        int i = result.intValue();
        //1 就是 库存不足 2 说明已经下过单
        if ( i != 0) {
            return Result.fail(i == 1 ? "库存不足" : "已经下单过");
        }

        return Result.ok(orderId);
    }







/*


    //基于阻塞队列 实现runable
    public class handlerSeckill implements Runnable {

        @Override
        public void run() {
            //执行下单操作
            while (true) {
                try {

                    VoucherOrder voucherOrder = blockingQueue.take();
                    HandlerVoucherOrder(voucherOrder);

                } catch (InterruptedException e) {
                    log.error("处理订单异常", e);
                }

            }
        }
    }



    //使用阻塞队列和Lua脚本
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

        //使用Lua脚本 判断 库存 和 是否符合购买资格(一人一单)
        Long userid = UserHolder.getUser().getId();

        Long result = stringRedisTemplate.execute(seckillScript,
                Collections.emptyList(),
                voucherId.toString(),
                userid.toString()
        );
        int i = result.intValue();
        if ( i != 0) {

            return Result.fail(i == 1 ? "库存不足" : "已经下单过");
        }

        //生成id 使用id生成器
        long orderId = redisIDWorker.nextId("order");

        //将voucherOrder返回到阻塞队列
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userid);

        //初始化  代理对象 为了事务
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //将订单id和用户id 传入到jvm的阻塞队列中  异步下单
        blockingQueue.add(voucherOrder);

        return Result.ok(orderId);
    }


*/


   /* @Override
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
    }*/


    //创建订单
    @Transactional
    public void CreateVoucherOrder(VoucherOrder voucherOrder) {
        //异步 只能从这里获取用户的id
        Long userId = voucherOrder.getUserId();
        //实现一人一单
        //根据优惠券id和用户id查询订单 判断订单是否存在
        int count = query().eq("user_id", userId)
                .eq("voucher_id", voucherOrder.getVoucherId())
                .count();
        //存在 返回异常
        if (count > 0) {
            log.error("你已经抢到了过一单!");
            return;
        }

        //不存在
        //有库存  库存   减1
        boolean isAlter = seckillVoucherService.update()
                .setSql("stock = stock-1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!isAlter) {

            log.error("暂无库存！");
            return;
        }

        //创建订单
        save(voucherOrder);

    }
}
