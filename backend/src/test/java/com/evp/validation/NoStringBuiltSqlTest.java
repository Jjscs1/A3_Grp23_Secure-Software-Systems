package com.evp.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoStringBuiltSqlTest {

    @Test
    void productionCodeNeverBuildsSqlFromStrings() throws Exception {
        Path main = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.isDirectory(main)) {
            return; // run from backend/ (maven does this)
        }
        assertEquals(List.of(), SqlConcatScanner.scan(main));
    }

    @Test
    void scannerDetectsUnsafePatterns(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Bad.java"), String.join("\n",
                "class Bad {",
                "  void a(String name) { em.createNativeQuery(\"select * from voter where name = '\" + name + \"'\"); }",
                "  void b(String n) { jdbc.query(String.format(\"select * from voter where name='%s'\", n), mapper); }",
                "  void c() { conn.createStatement(); }",
                "  @Query(\"select v from Voter v where v.name = '\" + X + \"'\")",
                "  void d() { }",
                "}"));
        List<String> findings = SqlConcatScanner.scan(dir);
        assertTrue(findings.size() >= 4, findings.toString());
    }

    @Test
    void scannerAcceptsParameterisedQueries(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Good.java"), String.join("\n",
                "class Good {",
                "  void a(String n) { em.createNativeQuery(\"select * from voter where name = :n\").setParameter(\"n\", n); }",
                "  void b(String n) { jdbc.query(\"select * from voter where name = ?\", mapper, n); }",
                "  @Query(\"select v from Voter v where v.name = :name\")",
                "  void c() { }",
                "}"));
        assertFalse(SqlConcatScanner.scan(dir).iterator().hasNext(), SqlConcatScanner.scan(dir).toString());
    }
}
