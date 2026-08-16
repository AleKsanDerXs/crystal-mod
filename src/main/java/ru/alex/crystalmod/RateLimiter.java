package ru.alex.crystalmod;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Скользящее окно попыток на игрока — та же схема, что {@code AuthAPI.isRateLimited}
 * (auth-mod), только по UUID игрока вместо IP. Один общий экземпляр на все
 * команды мода, а не по одному на каждую — не плодим лишние карты и потоки.
 *
 * <p>Периодическая чистка обязательна: без нее карта только растёт (заходил
 * игрок раз — запись осталась навсегда) — именно та "утечка памяти", которой
 * просили избежать.
 */
public final class RateLimiter {

    private final int maxAttempts;
    private final long windowMs;
    private final Map<UUID, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService CLEANUP = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CrystalMod-RateLimitCleanup");
        t.setDaemon(true);
        return t;
    });

    public RateLimiter(int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
        CLEANUP.scheduleAtFixedRate(this::cleanup, 10, 10, TimeUnit.MINUTES);
    }

    /** true — лимит уже исчерпан, вызывающая команда не должна ничего слать на сайт. Иначе считает попытку сразу. */
    public boolean isLimited(UUID player) {
        Deque<Long> deque = attempts.computeIfAbsent(player, k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            if (deque.size() >= maxAttempts) {
                return true;
            }
            deque.addLast(now);
            return false;
        }
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> {
            Deque<Long> deque = e.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }
}
