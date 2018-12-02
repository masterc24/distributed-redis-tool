package top.masterc.lock;

import top.masterc.constant.RedisToolsConstant;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisNode;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.*;

import java.util.concurrent.*;

public class RedisLockerTest {

    private static Logger logger = LoggerFactory.getLogger(RedisLockerTest.class);
    private static ExecutorService executorServicePool;


    private static RedisLocker redisLocker;

    private static JedisPool jedisPool;


    public static void main(String[] args) throws InterruptedException {
        RedisLockerTest redisLockTest = new RedisLockerTest();
        redisLockTest.init();
        initThread();

        for (int i = 0; i < 1; i++) {
            executorServicePool.execute(new Worker(i));
        }

        executorServicePool.shutdown();
        while (!executorServicePool.awaitTermination(1, TimeUnit.SECONDS)) {
            logger.info("worker running");
        }
        logger.info("worker over");

    }

    @Before
    public void setBefore() {
        init();

    }

    private void init() {

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(10);
        config.setMaxTotal(300);
        config.setMaxWaitMillis(10000);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);

        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration();
        redisClusterConfiguration.addClusterNode(new RedisNode("10.19.13.51", 7000));

        //单机
        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(config);

        //集群
        //JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(redisClusterConfiguration) ;
        jedisConnectionFactory.setHostName("47.98.194.60");
        jedisConnectionFactory.setPort(6379);
        jedisConnectionFactory.setPassword("");
        jedisConnectionFactory.setTimeout(100000);
        jedisConnectionFactory.afterPropertiesSet();
        //jedisConnectionFactory.setShardInfo(new JedisShardInfo("47.98.194.60", 6379));
        //JedisCluster jedisCluster = new JedisCluster(hostAndPort);

        HostAndPort hostAndPort = new HostAndPort("10.19.13.51", 7000);
        JedisCluster jedisCluster = new JedisCluster(hostAndPort);
        redisLocker = new RedisLocker.Builder(jedisConnectionFactory, RedisToolsConstant.SINGLE)
                .lockPrefix("lock_")
                .sleepTime(100)
                .build();

    }

    public static void initThread() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
                .setNameFormat("current-thread-%d").build();
        executorServicePool = new ThreadPoolExecutor(350, 350, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(200), namedThreadFactory, new ThreadPoolExecutor.AbortPolicy());

    }


    private static class Worker implements Runnable {

        private int index;

        public Worker(int index) {
            this.index = index;
        }

        @Override
        public void run() {
            boolean limit = redisLocker.tryLock("abc", "12345");
            if (limit) {
                logger.info("加锁成功");
            } else {
                logger.info("加锁失败");

            }
        }
    }


}