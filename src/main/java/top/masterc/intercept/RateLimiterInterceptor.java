package top.masterc.intercept;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;
import top.masterc.annotation.SpringControllerLimiter;
import top.masterc.limit.RedisRateLimiter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 拦截器锁， controller方法需使用SpringControllerLimiter注解
 */

public class RateLimiterInterceptor extends HandlerInterceptorAdapter {

    private static Logger logger = LoggerFactory.getLogger(RateLimiterInterceptor.class);

    @Autowired
    private RedisRateLimiter redisRateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (redisRateLimiter == null) {
            throw new NullPointerException("redisRateLimiter is null");
        }

        if (handler instanceof HandlerMethod) {
            HandlerMethod method = (HandlerMethod) handler;

            SpringControllerLimiter annotation = method.getMethodAnnotation(SpringControllerLimiter.class);
            if (annotation == null) {
                //skip
                return true;
            }

            boolean limit = redisRateLimiter.acquire();
            if (!limit) {
                logger.warn(annotation.errorMsg());
                response.sendError(annotation.errorCode(), annotation.errorMsg());
                return false;
            }

        }

        return true;

    }


}
