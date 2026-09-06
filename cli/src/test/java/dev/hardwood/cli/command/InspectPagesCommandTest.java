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

class InspectPagesCommandTest implements InspectPagesCommandContract {

    @Override
    public String plainFile() {
        return Cli.resourcePath("/plain_uncompressed.parquet");
    }

    @Override
    public String dictFile() {
        return Cli.resourcePath("/dictionary_uncompressed.parquet");
    }

    @Override
    public String pageIndexFile() {
        return Cli.resourcePath("/column_index_pushdown.parquet");
    }

    @Override
    public String longValueFile() {
        return Cli.resourcePath("/cli_long_value_test.parquet");
    }

    @Override
    public String nestedFile() {
        return Cli.resourcePath("/list_basic_test.parquet");
    }

    @Override
    public String nonexistentFile() {
        return "nonexistent.parquet";
    }

    @Test
    void rejectsRemoteUri() {
        Cli.Result result = Cli.launch("inspect", "pages", "-f", "gs://bucket/data.parquet");

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.errorOutput()).contains("not implemented yet");
    }
}
