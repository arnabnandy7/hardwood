/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.hardwood;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.hardwood.reader.ParquetFileReader;
import dev.hardwood.reader.RowReader;

import static org.assertj.core.api.Assertions.assertThat;

/// Differential **value** comparison: reads every row of the typed corpus through hardwood
/// and through DuckDB and asserts the decoded values agree, column by column, across the
/// physical and logical types (`INT32`/`INT64`/`FLOAT`/`DOUBLE`/`BOOLEAN`/`STRING`, plus the
/// `DATE`/`TIMESTAMP`/`DECIMAL` logical types and raw `BYTE_ARRAY`).
///
/// Where [DifferentialReadTest] checks that the *right rows* are selected, this checks that
/// each row *decodes* to the right value — the conformance layer that catches decode bugs of the
/// #537 class. Float/double are compared exactly (a reader must reproduce stored bits); decimals
/// by numeric value; dates/timestamps as `LocalDate`/`Instant`.
///
/// The same comparison runs over `REQUIRED` columns and over their `OPTIONAL` mirror, where every
/// third row is null. A column's null flag must match the oracle's on every row, and its value is
/// compared only where the oracle has one, so the definition-level decode is checked alongside the
/// values.
@Tag("differential")
class DifferentialValueTest {

    private static final Path FILE = Paths.get("src/test/resources/differential/diff_types.parquet");
    private static final Path OPTIONAL_FILE =
            Paths.get("src/test/resources/differential/diff_types_optional.parquet");
    private static final long EXPECTED_ROWS = 200;
    /// Every third of the 200 rows (67) is null in each of the 10 `OPTIONAL` value columns.
    private static final long OPTIONAL_NULL_CELLS = 67 * 10;

    @Test
    void decodedValuesMatchOracleAcrossTypes() throws Exception {
        assertMatchesOracle(FILE, 0);
    }

    @Test
    void decodedValuesAndNullsMatchOracleAcrossTypes() throws Exception {
        assertMatchesOracle(OPTIONAL_FILE, OPTIONAL_NULL_CELLS);
    }

    /// Reads every row of `file` through hardwood and DuckDB and compares them column by column,
    /// then asserts the oracle reported `expectedNullCells` nulls, so a corpus that has lost its
    /// nulls cannot pass as one that covers them.
    private static void assertMatchesOracle(Path file, long expectedNullCells) throws Exception {
        String sql = "SELECT * FROM read_parquet('" + file.toAbsolutePath() + "') ORDER BY __row__";
        try (ParquetFileReader reader = ParquetFileReader.open(InputFile.of(file));
             RowReader rows = reader.buildRowReader().build();
             Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            long seen = 0;
            long nullCells = 0;
            while (rs.next()) {
                assertThat(rows.hasNext()).as("hardwood has a row for oracle row %d", seen).isTrue();
                rows.next();
                long row = rows.getLong("__row__");
                assertThat(row).as("row order").isEqualTo(rs.getLong("__row__"));

                nullCells += compareCell(rows, rs, "i32", row,
                        () -> assertThat(rows.getInt("i32")).as("i32 @%d", row).isEqualTo(rs.getInt("i32")));
                nullCells += compareCell(rows, rs, "i64", row,
                        () -> assertThat(rows.getLong("i64")).as("i64 @%d", row).isEqualTo(rs.getLong("i64")));
                nullCells += compareCell(rows, rs, "f32", row,
                        () -> assertThat(rows.getFloat("f32")).as("f32 @%d", row).isEqualTo(rs.getFloat("f32")));
                nullCells += compareCell(rows, rs, "f64", row,
                        () -> assertThat(rows.getDouble("f64")).as("f64 @%d", row).isEqualTo(rs.getDouble("f64")));
                nullCells += compareCell(rows, rs, "flag", row,
                        () -> assertThat(rows.getBoolean("flag")).as("flag @%d", row).isEqualTo(rs.getBoolean("flag")));
                nullCells += compareCell(rows, rs, "s", row,
                        () -> assertThat(rows.getString("s")).as("s @%d", row).isEqualTo(rs.getString("s")));
                nullCells += compareCell(rows, rs, "d", row,
                        () -> assertThat(rows.getDate("d")).as("d @%d", row).isEqualTo(rs.getObject("d", LocalDate.class)));
                nullCells += compareCell(rows, rs, "ts", row,
                        () -> assertThat(rows.getTimestamp("ts")).as("ts @%d", row)
                                .isEqualTo(rs.getObject("ts", OffsetDateTime.class).toInstant()));
                nullCells += compareCell(rows, rs, "dec", row,
                        () -> assertThat(rows.getDecimal("dec")).as("dec @%d", row)
                                .isEqualByComparingTo(rs.getBigDecimal("dec")));
                nullCells += compareCell(rows, rs, "bin", row, () -> {
                    Blob bin = rs.getBlob("bin");
                    assertThat(rows.getBinary("bin")).as("bin @%d", row)
                            .isEqualTo(bin.getBytes(1L, Math.toIntExact(bin.length())));
                });

                seen++;
            }
            assertThat(rows.hasNext()).as("hardwood exhausted when oracle is").isFalse();
            assertThat(seen).isEqualTo(EXPECTED_ROWS);
            assertThat(nullCells).as("null cells in %s", file.getFileName()).isEqualTo(expectedNullCells);
        }
    }

    /// Asserts that `column` is null in hardwood exactly when it is null in the oracle, then runs
    /// `valueCheck` where the oracle has a value. Returns 1 for a null cell and 0 otherwise.
    private static int compareCell(RowReader rows, ResultSet rs, String column, long row, ValueCheck valueCheck)
            throws SQLException {
        boolean oracleNull = rs.getObject(column) == null;
        assertThat(rows.isNull(column)).as("%s null @%d", column, row).isEqualTo(oracleNull);
        if (oracleNull) {
            return 1;
        }
        valueCheck.run();
        return 0;
    }

    /// One column's value comparison, reading the oracle through JDBC.
    @FunctionalInterface
    private interface ValueCheck {
        void run() throws SQLException;
    }
}
