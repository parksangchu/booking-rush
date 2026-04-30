package com.couponrush.domain.accommodation.repository;

import com.couponrush.domain.accommodation.dto.HotelCardDto;
import com.couponrush.domain.accommodation.dto.SearchRequest;

import java.util.List;

public interface HotelSearchRepository {

    List<HotelCardDto> findHotels(SearchRequest req);

    long countHotels(SearchRequest req);
}
