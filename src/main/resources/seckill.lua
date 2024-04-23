--1.参数列表
--1.1优惠券的id
local voucherId=ARGV[1]
--1.2用户的id
local userId=ARGV[2]
--1.3 订单的id
local orderId=ARGV[3]

--key
local stockKey='seckill:stock:' ..voucherId

local orderKey='seckill:order:' .. voucherId

--脚本业务
--判断库存

local seckillValue=tonumber(redis.call('get',stockKey))

if seckillValue == nil then

    return 1
elseif (seckillValue <= 0)then
    --库存不足
    return 1
end

if (redis.call('sismember',orderKey,userId) == 1) then
    --说明已经下过单
    return 2;
end

--扣减库存
redis.call('incrby',stockKey,-1)
--将用户存入set集合
redis.call('sadd',orderKey,userId)
--将userId voucherId orderId存入队列中  xadd stream.orders * k1 v1 k2 v2
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)

return 0