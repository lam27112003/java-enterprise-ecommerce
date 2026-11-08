package com.ecommerce.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

    @Test
    void bcryptHashesAndMatches() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("Phase1-Secret!");

        assertThat(hash).isNotEqualTo("Phase1-Secret!");
        assertThat(encoder.matches("Phase1-Secret!", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
