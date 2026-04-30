package com.couponrush.domain.accommodation.dto;

public record HotelCardDto(
    Long hotelId,
    String name,
    String region,
    String city,
    String thumbnailUrl,
    Integer fromPrice,
    Double ratingAvg,
    Integer ratingCount
) {
}
