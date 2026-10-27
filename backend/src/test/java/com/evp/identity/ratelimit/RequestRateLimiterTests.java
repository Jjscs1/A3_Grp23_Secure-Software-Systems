package com.evp.identity.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RequestRateLimiterTests {
    private final AtomicLong now = new AtomicLong(100000);
    private final RequestRateLimiter limiter = new RequestRateLimiter(now::get, Duration.ofMinutes(1), 100);
    private List<RequestRateLimiter.Budget> budget(String key, int limit) {
        return List.of(new RequestRateLimiter.Budget(key, limit));
    }
    @Test void twentyFirstRequestIsRejectedWithRetryAfter() {
        for (int i=0;i<20;i++) assertThat(limiter.acquire(budget("ip",20))).isZero();
        assertThat(limiter.acquire(budget("ip",20))).isEqualTo(60);
    }
    @Test void rejectionDoesNotExtendWindowAndRecoveryIsAutomatic() {
        limiter.acquire(budget("ip",1));
        now.addAndGet(59001);
        assertThat(limiter.acquire(budget("ip",1))).isEqualTo(1);
        now.addAndGet(999);
        assertThat(limiter.acquire(budget("ip",1))).isZero();
    }
    @Test void slidingWindowDoesNotResetAtWallClockMinuteBoundary() {
        now.set(59999);
        limiter.acquire(budget("ip",1));
        now.set(60000);
        assertThat(limiter.acquire(budget("ip",1))).isEqualTo(60);
    }
    @Test void differentIpsAndEndpointsHaveIndependentBudgets() {
        limiter.acquire(budget("ip:a:enrol",1));
        assertThat(limiter.acquire(budget("ip:b:enrol",1))).isZero();
        assertThat(limiter.acquire(budget("ip:a:ballot",1))).isZero();
    }
    @Test void aggregateEndpointBudgetLimitsDifferentIps() {
        for(int i=0;i<3;i++) assertThat(limiter.acquire(List.of(
            new RequestRateLimiter.Budget("ip:"+i,20),new RequestRateLimiter.Budget("endpoint",3)))).isZero();
        assertThat(limiter.acquire(List.of(new RequestRateLimiter.Budget("ip:new",20),
            new RequestRateLimiter.Budget("endpoint",3)))).isEqualTo(60);
    }
    @Test void rejectedMultiBudgetRequestDoesNotConsumeOtherBudget() {
        limiter.acquire(budget("full",1));
        assertThat(limiter.acquire(List.of(new RequestRateLimiter.Budget("full",1),
            new RequestRateLimiter.Budget("other",1)))).isEqualTo(60);
        assertThat(limiter.acquire(budget("other",1))).isZero();
    }
    @Test void concurrentRequestsCannotExceedLimit() throws Exception {
        try(var executor=Executors.newFixedThreadPool(16)) {
            List<Callable<Long>> calls=IntStream.range(0,100)
                .mapToObj(i -> (Callable<Long>) () -> limiter.acquire(budget("same",20))).toList();
            long accepted=0;
            for(var result:executor.invokeAll(calls)) if(result.get()==0) accepted++;
            assertThat(accepted).isEqualTo(20);
        }
    }
    @Test void boundedMemoryDoesNotEvictLiveBudgetsAndRecovers() {
        var small=new RequestRateLimiter(now::get,Duration.ofMinutes(1),2);
        small.acquire(budget("a",1)); small.acquire(budget("b",1));
        assertThat(small.acquire(budget("c",1))).isEqualTo(60);
        assertThat(small.acquire(budget("a",1))).isEqualTo(60);
        now.addAndGet(60000);
        assertThat(small.acquire(budget("c",1))).isZero();
    }
}
