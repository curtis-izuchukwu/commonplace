package com.pararepilot.util;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class WeightedRandomPickerTest {

    @Test
    void onlyPositiveWeightItemCanBePicked() {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(new Random(1));

        List<String> items = List.of("A", "B", "C");

        Map<String, Integer> weights = Map.of(
                "A", 0,
                "B", 10,
                "C", 0
        );

        for (int i = 0; i < 50; i++) {
            assertEquals("B", picker.pick(items, weights::get));
        }
    }

    @Test
    void emptyListThrowsException() {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> picker.pick(List.of(), item -> 1)
        );
    }

    @Test
    void zeroTotalWeightThrowsException() {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();

        assertThrows(
                IllegalArgumentException.class,
                () -> picker.pick(List.of("A", "B"), item -> 0)
        );
    }
}