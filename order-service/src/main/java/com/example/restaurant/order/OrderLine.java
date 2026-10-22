package com.example.restaurant.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record OrderLine(@NotBlank String menuItemId, @Min(1) int quantity, @NotNull @Positive BigDecimal unitPrice) {}
