package top.masterc.intercept;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.stereotype.Component;
import top.masterc.limit.RedisRateLimiter;

/**
 * aop 切面锁，使用CommonLimiter注解
 *
 * @author Master.C
 */
@Aspect
@Component
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class RateLimiterAspect {

    private static Logger logger = LoggerFactory.getLogger(RateLimiterAspect.class);

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Pointcut("@annotation(top.masterc.annotation.CommonLimiter)")
    private void check() {
    }

    @Before("check()")
    public void before(JoinPoint joinPoint) throws Exception {

        if (redisRateLimiter == null) {
            throw new NullPointerException("redisRateLimiter is null");
        }

        boolean limit = redisRateLimiter.acquire();
        if (!limit) {
            logger.warn("request has bean limited");
            throw new RuntimeException("request has bean limited");
        }

    }
}
