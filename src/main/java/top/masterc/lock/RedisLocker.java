package top.masterc.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import top.masterc.constant.RedisToolsConstant;
import top.masterc.util.ScriptUtil;

import java.io.IOException;
import java.util.Collections;

/**
 * 基于Redis的分布式锁
 *
 * @author Master.C
 */
public class RedisLocker {
    private static Logger logger = LoggerFactory.getLogger(RedisLocker.class);

    private static final String LOCK_MSG = "OK";

    private static final Long UNLOCK_MSG = 1L;

    private static final String SET_IF_NOT_EXIST = "NX";
    private static final String SET_WITH_EXPIRE_TIME = "PX";


    private String lockPrefix;

    private int sleepTime;

    private JedisConnectionFactory jedisConnectionFactory;
    private int type;

    private static final int TIME = 1000;

    private String script;

    private RedisLocker(Builder builder) {
        this.jedisConnectionFactory = builder.jedisConnectionFactory;
        this.type = builder.type;
        this.lockPrefix = builder.lockPrefix;
        this.sleepTime = builder.sleepTime;

        buildScript();
    }


    /**
     * 获取redis链接
     *
     * @return
     */
    private Object getConnection() {
        Object connection;
        if (type == RedisToolsConstant.SINGLE) {
            RedisConnection redisConnection = jedisConnectionFactory.getConnection();
            connection = redisConnection.getNativeConnection();
        } else {
            RedisClusterConnection clusterConnection = jedisConnectionFactory.getClusterConnection();
            connection = clusterConnection.getNativeConnection();
        }
        return connection;
    }

    /**
     * Non-blocking lock
     *
     * @param key     lock business type
     * @param request value
     * @return true lock success,false lock fail
     */
    public boolean tryLock(String key, String request) {
        //get connection
        Object connection = getConnection();
        String result;
        if (connection instanceof Jedis) {
            result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
            ((Jedis) connection).close();
        } else {
            result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
            try {
                ((JedisCluster) connection).close();
            } catch (IOException e) {
                logger.error("IOException", e);
            }
        }

        if (LOCK_MSG.equals(result)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * blocking lock
     *
     * @param key
     * @param request
     */
    public void lock(String key, String request) throws InterruptedException {
        //get connection
        Object connection = getConnection();
        String result;
        for (; ; ) {
            if (connection instanceof Jedis) {
                result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
                ((Jedis) connection).close();
            } else {
                result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
                try {
                    ((JedisCluster) connection).close();
                } catch (IOException e) {
                    logger.error("IOException", e);
                }
            }

            if (LOCK_MSG.equals(result)) {
                break;
            }

            Thread.sleep(sleepTime);
        }

    }

    /**
     * blocking lock,custom time
     *
     * @param key
     * @param request
     * @param blockTime custom time
     * @return
     * @throws InterruptedException
     */
    public boolean lock(String key, String request, int blockTime) throws InterruptedException {

        //get connection
        Object connection = getConnection();
        String result;
        while (blockTime >= 0) {
            if (connection instanceof Jedis) {
                result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
                ((Jedis) connection).close();
            } else {
                result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, 10 * TIME);
                try {
                    ((JedisCluster) connection).close();
                } catch (IOException e) {
                    logger.error("IOException", e);
                }
            }
            if (LOCK_MSG.equals(result)) {
                return true;
            }
            blockTime -= sleepTime;

            Thread.sleep(sleepTime);
        }
        return false;
    }


    /**
     * Non-blocking lock
     *
     * @param key        lock business type
     * @param request    value
     * @param expireTime custom expireTime
     * @return true lock success
     * false lock fail
     */
    public boolean tryLock(String key, String request, int expireTime) {
        //get connection
        Object connection = getConnection();
        String result;

        if (connection instanceof Jedis) {
            result = ((Jedis) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
            ((Jedis) connection).close();
        } else {
            result = ((JedisCluster) connection).set(lockPrefix + key, request, SET_IF_NOT_EXIST, SET_WITH_EXPIRE_TIME, expireTime);
            try {
                ((JedisCluster) connection).close();
            } catch (IOException e) {
                logger.error("IOException", e);
            }
        }

        if (LOCK_MSG.equals(result)) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * unlock
     *
     * @param key
     * @param request request must be the same as lock request
     * @return
     */
    public boolean unlock(String key, String request) {

        //get connection
        Object connection = getConnection();
        //lua script

        Object result = null;
        if (connection instanceof Jedis) {
            result = ((Jedis) connection).eval(script, Collections.singletonList(lockPrefix + key), Collections.singletonList(request));
            ((Jedis) connection).close();
        } else if (connection instanceof JedisCluster) {
            result = ((JedisCluster) connection).eval(script, Collections.singletonList(lockPrefix + key), Collections.singletonList(request));
            try {
                ((JedisCluster) connection).close();
            } catch (IOException e) {
                logger.error("IOException", e);
            }
        } else {
            //throw new RuntimeException("instance is error") ;
            return false;
        }

        if (UNLOCK_MSG.equals(result)) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * read lua script
     */
    private void buildScript() {
        script = ScriptUtil.getScript("lock.lua");
    }


    public static class Builder {
        private static final String DEFAULT_LOCK_PREFIX = "lock_";
        /**
         * default sleep time
         */
        private static final int DEFAULT_SLEEP_TIME = 100;

        private JedisConnectionFactory jedisConnectionFactory = null;

        private int type;

        private String lockPrefix = DEFAULT_LOCK_PREFIX;
        private int sleepTime = DEFAULT_SLEEP_TIME;

        public Builder(JedisConnectionFactory jedisConnectionFactory, int type) {
            this.jedisConnectionFactory = jedisConnectionFactory;
            this.type = type;
        }

        public Builder lockPrefix(String lockPrefix) {
            this.lockPrefix = lockPrefix;
            return this;
        }

        public Builder sleepTime(int sleepTime) {
            this.sleepTime = sleepTime;
            return this;
        }

        public RedisLocker build() {
            return new RedisLocker(this);
        }

    }
}
