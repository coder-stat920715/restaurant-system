package com.example.restaurant.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {

    private Order newOrder() {
        return Order.create("c1", "loc-1", List.of(new OrderLine("burger", 1, BigDecimal.TEN)), BigDecimal.TEN);
    }

    @Test
    void happyPathIsLegal() {
        var o = newOrder();
        o.transitionTo(OrderStatus.PAYMENT_PENDING);
        o.transitionTo(OrderStatus.INVENTORY_RESERVED);
        o.transitionTo(OrderStatus.COMPLETED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void cancelIsAllowedBeforeCompletion() {
        var o = newOrder();
        o.transitionTo(OrderStatus.PAYMENT_PENDING);
        o.transitionTo(OrderStatus.CANCELLED);
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void terminalStatesCannotMove() {
        var done = newOrder();
        done.transitionTo(OrderStatus.PAYMENT_PENDING);
        done.transitionTo(OrderStatus.INVENTORY_RESERVED);
        done.transitionTo(OrderStatus.COMPLETED);
        assertThatThrownBy(() -> done.transitionTo(OrderStatus.CANCELLED)).isInstanceOf(IllegalStateException.class);

        var cancelled = newOrder();
        cancelled.transitionTo(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> cancelled.transitionTo(OrderStatus.PAYMENT_PENDING)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stepsCannotBeSkipped() {
        assertThatThrownBy(() -> newOrder().transitionTo(OrderStatus.COMPLETED)).isInstanceOf(IllegalStateException.class);
    }
}
