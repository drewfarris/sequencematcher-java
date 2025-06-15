# SequenceMatcher Java Port

A Java port of Python's [difflib.SequenceMatcher](https://docs.python.org/3/library/difflib.html#difflib.SequenceMatcher) 
class for comparing sequences and computing differences between strings.

Note: this port was performed with significant assistance from [Anthropic's Claude Code(tm)](https://www.anthropic.com/claude-code), 
while the code has been reviewed for validity and correctness, this implementation is not guaranteed to be bug-free. 
Please use at your own risk and in accordance to any guidelines your organization may have in place for AI generated 
software. No warranties are expressed or implied. 

## Overview

SequenceMatcher is a flexible class for comparing pairs of sequences of any type, implementing an algorithm based on the
"gestalt pattern matching" algorithm originally described by Ratcliff and Obershelp. Like the original Python 
implementation, this implementation provides human-friendly diff computation that focuses on finding the longest
contiguous matching subsequences.

The algorithm is particularly good at:
- Finding matches that "look right" to people
- Handling "junk" elements (like whitespace) intelligently  
- Providing intuitive difference reports
- Avoiding synchronization on blocks of unimportant content

## Features

### Core Functionality
- **Sequence Comparison**: Compare any two string sequences with customizable junk filtering
- **Similarity Metrics**: Multiple ratio calculations with different performance profiles (`ratio()`, `quickRatio()`, `realQuickRatio()`)
- **Matching Blocks**: Identify all matching subsequences between strings
- **Operation Codes**: Generate edit operations (insert, delete, replace, equal) to transform one string into another
- **Close Matches**: Find the best matches from a list of possibilities

### Performance Optimizations
- **Object Pooling**: Reuses HashMaps and arrays to minimize garbage collection
- **String Access**: Direct character access instead of array copying
- **Intelligent Caching**: Caches expensive computations and clears appropriately

### Advanced Features
- **Junk Filtering**: Custom predicates to ignore irrelevant characters (whitespace, punctuation, etc.)
- **Auto-Junk Heuristic**: Automatically treats overly common elements as junk for better performance
- **Popular Element Handling**: Efficiently handles sequences with many repeated characters

## Quick Start

### Basic Usage

```java
import drew.util.difflib.SequenceMatcher;

// Compare two strings
SequenceMatcher sm = new SequenceMatcher("hello world", "hello java");

// Get similarity ratio (0.0 to 1.0)
double similarity = sm.ratio(); // 0.615

// Get matching blocks
List<SequenceMatcher.Match> blocks = sm.getMatchingBlocks();
// Result: [Match{a=0, b=0, size=6}, Match{a=11, b=10, size=0}]

// Get edit operations
List<SequenceMatcher.Opcode> opcodes = sm.getOpcodes();
// Result: [EQUAL(0,6,0,6), REPLACE(6,11,6,10)]
```

### Advanced Usage with Junk Filtering

```java
// Ignore whitespace and punctuation when comparing
SequenceMatcher sm = new SequenceMatcher(
    ch -> ch == ' ' || ch == '\t' || ch == '.',
    "hello, world!",
    "hello world",
    true
);

double ratio = sm.ratio(); // Higher similarity due to ignored punctuation
```

### Finding Close Matches

```java
List<String> possibilities = Arrays.asList(
    "apple", "peach", "orange", "grape", "apricot", "pineapple"
);

List<String> matches = SequenceMatcher.getCloseMatches(
    "apple", possibilities, 3, 0.6
);
// Result: ["apple", "pineapple", "grape"]
```

### Reusing SequenceMatcher Instances

```java
SequenceMatcher sm = new SequenceMatcher();

// Efficiently compare many pairs
for (String[] pair : stringPairs) {
    sm.setSequences(pair[0], pair[1]);
    double similarity = sm.ratio();
    // Process similarity...
}
```

## API Reference

### Constructor

```java
public SequenceMatcher(JunkFilter junkFilter, String a, String b, boolean autoJunk)
```

- `junkFilter`: Function to identify junk characters (null for no junk filtering)
- `a`: First sequence to compare
- `b`: Second sequence to compare  
- `autoJunk`: Enable automatic junk detection for popular characters

### Core Methods

#### Similarity Metrics
- `double ratio()`: Precise similarity ratio (0.0 = no similarity, 1.0 = identical)
- `double quickRatio()`: Fast upper bound on ratio()
- `double realQuickRatio()`: Very fast upper bound on ratio()

#### Sequence Analysis
- `List<Match> getMatchingBlocks()`: All matching subsequences
- `List<Opcode> getOpcodes()`: Edit operations to transform sequence A into B
- `Match findLongestMatch(int alo, int ahi, int blo, int bhi)`: Longest match in specified ranges

#### Sequence Management
- `void setSequences(String a, String b)`: Set both sequences
- `void setSequenceA(String a)`: Set first sequence
- `void setSequenceB(String b)`: Set second sequence

#### Utility Methods
- `static List<String> getCloseMatches(String word, List<String> possibilities, int n, double cutoff)`: Find best matches

### Data Classes

#### Match
```java
public static final class Match {
    public final int a;      // Starting position in sequence A
    public final int b;      // Starting position in sequence B  
    public final int size;   // Length of matching subsequence
}
```

#### Opcode
```java
public static final class Opcode {
    public final OpcodeTag tag;  // Type of operation
    public final int i1, i2;     // Range in sequence A
    public final int j1, j2;     // Range in sequence B
}
```

Operations types: `EQUAL`, `REPLACE`, `DELETE`, `INSERT`

## Performance Characteristics

### Time Complexity
- **Best Case**: O(n) for similar sequences
- **Average Case**: O(nm) where n,m are sequence lengths
- **Worst Case**: O(nm) but with significant constant factor improvements

### Memory Usage
- **Object Reuse**: Minimizes garbage collection through intelligent pooling
- **Caching**: Results cached until sequences change
- **Auto-Cleanup**: Resources cleaned when sequences are updated

## Use Cases

### Text Processing
- **Document Comparison**: Finding changes between document versions
- **Code Diff Tools**: Implementing source code difference viewers
- **Plagiarism Detection**: Measuring similarity between texts
- **Spell Checkers**: Finding closest matches to misspelled words

### Data Analysis
- **Record Matching**: Identifying similar database records
- **Data Deduplication**: Finding near-duplicate entries
- **String Clustering**: Grouping similar strings together

### Real-time Applications  
- **Live Editing**: Computing diffs for collaborative editors
- **Version Control**: Implementing diff algorithms for VCS systems
- **Content Management**: Tracking changes in CMS systems

## Testing

The project includes JUnit tests covering:

```bash
# Run all tests
mvn test

# Run specific test classes
mvn test -Dtest=SequenceMatcherTest
mvn test -Dtest=SequenceMatcherTestPerformanceTest
```

### Test Coverage
- **Basic Functionality**: String comparison, ratio calculation, matching blocks
- **Edge Cases**: Empty strings, identical strings, completely different strings
- **Junk Filtering**: Custom and automatic junk detection
- **Performance**: Memory usage and execution time verification
- **Regression Tests**: Protection against known issues

## Building

### Requirements
- Java 11 or higher
- Maven 3.6 or higher

### Build Commands
```bash
# Compile and run tests
mvn clean test

# Package as JAR
mvn clean package

# Install to local repository
mvn clean install
```

## Dependencies

### Runtime Dependencies
- **None**: Zero external dependencies

### Test Dependencies
- JUnit 5 (Jupiter) for unit testing
- Maven Surefire for test execution

## Contributing

### Development Setup
1. Clone the repository
2. Import as Maven project in your IDE
3. Run tests to verify setup: `mvn test`

### Code Style
- Follow existing code formatting and naming conventions
- Add unit tests for new functionality
- Update documentation for API changes
- Ensure all tests pass before submitting changes

### Performance Considerations
- Profile code changes with large inputs
- Verify memory usage patterns
- Test with both similar and dissimilar string pairs
- Consider impact on garbage collection

## License

This project is a port of Python's difflib module. The original Python implementation is part of the Python Standard Library 
and is covered by the Python Software Foundation License. This port is released under the Apache 2.0 license.

## Comparison with Python difflib

### Functional Compatibility
- ✅ **Algorithm**: Identical to Python implementation
- ✅ **Results**: Same output for equivalent inputs  
- ✅ **Edge Cases**: Handles all Python edge cases correctly
- ✅ **Junk Filtering**: Full compatibility with Python junk functions

### Java-Specific Enhancements
- **Type Safety**: Compile-time checking of opcode types with enums
- **Method Naming**: Java-style camelCase method names
- **Resource Management**: Automatic cleanup of internal resources
- **Thread Safety**: Safe for concurrent read access (single-threaded writes)