package drew.util.difflib;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Experimental port of Python difflib's
 * <a href="https://github.com/python/cpython/blob/e6c3039cb39e68ae9af9ddcaca341c5af8f9cf23/Lib/difflib.py#L44">SequenceMatcher</a> to Java. Where appropriate,
 * documentation and comments are copied from SequenceMatcher.
 * <p/>
 * Documentation from SequenceMatcher:
 * <p/>
 * SequenceMatcher is a flexible class for comparing pairs of sequences of any type, so long as the sequence elements are hashable. The basic algorithm
 * predates, and is a little fancier than, an algorithm published in the late 1980's by Ratcliff and Obershelp under the hyperbolic name "gestalt pattern
 * matching". The basic idea is to find the longest contiguous matching subsequence that contains no "junk" elements (R-O doesn't address junk). The same idea
 * is then applied recursively to the pieces of the sequences to the left and to the right of the matching subsequence. This does not yield minimal edit
 * sequences, but does tend to yield matches that "look right" to people.
 * <p/>
 * SequenceMatcher tries to compute a "human-friendly diff" between two sequences. Unlike e.g. UNIX(tm) diff, the fundamental notion is the longest *contiguous*
 * & junk-free matching subsequence. That's what catches peoples' eyes. The Windows(tm) windiff has another interesting notion, pairing up elements that appear
 * uniquely in each sequence. That, and the method here, appear to yield more intuitive difference reports than does diff. This method appears to be the least
 * vulnerable to syncing up on blocks of "junk lines", though (like blank lines in ordinary text files, or maybe "&lt;P&gt;lines in HTML files). That may be because this is the only method of the 3 that has a *concept* of "junk" &lt;wink&gt;.
 * <p/>
 * {@link #ratio()} returns a float in <code>[0, 1]</code>, measuring the "similarity" of the sequences. As a rule of thumb, a {@link #ratio()} value over 0.6 means the sequences are close
 * matches.
 * <p/>
 * If you're only interested in where the sequences match, {@link #getMatchingBlocks()} is handy:
 * <p/>
 * Note that the last tuple returned by {@link #getMatchingBlocks()} is always a dummy, <code>(len(a), len(b), 0)</code>, and this is the only case in which the last tuple element
 * (number of elements matched) is 0.
 * <p/>
 * If you want to know how to change the first sequence into the second, use {@link #getOpcodes()}.
 * <p/>
 * Timing: Basic R-O is cubic time worst case and quadratic time expected case. SequenceMatcher is quadratic time for the worst case and has expected-case
 * behavior dependent in a complicated way on how many elements the sequences have in common; best case time is linear.
 */
public class SequenceMatcher {

    /** first sequence */
    private String a;

    /** second sequence; differences are computed as "what do we need to do to 'a' to change it into 'b'" */
    private String b;

    /** <code>for x in b, b2j[x]</code> is a list of the indices (into b) at which x appears; junk and popular elements do not appear */
    private Map<Character,List<Integer>> b2j;

    /** the items in b for which {@link #junkFilter} is True. */
    private Set<Character> bJunk;

    /** autojunk should be set to <code>false</code> to disable the "automatic junk heuristic" that treats popular elements as junk. */
    private final boolean autoJunk;

    /** a list of <code>(i, j, k)</code> triples, where <code>a[i:i+k] == b[j:j+k]</code>; ascending & non-overlapping in i and in j; terminated by a dummy <code>(len(a), len(b), 0)</code> sentinel */
    private List<Match> matchingBlocks;

    /**
     * a list of (tag, i1, i2, j1, j2) tuples, where tag is: one of
     * <dl>
     * <dt>replace</dt>
     * <dd>a[i1:i2] should be replaced by b[j1:j2]</dd>
     * <dt>delete</dt>
     * <dd>a[i1:i2] should be deleted</dd>
     * <dt>insert</dt>
     * <dd>b[j1:j2] should be inserted</dd>
     * <dt>equal</dt>
     * <dd>a[i1:i2] == b[j1:j2]</dd>
     * </dl>
     */
    private List<Opcode> opcodes;

    /**
     * <code>for x in b</code>, <code>fullbcount[x]</code> equals the number of times x appears in b; only materialized if really needed (used only for computing {@link #quickRatio()})
     */
    private Map<Character,Integer> fullBCount;

    /**
     * a user-supplied function taking a sequence element and returning true iff the element is "junk" -- this has subtle but helpful effects on the algorithm,
     * which I'll get around to writing up someday <0.9 wink>. DON'T USE! Only {@link #chainB()} uses this. Use <code>bjunk.contains(...)</code>
     */
    private final JunkFilter junkFilter;

    /**
     * Construct a SequenceMatcher.
     *
     * @param junkFilter
     *            a one-argument function that takes a sequence element and returns true iff the element is junk. None is equivalent to passing "lambda x: 0",
     *            i.e. no elements are considered to be junk. For example, pass lambda x: x in " \\t" if you're comparing lines as sequences of characters, and
     *            don't want to sync up on blanks or hard tabs.
     *
     * @param a
     *            the first of two sequences to be compared. By default, an empty string. The elements of a must be hashable. See also .set_seqs() and
     *            .set_seq1().
     *
     * @param b
     *            the second of two sequences to be compared. By default, an empty string. The elements of b must be hashable. See also .set_seqs() and
     *            .set_seq2().
     *
     * @param autoJunk
     *            should be set to false to disable the "automatic junk heuristic" that treats popular elements as junk
     *
     */
    public SequenceMatcher(JunkFilter junkFilter, String a, String b, boolean autoJunk) {
        this.junkFilter = junkFilter;
        this.autoJunk = autoJunk;
        setSeqs(a, b);
    }

    /** Set the two sequences to be compared */
    public void setSeqs(String a, String b) {
        setSeqA(a);
        setSeqB(b);
    }

    /**
     * Set the first sequence to be compared.
     * <p/>
     * The second sequence to be compared is not changed.
     */

    public void setSeqA(String a) {
        if (a.equals(this.a)) {
            return;
        }
        this.a = a;
        this.matchingBlocks = null;
        this.opcodes = null;
    }

    /**
     * Set the second sequence to be compared.
     * <p/>
     * The first sequence to be compared is not changed.
     */
    public void setSeqB(String b) {
        if (b.equals(this.b)) {
            return;
        }
        this.b = b;
        this.matchingBlocks = null;
        this.opcodes = null;
        this.fullBCount = null;
        chainB();
    }

    /**
     * For each element <code>x in b</code>, set <code>b2j[x]</code> to a list of the indices in b where x appears; the indices are in increasing order;
     * <p/>
     * note that the number of times x appears in b is <code>b2j.get(x).size()</code> ... when isJunk is defined, junk elements don't show up in this map at all, which stops
     * the central {@link #findLongestMatch(int, int, int, int)} method from starting any matching block at a junk element.
     * <p/>
     * <code>b2j</code> also does not contain entries for "popular" elements, meaning elements that account for more than 1 + 1% of the total elements, and when the
     * sequence is reasonably large (>= 200 elements); this can be viewed as an adaptive notion of semi-junk, and yields an enormous speedup when, e.g.,
     * comparing program files with hundreds of instances of "return null;" ... note that this is only called when b changes; so for cross-product kinds of
     * matches, it's best to call {@link #setSeqB(String)} once, then {@link #setSeqA(String)} repeatedly.
     * <p/>
     * Because junkFilter is a user-defined function, and we test for junk a LOT, it's important to minimize the number of calls. The first trick is to
     * build b2j ignoring the possibility of junk. I.e., we don't call isJunk at all yet. Throwing out the junk later is much cheaper than building b2j
     * "right" from the start.
     */

    private void chainB() {

        this.b2j = new HashMap<>();
        for (int i = 0; i < b.length(); i++) {
            char elt = b.charAt(i);
            b2j.computeIfAbsent(elt, k -> new ArrayList<>()).add(i);
        }

        this.bJunk = new HashSet<>();
        if (junkFilter != null) {
            for (char elt : b2j.keySet()) {
                if (junkFilter.isJunk(elt)) {
                    bJunk.add(elt);
                }
            }
            for (char elt : bJunk) {
                b2j.remove(elt);
            }
        }

        /* nonjunk items in b treated as junk by the heuristic (if used). */
        Set<Character> bPopular = new HashSet<>();
        int n = b.length();
        if (autoJunk && n >= 200) {
            int nTest = n / 100 + 1;
            for (Map.Entry<Character,List<Integer>> entry : b2j.entrySet()) {
                if (entry.getValue().size() > nTest) {
                    bPopular.add(entry.getKey());
                }
            }
            for (char elt : bPopular) {
                b2j.remove(elt);
            }
        }
    }

    /**
     * Find the longest matching block in <code>a[alo:ahi]</code> and <code>b[blo:bhi]</code>.
     * <p/>
     * By default, it will find the longest match in the entirety of a and b.
     * <p/>
     * If <code>junkFilter</code> is not defined:
     * <p/>
     * Return <code>(i,j,k)</code> such that <code>a[i:i+k]</code> is equal to <code>b[j:j+k]</code>, where <code>alo <= i <= i+k <= ahi blo <= j <= j+k <= bhi</code> and for all <code>(i',j',k')</code> meeting those
     * conditions, <code>k >= k' i <= i'</code> and if <code>i == i', j <= j'</code>.
     * <p/>
     * In other words, of all maximal matching blocks, return one that starts earliest in <code>a</code>, and of all those maximal matching blocks that start earliest in <code>a</code>,
     * return the one that starts earliest in <code>b</code>.
     * <p/>
     * If <code>junkFilter</code> is defined, first the longest matching block is determined as above, but with the additional restriction that no junk element appears in the
     * block. Then that block is extended as far as possible by matching (only) junk elements on both sides. So the resulting block never matches on junk except
     * as identical junk happens to be adjacent to an "interesting" match.
     * <p/>
     * Here's the same example as before, but considering blanks to be junk. That prevents <code>" abcd"</code> from matching the <code>" abcd"</code> at the tail end of the second
     * sequence directly. Instead, only the <code>"abcd"</code> can match, and matches the leftmost <code>"abcd"</code> in the second sequence.
     * <p/>
     * CAUTION: stripping common prefix or suffix would be incorrect. E.g., ab acab Longest matching block is "ab", but if common prefix is stripped, it's
     * "a" (tied with "b"). UNIX(tm) diff does so strip, so ends up claiming that ab is changed to acab by inserting "ca" in the middle. That's minimal but
     * unintuitive: "it's obvious" that someone inserted "ac" at the front. Windiff ends up at the same place as diff, but by pairing up the unique 'b's and
     * then matching the first two 'a's.
     */
    public Match findLongestMatch(int alo, int ahi, int blo, int bhi) {

        int besti = alo;
        int bestj = blo;
        int bestsize = 0;

        // find the longest junk-free match
        // during an iteration of the loop, j2len[j] = length of longest
        // junk-free match ending with a[i-1] and b[j]

        // TODO: what to do about j2len?
        Map<Integer,Integer> j2len = new HashMap<>();
        List<Integer> nothing = new ArrayList<>();

        for (int i = alo; i < ahi; i++) {
            Map<Integer,Integer> newj2len = new HashMap<>();
            for (int j : b2j.getOrDefault(a.charAt(i), nothing)) {
                if (j < blo)
                    continue;
                if (j >= bhi)
                    break;
                int k = newj2len.getOrDefault(j - 1, 0) + 1;
                newj2len.put(j, k);
                if (k > bestsize) {
                    besti = i - k + 1;
                    bestj = j - k + 1;
                    bestsize = k;
                }
            }
            j2len = newj2len;
        }

        while (besti > alo && bestj > blo && !bJunk.contains(b.charAt(bestj - 1)) && a.charAt(besti - 1) == b.charAt(bestj - 1)) {
            besti--;
            bestj--;
            bestsize++;
        }
        while (besti + bestsize < ahi && bestj + bestsize < bhi && !bJunk.contains(b.charAt(bestj + bestsize))
                        && a.charAt(besti + bestsize) == b.charAt(bestj + bestsize)) {
            bestsize++;
        }

        while (besti > alo && bestj > blo && bJunk.contains(b.charAt(bestj - 1)) && a.charAt(besti - 1) == b.charAt(bestj - 1)) {
            besti--;
            bestj--;
            bestsize++;
        }
        while (besti + bestsize < ahi && bestj + bestsize < bhi && bJunk.contains(b.charAt(bestj + bestsize))
                        && a.charAt(besti + bestsize) == b.charAt(bestj + bestsize)) {
            bestsize++;
        }

        return new Match(besti, bestj, bestsize);
    }

    /**
     * Return list of triples describing matching subsequences.
     * <p/>
     * Each triple is of the form <code>(i, j, n)</code>, and means that <code>a[i:i+n] == b[j:j+n]</code>. The triples are monotonically increasing in i and in j.
     * <p/>
     * New in Python 2.5, it's also guaranteed that if <code>(i, j, n)</code> and <code>(i', j', n')</code> are adjacent triples in the list, and the second is not the last triple in the list, then <code>i+n !=
     * i' or j+n != j'</code>. IOW, adjacent triples never describe adjacent equal blocks.
     * <p/>
     * The last triple is a dummy, <code>(len(a), len(b), 0)</code>, and is the only triple with <code>n==0</code>.
     */
    public List<Match> getMatchingBlocks() {
        if (matchingBlocks != null) {
            return matchingBlocks;
        }
        int la = a.length();
        int lb = b.length();

        // This is most naturally expressed as a recursive algorithm, but
        // at least one user bumped into extreme use cases that exceeded
        // the recursion limit on their box. So, now we maintain a list
        // ('queue') of blocks we still need to look at, and append partial
        // results to `matching_blocks` in a loop; the matches are sorted
        // at the end.

        List<int[]> queue = new ArrayList<>();
        queue.add(new int[] {0, la, 0, lb});
        List<Match> matchingBlocks = new ArrayList<>();

        while (!queue.isEmpty()) {
            int[] x = queue.remove(queue.size() - 1);
            int alo = x[0];
            int ahi = x[1];
            int blo = x[2];
            int bhi = x[3];
            Match match = findLongestMatch(alo, ahi, blo, bhi);
            int i = match.a;
            int j = match.b;
            int k = match.size;
            if (k > 0) {
                matchingBlocks.add(match);
                if (alo < i && blo < j) {
                    queue.add(new int[] {alo, i, blo, j});
                }
                if (i + k < ahi && j + k < bhi) {
                    queue.add(new int[] {i + k, ahi, j + k, bhi});
                }
            }
        }

        // It's possible that we have adjacent equal blocks in the
        // matching_blocks list now. Starting with 2.5, this code was added
        // to collapse them.
        matchingBlocks.sort(Comparator.comparingInt((Match m) -> m.a).thenComparingInt(m -> m.b));
        this.matchingBlocks = getNonAdjacentMatches(matchingBlocks, la, lb);
        return matchingBlocks;
    }

    private static List<Match> getNonAdjacentMatches(List<Match> matchingBlocks, int la, int lb) {
        List<Match> nonAdjacent = new ArrayList<>();
        int i1 = 0, j1 = 0, k1 = 0;
        for (Match match : matchingBlocks) {
            int i2 = match.a;
            int j2 = match.b;
            int k2 = match.size;
            if (i1 + k1 == i2 && j1 + k1 == j2) {
                k1 += k2;
            } else {
                if (k1 > 0) {
                    nonAdjacent.add(new Match(i1, j1, k1));
                }
                i1 = i2;
                j1 = j2;
                k1 = k2;
            }
        }
        if (k1 > 0) {
            nonAdjacent.add(new Match(i1, j1, k1));
        }
        nonAdjacent.add(new Match(la, lb, 0));
        return nonAdjacent;
    }

    /**
     * Return list of 5-tuples describing how to turn <code>a</code> into <code>b</code>.
     * <p/>
     * Each tuple is of the form <code>(tag, i1, i2, j1, j2)</code>. The first tuple has <code>i1 == j1 == 0</code>, and remaining tuples have <code>i1 == the i2</code> from the tuple preceding it,
     * and likewise for <code>j1 ==</code> the previous <code>j2</code>.
     * <p/>
     * The tags are strings, with these meanings:
     * <dl>
     * <dt>replace</dt>
     * <dd><code>a[i1:i2]</code> should be replaced by <code>b[j1:j2]</code></dd>
     * <dt>delete</dt>
     * <dd><code>a[i1:i2]</code> should be deleted. Note that <code>j1==j2</code> in this case.</dd>
     * <dt>insert</dt>
     * <dd><code>b[j1:j2]</code> should be inserted at <code>a[i1:i1]</code>. Note that <code>i1==i2</code> in this case.</dd>
     * <dt>equal</dt>
     * <dd><code>a[i1:i2] == b[j1:j2]</code></dd>
     * </dl>
     */
    public List<Opcode> getOpcodes() {
        if (opcodes != null) {
            return opcodes;
        }
        int i = 0;
        int j = 0;
        List<Opcode> opcodes = new ArrayList<>();
        for (Match match : getMatchingBlocks()) {
            int ai = match.a;
            int bj = match.b;
            int size = match.size;
            String tag = "";
            if (i < ai && j < bj) {
                tag = "replace";
            } else if (i < ai) {
                tag = "delete";
            } else if (j < bj) {
                tag = "insert";
            }
            if (!tag.isEmpty()) {
                opcodes.add(new Opcode(tag, i, ai, j, bj));
            }
            i = ai + size;
            j = bj + size;
            if (size > 0) {
                opcodes.add(new Opcode("equal", ai, i, bj, j));
            }
        }
        this.opcodes = opcodes;
        return opcodes;
    }

    /**
     * Return a measure of the sequences' similarity (float in <code>[0,1]</code>).
     * <p/>
     * Where <code>T</code> is the total number of elements in both sequences, and <code>M</code> is the number of matches, this is <code>2.0*M / T</code>. Note that this is 1 if the sequences are
     * identical, and 0 if they have nothing in common.
     * <p/>
     * <code>ratio()</code> is expensive to compute if you haven't already computed {@link #getMatchingBlocks()} or {@link #getOpcodes()}, in which case you may want to try
     * {@link #quickRatio()} or {@link #realQuickRatio()} first to get an upper bound.
     */
    public double ratio() {
        int matches = getMatchingBlocks().stream().mapToInt(m -> m.size).sum();
        return calculateRatio(matches, a.length() + b.length());
    }

    /**
     * Return an upper bound on {@link #ratio()} relatively quickly.
     * <p/>
     * This isn't defined beyond that it is an upper bound on {@link #ratio()}, and is faster to compute.
     */
    public double quickRatio() {
        if (fullBCount == null) {
            fullBCount = new HashMap<>();
            for (char elt : b.toCharArray()) {
                fullBCount.put(elt, fullBCount.getOrDefault(elt, 0) + 1);
            }
        }
        Map<Character,Integer> avail = new HashMap<>();
        int matches = 0;
        for (char elt : a.toCharArray()) {
            int numb = avail.getOrDefault(elt, fullBCount.getOrDefault(elt, 0));
            avail.put(elt, numb - 1);
            if (numb > 0) {
                matches++;
            }
        }
        return calculateRatio(matches, a.length() + b.length());
    }

    /**
     * Return an upper bound on {@link #ratio()} very quickly.
     * <p/>
     * This isn't defined beyond that it is an upper bound on {@link #ratio()}, and is faster to compute than either {@link #ratio()} or {@link #quickRatio()}.
     */
    public double realQuickRatio() {
        int la = a.length();
        int lb = b.length();
        return calculateRatio(Math.min(la, lb), la + lb);
    }

    /**
     * Get the set of junk characters in sequence b.
     * 
     * @return the set of characters in b that are considered junk
     */
    public Set<Character> getBJunk() {
        return new HashSet<>(bJunk);
    }

    private double calculateRatio(int matches, int length) {
        return 2.0 * matches / length;
    }

    /**
     * Use SequenceMatcher to return list of the best "good enough" matches.
     * <p/>
     *
     * @param word
     *            is a sequence for which close matches are desired (typically a string).
     * @param possibilities
     *            is a list of sequences against which to match word (typically a list of strings).
     * @param n
     *            (default 3) is the maximum number of close matches to return. n must be > 0.
     * @param cutoff
     *            (default 0.6) is a float in <code>[0, 1]</code>. Possibilities that don't score at least that similar to word are ignored.
     *
     * @return The best (no more than n) matches among the possibilities are returned in a list, sorted by similarity score, most similar first.
     */
    public static List<String> getCloseMatches(String word, List<String> possibilities, int n, double cutoff) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be > 0");
        }
        if (cutoff < 0.0 || cutoff > 1.0) {
            throw new IllegalArgumentException("cutoff must be in [0.0, 1.0]");
        }
        List<MatchResult> result = new ArrayList<>();
        SequenceMatcher s = new SequenceMatcher(null, "", word, true);
        for (String x : possibilities) {
            s.setSeqA(x);
            if (s.realQuickRatio() >= cutoff && s.quickRatio() >= cutoff && s.ratio() >= cutoff) {
                result.add(new MatchResult(s.ratio(), x));
            }
        }
        result.sort((a, b) -> Double.compare(b.score, a.score));
        List<String> matches = new ArrayList<>();
        for (int i = 0; i < Math.min(n, result.size()); i++) {
            matches.add(result.get(i).word);
        }
        return matches;
    }

    /** Interface for junk filter operations */
    public interface JunkFilter {
        /**
         * @param ch the character to evaluate.
         * @return true of the input character should be considered junk.
         */
        boolean isJunk(char ch);
    }

    public static final class Match {
        public final int a;
        public final int b;
        public final int size;

        public Match(int a, int b, int size) {
            this.a = a;
            this.b = b;
            this.size = size;
        }

        @Override
        public String toString() {
            return "Match{" + "a=" + a + ", b=" + b + ", size=" + size + '}';
        }
    }

    public static final class Opcode {
        public final String tag;
        public final int i1;
        public final int i2;
        public final int j1;
        public final int j2;

        public Opcode(String tag, int i1, int i2, int j1, int j2) {
            this.tag = tag;
            this.i1 = i1;
            this.i2 = i2;
            this.j1 = j1;
            this.j2 = j2;
        }
    }

    public static class MatchResult {
        public final double score;
        public final String word;

        public MatchResult(double score, String word) {
            this.score = score;
            this.word = word;
        }

        @Override
        public String toString() {
            return "MatchResult{" + "score=" + score + ", word='" + word + '\'' + '}';
        }
    }
}
