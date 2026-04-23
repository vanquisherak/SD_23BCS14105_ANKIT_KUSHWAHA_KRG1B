package com.flashsale.service;

import com.flashsale.config.FlashSaleProperties;
import com.flashsale.entity.SaleEvent;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FlashSaleRedisService {

    private final StringRedisTemplate redisTemplate;
    private final FlashSaleProperties properties;
    private final Map<Long, AtomicInteger> localInventory = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> localRateLimit = new ConcurrentHashMap<>();
    private final Map<String, String> localOrderState = new ConcurrentHashMap<>();
    private final Map<String, Long> localReservations = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> localQueue = new ConcurrentLinkedQueue<>();

    public FlashSaleRedisService(StringRedisTemplate redisTemplate, FlashSaleProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void preloadSale(SaleEvent saleEvent) {
        localInventory.put(saleEvent.getId(), new AtomicInteger(saleEvent.getSaleStock()));

        String keyPrefix = salePrefix(saleEvent.getId());
        try {
            redisTemplate.opsForValue().set(keyPrefix + ":inventory", String.valueOf(saleEvent.getSaleStock()));
            redisTemplate.opsForValue().set(keyPrefix + ":start_time", String.valueOf(toEpochSeconds(saleEvent.getStartTime())));
            redisTemplate.opsForValue().set(keyPrefix + ":end_time", String.valueOf(toEpochSeconds(saleEvent.getEndTime())));
            redisTemplate.opsForValue().set(keyPrefix + ":status", "PENDING");
        } catch (DataAccessException ignored) {
            // Fallback mode uses local in-memory structures when Redis is unavailable.
        }
    }

    public boolean allowRequest(Long saleId, Long userId) {
        String key = "FLASHSALE:RATELIMIT:sale:" + saleId + ":user:" + userId;
        long nowMs = System.currentTimeMillis();
        long windowMs = properties.getRateLimit().getWindowSeconds() * 1000L;
        try {
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, nowMs - windowMs);
            Long current = redisTemplate.opsForZSet().zCard(key);
            if (current != null && current >= properties.getRateLimit().getMaxRequests()) {
                return false;
            }

            redisTemplate.opsForZSet().add(key, String.valueOf(nowMs), nowMs);
            redisTemplate.expire(key, Duration.ofSeconds(properties.getRateLimit().getWindowSeconds()));
            return true;
        } catch (DataAccessException ignored) {
            Deque<Long> requests = localRateLimit.computeIfAbsent(key, unused -> new ArrayDeque<>());
            synchronized (requests) {
                while (!requests.isEmpty() && requests.peekFirst() < (nowMs - windowMs)) {
                    requests.pollFirst();
                }
                if (requests.size() >= properties.getRateLimit().getMaxRequests()) {
                    return false;
                }
                requests.addLast(nowMs);
                return true;
            }
        }
    }

    public int tryReserveInventory(Long saleId, int quantity) {
        String key = salePrefix(saleId) + ":inventory";
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key, quantity);
            if (remaining == null) {
                return -1;
            }

            if (remaining < 0) {
                redisTemplate.opsForValue().increment(key, quantity);
                return -1;
            }

            return remaining.intValue();
        } catch (DataAccessException ignored) {
            AtomicInteger local = localInventory.computeIfAbsent(saleId, unused -> new AtomicInteger(0));
            while (true) {
                int current = local.get();
                int next = current - quantity;
                if (next < 0) {
                    return -1;
                }
                if (local.compareAndSet(current, next)) {
                    return next;
                }
            }
        }
    }

    public void releaseInventory(Long saleId, int quantity) {
        try {
            redisTemplate.opsForValue().increment(salePrefix(saleId) + ":inventory", quantity);
        } catch (DataAccessException ignored) {
            localInventory.computeIfAbsent(saleId, unused -> new AtomicInteger(0)).addAndGet(quantity);
        }
    }

    public Integer getInventory(Long saleId) {
        try {
            String raw = redisTemplate.opsForValue().get(salePrefix(saleId) + ":inventory");
            if (raw == null) {
                return null;
            }
            return Integer.parseInt(raw);
        } catch (DataAccessException ignored) {
            AtomicInteger local = localInventory.get(saleId);
            return local == null ? null : local.get();
        }
    }

    public boolean markUserReservation(Long saleId, Long userId, LocalDateTime endTime) {
        String key = reservationKey(saleId, userId);
        Duration ttl = Duration.between(LocalDateTime.now(), endTime).plusMinutes(120);
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (DataAccessException ignored) {
            long expiresAt = System.currentTimeMillis() + ttl.toMillis();
            return localReservations.putIfAbsent(key, expiresAt) == null;
        }
    }

    public void clearUserReservation(Long saleId, Long userId) {
        String key = reservationKey(saleId, userId);
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException ignored) {
            localReservations.remove(key);
        }
    }

    public Long waitListPosition() {
        try {
            Long size = redisTemplate.opsForList().size(properties.getQueue().getKey());
            return size == null ? 1L : size + 1;
        } catch (DataAccessException ignored) {
            return (long) localQueue.size() + 1;
        }
    }

    public void enqueue(String payload) {
        try {
            redisTemplate.opsForList().leftPush(properties.getQueue().getKey(), payload);
        } catch (DataAccessException ignored) {
            localQueue.offer(payload);
        }
    }

    public String dequeue() {
        try {
            return redisTemplate.opsForList().rightPop(properties.getQueue().getKey());
        } catch (DataAccessException ignored) {
            return localQueue.poll();
        }
    }

    public void setOrderShadowState(String orderRef, String state, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(orderShadowKey(orderRef), state, ttl);
        } catch (DataAccessException ignored) {
            localOrderState.put(orderRef, state);
        }
    }

    public String getOrderShadowState(String orderRef) {
        try {
            return redisTemplate.opsForValue().get(orderShadowKey(orderRef));
        } catch (DataAccessException ignored) {
            return localOrderState.get(orderRef);
        }
    }

    public void setSaleStatus(Long saleId, String status) {
        try {
            redisTemplate.opsForValue().set(salePrefix(saleId) + ":status", status);
        } catch (DataAccessException ignored) {
            // No-op in fallback mode.
        }
    }

    public boolean isSaleLive(Long saleId) {
        try {
            String startRaw = redisTemplate.opsForValue().get(salePrefix(saleId) + ":start_time");
            String endRaw = redisTemplate.opsForValue().get(salePrefix(saleId) + ":end_time");

            if (startRaw == null || endRaw == null) {
                return false;
            }

            long now = System.currentTimeMillis() / 1000L;
            long start = Long.parseLong(startRaw);
            long end = Long.parseLong(endRaw);
            return now >= start && now <= end;
        } catch (DataAccessException ignored) {
            return true;
        }
    }

    private String salePrefix(Long saleId) {
        return "SALE:" + saleId;
    }

    private String reservationKey(Long saleId, Long userId) {
        return salePrefix(saleId) + ":user:" + userId + ":reserved";
    }

    private String orderShadowKey(String orderRef) {
        return "FLASHSALE:ORDER:STATE:" + orderRef;
    }

    private long toEpochSeconds(LocalDateTime time) {
        return time.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }
}
