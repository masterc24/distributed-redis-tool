package top.masterc.limit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import top.masterc.constant.RedisToolsConstant;
import top.masterc.util.ScriptUtil;

import java.io.IOException;
import java.util.Collections;

/**
 * 基于Redis的分布式限流
 *
 * @author Master.C
 */
public class RedisRateLimiter {

    private static Logger logger = LoggerFactory.getLogger(RedisRateLimiter.class);


    private JedisConnectionFactory jedisConnectionFactory;
    private int type;
    private int limit = 200;

    private static final int FAIL_CODE = 0;

    /**
     * lua script
     */
    private String script;

    private RedisRateLimiter(Builder builder) {
        this.limit = builder.limit;
        this.jedisConnectionFactory = builder.jedisConnectionFactory;
        this.type = builder.type;
        buildScript();
    }


    /**
     * limit traffic
     *
     * @return if true no limit, false limit
     */
    public boolean acquire() {

        //get connection
        Object connection = getConnection();

        String key = String.valueOf(System.currentTimeMillis() / 1000);

        Object result = limitRequest(connection, key);

        if (FAIL_CODE != (Long) result) {
            return true;
        } else {
            return false;
        }
    }

    public boolean acquire(String key) {
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException("key cannot be null");
        }
        Object connection = getConnection();
        key = key + System.currentTimeMillis() / 1000;
        Object result = limitRequest(connection, key);
        if (FAIL_CODE != (Long) result) {
            return true;
        } else {
            return false;
        }
    }

    private Object limitRequest(Object connection, String key) {
        Object result = null;
        if (connection instanceof Jedis) {
            result = ((Jedis) connection).eval(script, Collections.singletonList(key), Collections.singletonList(String.valueOf(limit)));
            ((Jedis) connection).close();
        } else {
            result = ((JedisCluster) connection).eval(script, Collections.singletonList(key), Collections.singletonList(String.valueOf(limit)));
            try {
                ((JedisCluster) connection).close();
            } catch (IOException e) {
                logger.error("IOException", e);
            }
        }
        return result;
    }

    /**
     * get Redis connection
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
     * read lua script
     */
    private void buildScript() {
        script = ScriptUtil.getScript("limit.lua");
    }


    /**
     * the builder
     */
    public static class Builder {
        private JedisConnectionFactory jedisConnectionFactory = null;

        private int limit = 200;
        private int type;


        public Builder(JedisConnectionFactory jedisConnectionFactory, int type) {
            this.jedisConnectionFactory = jedisConnectionFactory;
            this.type = type;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public RedisRateLimiter build() {
            return new RedisRateLimiter(this);
        }

    }
}
