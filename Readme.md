## Spring Redis 并发工具

### 1 在SpringBoot中使用

* 引入Redis
```java
@Configuration
@EnableCaching
public class RedisConfig extends CachingConfigurerSupport {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("${spring.redis.host}")
    private String host;

    @Value("${spring.redis.port}")
    private int port;

    @Value("${spring.redis.timeout}")
    private int timeout;

    @Value("${spring.redis.password}")
    private String password;

    @Value("${spring.redis.database}")
    private int database;

    @Value("${spring.redis.pool.max-idle}")
    private int maxIdle;

    @Value("${spring.redis.pool.min-idle}")
    private int minIdle;

    /**
     * 注解@Cache key生成规则
     */
    @Bean
    public KeyGenerator keyGenerator() {
        return new KeyGenerator() {
            @Override
            public Object generate(Object target, Method method, Object... params) {
                StringBuilder sb = new StringBuilder();
                sb.append(target.getClass().getName());
                sb.append(method.getName());
                for (Object obj : params) {
                    sb.append(obj.toString());
                }
                return sb.toString();
            }
        };
    }

    /**
     * 注解@Cache的管理器，设置过期时间的单位是秒
     *
     * @param redisTemplate
     * @return
     * @Description:
     */
    @Bean
    public CacheManager cacheManager(RedisTemplate redisTemplate) {
        RedisCacheManager cacheManager = new RedisCacheManager(redisTemplate);
        cacheManager.setDefaultExpiration(10000); //设置key-value超时时间
        return cacheManager;
    }

    /**
     * redis模板，存储关键字是字符串，值是Jdk序列化
     *
     * @param factory
     * @return
     * @Description:
     */
    @Bean
    public RedisTemplate<?, ?> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<?, ?> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        //key序列化方式;但是如果方法上有Long等非String类型的话，会报类型转换错误；
        RedisSerializer<String> redisSerializer = new StringRedisSerializer();//Long类型不可以会出现异常信息;
        redisTemplate.setKeySerializer(redisSerializer);
        redisTemplate.setHashKeySerializer(redisSerializer);

        //JdkSerializationRedisSerializer序列化方式;
        JdkSerializationRedisSerializer jdkRedisSerializer = new JdkSerializationRedisSerializer();
        redisTemplate.setValueSerializer(jdkRedisSerializer);
        redisTemplate.setHashValueSerializer(jdkRedisSerializer);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate stringRedisTemplate = new StringRedisTemplate();
        stringRedisTemplate.setConnectionFactory(factory);
        RedisSerializer<String> stringSerializer = new StringRedisSerializer();
        stringRedisTemplate.setKeySerializer(stringSerializer);
        stringRedisTemplate.setValueSerializer(stringSerializer);
        stringRedisTemplate.setHashKeySerializer(stringSerializer);
        stringRedisTemplate.setHashValueSerializer(stringSerializer);
        stringRedisTemplate.afterPropertiesSet();
        return stringRedisTemplate;
    }


    /**
     * redis连接的基础设置
     *
     * @return
     * @Description:
     */
    @Bean
    public JedisConnectionFactory redisConnectionFactory() {
        JedisConnectionFactory factory = new JedisConnectionFactory();
        factory.setHostName(host);
        factory.setPort(port);
        factory.setPassword(password);
        //存储的库
        factory.setDatabase(database);
        //设置连接超时时间
        factory.setTimeout(timeout);
        factory.setUsePool(true);
        factory.setPoolConfig(jedisPoolConfig());
        return factory;
    }

    /**
     * 连接池配置
     *
     * @return
     * @Description:
     */
    @Bean
    public JedisPoolConfig jedisPoolConfig() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxIdle(maxIdle);
        jedisPoolConfig.setMinIdle(minIdle);
//    jedisPoolConfig.set ...
        return jedisPoolConfig;
    }

    /**
     * redis数据操作异常处理
     * 这里的处理：在日志中打印出错误信息，但是放行
     * 保证redis服务器出现连接等问题的时候不影响程序的正常运行，使得能够出问题时不用缓存
     *
     * @return
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        CacheErrorHandler cacheErrorHandler = new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                logger.error("redis异常：key=[{}]", key, e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                logger.error("redis异常：key=[{}]", key, e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                logger.error("redis异常：key=[{}]", key, e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                logger.error("redis异常：", e);
            }
        };
        return cacheErrorHandler;
    }

}

```

* 引入Locker或Limiter

```java
@Configuration
public class RedisLimiterConfig {

    @Autowired
    private JedisConnectionFactory jedisConnectionFactory;

    @Bean
    public RedisRateLimiter buildRefundLimiter() {
        RedisRateLimiter redisLimit = new RedisRateLimiter.Builder(jedisConnectionFactory, RedisToolsConstant.SINGLE)
                .limit(50)//这里是秒流量限制数
                .build();
        return redisLimit;
    }
    
    @Bean
    public RedisLocker build() {
        RedisLocker redisLock = new RedisLocker.Builder(jedisConnectionFactory, RedisToolsConstant.SINGLE)
                .lockPrefix("lock_")
                .sleepTime(100)
                .build();

        return redisLock;
    }
}
```

* 使用

```java
public class TestRedisRateLimiter {
    @Autowired
    private RedisRateLimiter redisRateLimiter;
    
    @Autowired
    private RedisLocker redisLocker;
    
    public Message business1() {
        Message m = new Message();
        if (!rateLimiter.tryAcquire()) {
            message.setMessage("目前下单人数过多，请稍后再试");
            message.setSuccess(false);
            return message;
        }
        ...
        return m;
    }
    
    public Message business2(String orderCode) {
        Message m = new Message();
        boolean lock = redisLocker.tryLock(orderCode, orderCode);
        if(!lock){
            m.setSuccess(false);
            m.setMessage("方法被锁");
            return m;
        }
    }
}
```

### 2在SpringController中使用

* 向Spring注册拦截器
```java
@Configuration
public class RestMvcConfigurerAdapter extends WebMvcConfigurerAdapter {
    
    @Bean
    public RateLimiterInterceptor rateLimiterInterceptor() {
        return new RateLimiterInterceptor();
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiterInterceptor()).addPathPatterns("/**");
    }
}

```

* 在Controller中使用
```java
@RestController
@RequestMapping('/xxx/yyy')
public class XXXController {
    
    @RequestMapping("aaa")
    @SpringControllerLimiter
    public Message test() {
        return null;
    }
}
```

### 3在普通类中使用（必须在Spring AOP容器内，否则需自己做AOP处理）
```java
@Service
public class XXXServiceImpl implements XXXService {
    
    @Override
    @CommonLimiter
    public Message vvv(){
        return  null;
    }
}


```