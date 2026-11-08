package com.ecommerce.platform.support;

import org.testcontainers.DockerClientFactory;

/**
 * Hard gate for Testcontainers-based ITs.
 * Fails the test (does not skip) when Docker is unavailable so CI cannot silently pass.
 */
public final class DockerInfrastructure {

    private DockerInfrastructure() {
    }

    public static void requireDocker(String suiteName) {
        try {
            DockerClientFactory.instance().client();
        } catch (Throwable ex) {
            throw new AssertionError(
                    suiteName + " requires a running Docker engine for Testcontainers, but Docker is unavailable. "
                            + "Exact cause: " + ex.getClass().getName() + ": " + ex.getMessage(),
                    ex);
        }
    }
}
