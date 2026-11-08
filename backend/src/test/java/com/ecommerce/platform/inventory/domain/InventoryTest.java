package com.ecommerce.platform.inventory.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void reserveReleaseAndCommitMaintainInvariants() {
        Inventory inventory = new Inventory(UUID.randomUUID());
        inventory.applyAvailableDelta(10);

        inventory.reserve(4);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(6);
        assertThat(inventory.getQuantityReserved()).isEqualTo(4);

        inventory.release(1);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
        assertThat(inventory.getQuantityReserved()).isEqualTo(3);

        inventory.commit(2);
        assertThat(inventory.getQuantityAvailable()).isEqualTo(7);
        assertThat(inventory.getQuantityReserved()).isEqualTo(1);
    }

    @Test
    void rejectsNegativeAvailableAndOversell() {
        Inventory inventory = new Inventory(UUID.randomUUID());
        inventory.applyAvailableDelta(2);

        assertThatThrownBy(() -> inventory.applyAvailableDelta(-3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inventory.reserve(3))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inventory.release(1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inventory.commit(1))
                .isInstanceOf(IllegalStateException.class);
    }
}
