package com.couponrush.domain.accommodation.dto;

import java.util.List;

public record SearchResponse(
    long total,
    int page,
    int size,
    List<HotelCardDto> items
) {
}
