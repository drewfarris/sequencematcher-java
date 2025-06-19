package drewfarris.util.difflib;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Performance tests for SequenceMatcher optimizations.
 */
public class SequenceMatcherPerformanceTest {

    @Test
    public void testPerformanceWithLargeStrings() {
        // Generate large test strings
        String a = generateRandomString(1000);
        String b = generateRandomString(1000);

        SequenceMatcher sm = new SequenceMatcher(null, a, b, true);

        // Warm up
        for (int i = 0; i < 10; i++) {
            sm.ratio();
            sm.quickRatio();
            sm.getMatchingBlocks();
            sm.getOpcodes();
        }

        // Time the operations
        long startTime = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            sm.ratio();
            sm.quickRatio();
            sm.getMatchingBlocks();
            sm.getOpcodes();
        }
        long endTime = System.nanoTime();

        System.out.println("Time for 100 operations on 1000-char strings: " + (endTime - startTime) / 1_000_000 + "ms");
    }

    @Test
    public void testPerformanceWithManySequenceChanges() {
        SequenceMatcher sm = new SequenceMatcher(null, "", "", true);

        // Test many sequence changes to verify object reuse
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            String a = generateRandomString(100);
            String b = generateRandomString(100);
            sm.setSequences(a, b);
            sm.ratio(); // Force computation
        }
        long endTime = System.nanoTime();

        System.out.println("Time for 1000 sequence changes with 100-char strings: " + (endTime - startTime) / 1_000_000 + "ms");
    }

    private String generateRandomString(int length) {
        Random random = new Random(42); // Fixed seed for reproducible tests
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            // Use a limited charset to increase character repetition
            sb.append((char) ('a' + random.nextInt(10)));
        }
        return sb.toString();
    }
}
