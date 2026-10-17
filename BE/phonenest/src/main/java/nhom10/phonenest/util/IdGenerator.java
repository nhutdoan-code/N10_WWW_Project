package nhom11.phonenest.util;

import java.util.concurrent.atomic.AtomicInteger;

public final class IdGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private IdGenerator() {
    }

    public static String next(String prefix) {
        return prefix + System.currentTimeMillis() + String.format("%03d", SEQ.incrementAndGet() % 1000);
    }
}
