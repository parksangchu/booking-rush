package com.couponrush.domain.accommodation.service;

import com.couponrush.domain.accommodation.dto.HotelCardDto;
import com.couponrush.domain.accommodation.dto.SearchRequest;
import com.couponrush.domain.accommodation.dto.SearchResponse;
import com.couponrush.domain.accommodation.exception.SearchException;
import com.couponrush.domain.accommodation.repository.HotelSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Set<String> VALID_SORTS = Set.of("popular", "price_asc", "price_desc", "rating");

    private final HotelSearchRepository hotelSearchRepository;

    public SearchResponse search(SearchRequest req) {
        validate(req);
        List<HotelCardDto> items = hotelSearchRepository.findHotels(req);
        long total = hotelSearchRepository.countHotels(req);
        return new SearchResponse(total, req.page(), req.size(), items);
    }

    private void validate(SearchRequest req) {
        if (req.region() == null || req.region().isBlank()) {
            throw new SearchException("region은 필수입니다");
        }
        if (req.checkIn() == null || req.checkOut() == null) {
            throw new SearchException("check_in, check_out은 필수입니다");
        }
        if (!req.checkOut().isAfter(req.checkIn())) {
            throw new SearchException("check_out은 check_in 이후여야 합니다");
        }
        if (req.guests() == null || req.guests() < 1) {
            throw new SearchException("guests는 1 이상이어야 합니다");
        }
        if (req.size() < 1 || req.size() > 100) {
            throw new SearchException("size는 1~100 사이여야 합니다");
        }
        if (req.page() < 1) {
            throw new SearchException("page는 1 이상이어야 합니다");
        }
        if (req.sort() != null && !VALID_SORTS.contains(req.sort())) {
            throw new SearchException("지원하지 않는 정렬: " + req.sort());
        }
    }
}
