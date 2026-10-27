package com.evp.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Test support: finds SQL/JPQL that is built by string concatenation, String.format or
 * interpolation. Parameterised queries (named/positional parameters, Spring Data derived queries)
 * are the primary SQL-injection defence; this guard fails the build if anyone bypasses them.
 */
final class SqlConcatScanner {
    private SqlConcatScanner() { }

    // executing calls whose first argument is not a plain string literal, or is a concatenation/format
    private static final Pattern CALL = Pattern.compile(
            "\\.(createNativeQuery|createQuery|executeQuery|executeUpdate|execute|prepareStatement|queryForObject|queryForList|query|update|batchUpdate)\\s*\\(\\s*([^;]*)");
    private static final Pattern ANNOTATION_QUERY = Pattern.compile("@(Query|NamedQuery)\\s*\\(([^)]*)\\)");

    static List<String> scan(Path root) throws IOException {
        List<String> findings = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String src = Files.readString(f);
                String[] lines = src.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].strip();
                    if (line.startsWith("//") || line.startsWith("*")) {
                        continue;
                    }
                    if (line.contains("createStatement(")) {
                        findings.add(f + ":" + (i + 1) + " java.sql.Statement (use PreparedStatement)");
                    }
                    Matcher m = CALL.matcher(line);
                    while (m.find()) {
                        String arg = m.group(2);
                        if (looksLikeSql(arg) && isBuilt(arg)) {
                            findings.add(f + ":" + (i + 1) + " SQL built by concatenation/format");
                        }
                    }
                    Matcher a = ANNOTATION_QUERY.matcher(line);
                    while (a.find()) {
                        if (isBuilt(a.group(2)) && a.group(2).contains("\"")) {
                            findings.add(f + ":" + (i + 1) + " @Query built by concatenation");
                        }
                    }
                }
            }
        }
        return findings;
    }

    private static boolean looksLikeSql(String arg) {
        String a = arg.toLowerCase();
        return a.matches(".*\\b(select|insert|update|delete|from|where|set)\\b.*") || a.startsWith("sql") || a.startsWith("query");
    }

    static boolean isBuilt(String arg) {
        return arg.contains("\" +") || arg.contains("+ \"") || arg.contains("\"+") || arg.contains("+\"")
                || arg.contains("String.format") || arg.contains(".formatted(") || arg.contains(".concat(")
                || arg.contains("String.join") || arg.contains("${");
    }
}
