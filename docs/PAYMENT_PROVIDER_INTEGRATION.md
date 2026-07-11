# Zens 支持者支付接入说明

## 当前状态

支持者方案、现金订单、支付回调审计和权益期限已经独立建模，但生产环境默认：

```properties
PAYMENT_ENABLED=false
PAYMENT_PROVIDER=disabled
```

因此当前页面只展示方案，不会发生真实扣款。`mock` 仅供本地自动化测试，应用在 `prod` / `production` profile 下发现 mock 配置会拒绝启动。

## 个人经营者应准备什么

优先选择支付机构官方商户平台或持牌聚合支付服务商，不使用个人收款码模拟网站支付。通常需要准备：

1. 与网站经营主体一致的个人经营者/个体工商户资料；
2. 已备案且可正常访问的域名、清晰的商品或服务说明、价格、交付期限；
3. 用户协议、隐私政策、退款规则、客服联系方式；
4. 结算银行卡以及服务商要求的经营场景材料；
5. 商户号、应用 ID、API 密钥/私钥、平台公钥或平台证书、回调地址；
6. 在服务商后台配置 HTTPS 回调：`https://www.allinsong.top/api/payment/callback/{provider}`。

具体准入材料、费率和个人经营者可用产品会随服务商与地区变化，应以签约时的官方商户平台规则为准。

## 新增真实支付渠道

项目已经内置支付宝电脑网站支付适配器 `alipay`。签约并取得开放平台参数后，在服务器密钥配置中设置：

```properties
PAYMENT_ENABLED=true
PAYMENT_PROVIDER=alipay
ALIPAY_APP_ID=<应用 ID>
ALIPAY_MERCHANT_PRIVATE_KEY=<应用 RSA2 PKCS#8 私钥>
ALIPAY_PUBLIC_KEY=<支付宝开放平台公钥>
ALIPAY_NOTIFY_URL=https://www.allinsong.top/api/payment/callback/alipay
ALIPAY_RETURN_URL=https://www.allinsong.top/supporter
ALIPAY_SELLER_ID=<可选，配置后会校验通知中的收款方>
```

适配器创建 `alipay.trade.page.pay` 收银台 URL，使用 `SHA256withRSA`（RSA2）签名；异步通知会校验支付宝公钥签名、`app_id`、可选 `seller_id`、服务商交易号、订单号、金额、币种、交易状态和支付时间。同步跳转只用于回到订单页面，不会发放权益。

如后续新增其他真实渠道，适配器必须实现：

```java
com.campus.trend.campus_pulse.payment.PaymentProvider
```

其中：

- `createCheckout` 使用主站生成的 `orderNo` 作为服务商侧幂等业务单号；
- 订单金额只能读取服务端 `PaymentOrder.amountCents`，不能接受客户端金额；
- `verifyCallback` 必须使用服务商平台证书/公钥或官方指定的 MAC 算法验签；
- 必须校验时间戳/证书序列号等防重放字段；
- 返回的事件 ID、商户订单号、金额、币种和支付时间必须来自验签后的报文；
- 严禁仅依据浏览器跳转页面、查询参数或客户端“支付成功”状态发放权益。

启用真实渠道时，通过服务器密钥管理设置：

```properties
PAYMENT_ENABLED=true
PAYMENT_PROVIDER=<provider-code>
PAYMENT_ORDER_EXPIRE_MINUTES=30
```

商户私钥、API 密钥和平台证书不得写入 Git、前端环境变量或日志。

## 上线前财务与运营闸门

真实支付启用前至少完成：

- 服务商沙箱/测试环境的下单、重复下单、取消、超时和重复回调测试；
- 错误金额、错误币种、错误签名、过期回调和未知订单测试；
- 日对账与异常订单告警；
- 明确退款入口、退款时效及权益撤销规则；
- 客服与投诉处理流程；
- 订单、回调事件与结算单三方对账；
- 小额灰度和人工复核，再逐步放量。

首期建议采用固定 30 天支持计划，不做自动续费。自动续费涉及单独签约、扣款授权、续费提醒和解约能力，应在真实留存与付费意愿验证后再实现。
