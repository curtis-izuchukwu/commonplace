package com.pararepilot.util;

import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;

public class WeightedRandomPicker<T> {

    private final Random random;

    public WeightedRandomPicker() {
        this(new Random());
    }

    public WeightedRandomPicker(Random random) {
        this.random = random;
    }

    public T pick(List<T> items, ToIntFunction<T> weightFunction) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cannot pick from an empty list.");
        }

        int totalWeight = items.stream()
                .mapToInt(item -> Math.max(0, weightFunction.applyAsInt(item)))
                .sum();

        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be greater than zero.");
        }

        int roll = random.nextInt(totalWeight);

        for (T item : items) {
            roll -= Math.max(0, weightFunction.applyAsInt(item));

            if (roll < 0) {
                return item;
            }
        }

        return items.get(items.size() - 1);
    }
}