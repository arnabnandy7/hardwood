/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.hardwood.cli.command;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrintCommandTest implements PrintCommandContract {

    private final String VARIANT_FILE = Cli.resourcePath("/variant_test.parquet");

    private final String VARIANT_SHREDDED_FILE = Cli.resourcePath("/variant_shredded_test.parquet");

    private final String VARIANT_ATTRIBUTES_FILE = Cli.resourcePath("/variant_attributes_example.parquet");

    @Override
    public String plainFile() {
        return Cli.resourcePath("/plain_uncompressed.parquet");
    }

    @Override
    public String byteArrayFile() {
        return Cli.resourcePath("/delta_byte_array_test.parquet");
    }

    @Override
    public String deepNestedFile() {
        return Cli.resourcePath("/deep_nested_struct_test.parquet");
    }

    @Override
    public String listFile() {
        return Cli.resourcePath("/list_basic_test.parquet");
    }

    @Override
    public String nonexistentFile() {
        return "nonexistent.parquet";
    }

    @Override
    public String unsignedIntFile() {
        return Cli.resourcePath("/unsigned_int_test.parquet");
    }

    @Override
    public String multiRowGroupIntFile() {
        return Cli.resourcePath("/filter_pushdown_int.parquet");
    }

    @Test
    void rejectsRemoteUri() {
        Cli.Result result = Cli.launch("print", "-f", "gs://bucket/data.parquet");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.errorOutput()).isEqualTo("Remote URIs are not implemented yet.");
    }

    @Test
    void rendersUnshreddedVariantValuesAsDecodedScalars() {
        Cli.Result result = Cli.launch("print", "-f", VARIANT_FILE);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("""
                +----+-------+
                | id | var   |
                +----+-------+
                | 1  | true  |
                | 2  | false |
                | 3  | 42    |
                | 4  | "hi"  |
                +----+-------+""");
    }

    @Test
    void rendersShreddedVariantValuesAsDecodedScalars() {
        Cli.Result result = Cli.launch("print", "-f", VARIANT_SHREDDED_FILE);

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("""
                +----+---------------+
                | id | var           |
                +----+---------------+
                | 1  | 42            |
                | 2  | true          |
                | 3  | null          |
                | 4  | 1000000000000 |
                +----+---------------+""");
    }

    @Test
    void rendersVariantObjectAsJsonLikeText() {
        Cli.Result result = Cli.launch("print", "-f", VARIANT_ATTRIBUTES_FILE, "-w", "120");

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("""
                +----+-------------+-----------------------------------+
                | id | name        | value                             |
                +----+-------------+-----------------------------------+
                | 1  | age         | 42                                |
                | 1  | email       | "ada@example.com"                 |
                | 1  | preferences | {"opt_in": true, "theme": "dark"} |
                +----+-------------+-----------------------------------+""");
    }
}
