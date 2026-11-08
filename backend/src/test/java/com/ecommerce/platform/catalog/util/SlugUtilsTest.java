package com.ecommerce.platform.catalog.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugUtilsTest {

    @Test
    void slugifiesReadableNames() {
        assertThat(SlugUtils.slugify("Winter Jackets 2026!")).isEqualTo("winter-jackets-2026");
        assertThat(SlugUtils.slugify("  Café  Rouge ")).isEqualTo("cafe-rouge");
    }
}
