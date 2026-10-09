/*
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Copyright The original authors
 *
 *  Licensed under the Apache Software License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package dev.hardwood.cli;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;

/// Resolves test resources to file system paths.
public final class TestResources {

    private TestResources() {
    }

    /// The file system path of a classpath resource, such as `/plain_uncompressed.parquet`.
    /// Goes through the resource's URI rather than [URL#getPath()], whose `/C:/...` form
    /// is not a valid path on Windows.
    public static Path path(String resource) {
        URL url = TestResources.class.getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("Test resource not found: " + resource);
        }
        try {
            return Path.of(url.toURI());
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException("Test resource has no valid URI: " + url, e);
        }
    }
}
