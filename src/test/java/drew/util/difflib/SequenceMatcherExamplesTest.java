package drew.util.difflib;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

public class SequenceMatcherExamplesTest {
    @Test
    public void testBasicCompareTwoStrings() {
        // Compare two strings
        SequenceMatcher sm = new SequenceMatcher("hello world", "hello java");

        // Get similarity ratio (0.0 to 1.0)
        double similarity = sm.ratio(); // 0.5714..
        assertEquals(0.5714, similarity, 0.0001);

        // Get matching blocks
        List<SequenceMatcher.Match> blocks = sm.getMatchingBlocks();
        // Result: [Match{a=0, b=0, size=6}, Match{a=11, b=10, size=0}]
        assertEquals(2, blocks.size());
        SequenceMatcher.Match matchOne = new SequenceMatcher.Match(0, 0, 6);
        SequenceMatcher.Match matchTwo = new SequenceMatcher.Match(11, 10, 0);
        assertEquals(matchOne, blocks.get(0));
        assertEquals(matchTwo, blocks.get(1));

        // Get edit operations
        List<SequenceMatcher.Opcode> opcodes = sm.getOpcodes();
        // Result: [EQUAL(0,6,0,6), REPLACE(6,11,6,10)
        assertEquals(2, opcodes.size());

        SequenceMatcher.Opcode opcodeOne = new SequenceMatcher.Opcode(SequenceMatcher.OpcodeTag.EQUAL, 0, 6, 0, 6);
        SequenceMatcher.Opcode opcodeTwo = new SequenceMatcher.Opcode(SequenceMatcher.OpcodeTag.REPLACE, 6, 11, 6, 10);
        assertEquals(opcodeOne, opcodes.get(0));
        assertEquals(opcodeTwo, opcodes.get(1));
    }

    @Test
    public void testCustomJunkFilter() {
        // Ignore whitespace and punctuation when comparing
        SequenceMatcher sm = new SequenceMatcher(ch -> ch == ' ' || ch == '\t' || ch == '.', "hello, world!", "hello world", true);

        double ratio = sm.ratio(); // Higher similarity due to ignored punctuation
        assertEquals(0.916, ratio, 0.01);
    }

    @Test
    public void testCloseMatches() {
        List<String> possibilities = Arrays.asList("apple", "peach", "orange", "grape", "apricot", "pineapple");

        List<String> matches = SequenceMatcher.getCloseMatches("apple", possibilities, 3, 0.6);
        assertEquals(3, matches.size());
    }

    @Test
    public void testReuse() {
        String[][] stringPairs = {{"apple", "pineapple"}, {"apple", "grape"}, {"peach", "apricot"}};

        SequenceMatcher sm = new SequenceMatcher();

        // Efficiently compare many pairs
        double lastSimilarity = 0.0;
        for (String[] pair : stringPairs) {
            sm.setSequences(pair[0], pair[1]);
            double similarity = sm.ratio();
            assertNotEquals(lastSimilarity, similarity, 0.0);
            lastSimilarity = similarity;
        }
    }
}
