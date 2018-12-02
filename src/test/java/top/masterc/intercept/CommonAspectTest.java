package top.masterc.intercept;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import top.masterc.limit.RedisRateLimiter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommonAspectTest {

    @InjectMocks
    private RateLimiterAspect rateLimiterAspect;

    @Mock
    private RedisRateLimiter redisRateLimiter;

    @Before
    public void setBefore() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test1() {
        try {
            Mockito.when(redisRateLimiter.acquire()).thenReturn(true);
            rateLimiterAspect.before(null);
            boolean limit = redisRateLimiter.acquire();
            System.out.println(limit);
            assertTrue(limit);
            Mockito.verify(redisRateLimiter, Mockito.times(2)).acquire();

        } catch (Exception e) {
        }
    }

    @Test
    public void test2() {
        try {
            Mockito.when(redisRateLimiter.acquire()).thenReturn(false);
            rateLimiterAspect.before(null);


        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        boolean limit = redisRateLimiter.acquire();
        System.out.println(limit);
        assertFalse(limit);
        Mockito.verify(redisRateLimiter, Mockito.times(2)).acquire();
    }

}