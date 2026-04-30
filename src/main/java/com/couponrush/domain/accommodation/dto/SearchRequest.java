package com.couponrush.domain.accommodation.dto;

import java.time.LocalDate;

public record SearchRequest(
    String region,
    LocalDate checkIn,
    LocalDate checkOut,
    Integer guests,
    Integer minPrice,
    Integer maxPrice,
    Double minRating,
    String sort,
    int page,
    int size
) {
    public int offset() {
        return (page - 1) * size;
    }

    public int effectiveMinPrice() {
        return minPrice == null ? 0 : minPrice;
    }

    public int effectiveMaxPrice() {
        return maxPrice == null ? Integer.MAX_VALUE : maxPrice;
    }

    public double effectiveMinRating() {
        return minRating == null ? 0.0 : minRating;
    }
}
