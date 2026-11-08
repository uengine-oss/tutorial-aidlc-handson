package kr.voiceparking.payment.support;

import java.util.function.BooleanSupplier;

/** 조건이 참이 될 때까지 짧게 기다리는 시험 도우미. */
public final class Await {

    private Await() {
    }

    public static boolean until(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return condition.getAsBoolean();
    }
}
