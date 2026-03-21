package com.cliente.cliente_back.retention;

import java.util.Arrays;
import java.util.Optional;


public enum RetentionTierSpec {
    LEVEL_1(1, 25, 3),
    LEVEL_2(2, 50, 3),
    LEVEL_3(3, 65, 3),
    LEVEL_4(4, 80, 6);

    private final int level;
    private final int discountPercent;
    private final int durationMonths;

    RetentionTierSpec(int level, int discountPercent, int durationMonths) {
        this.level = level;
        this.discountPercent = discountPercent;
        this.durationMonths = durationMonths;
    }

    public int level() {
        return level;
    }

    public int discountPercent() {
        return discountPercent;
    }

    public int durationMonths() {
        return durationMonths;
    }

    public static Optional<RetentionTierSpec> fromLevel(int level) {
        return Arrays.stream(values()).filter(t -> t.level == level).findFirst();
    }
}
