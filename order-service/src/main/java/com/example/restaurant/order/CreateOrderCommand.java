package com.example.restaurant.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateOrderCommand(@NotBlank String locationId, @NotEmpty List<@Valid OrderLine> lines) {}
