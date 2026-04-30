package com.couponrush.domain.accommodation.controller;

import com.couponrush.domain.accommodation.dto.SearchRequest;
import com.couponrush.domain.accommodation.dto.SearchResponse;
import com.couponrush.domain.accommodation.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/hotels")
    public SearchResponse searchHotels(
        @RequestParam String region,
        @RequestParam("check_in") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @RequestParam("check_out") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
        @RequestParam Integer guests,
        @RequestParam(name = "min_price", required = false) Integer minPrice,
        @RequestParam(name = "max_price", required = false) Integer maxPrice,
        @RequestParam(name = "min_rating", required = false) Double minRating,
        @RequestParam(defaultValue = "popular") String sort,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        SearchRequest req = new SearchRequest(
            region, checkIn, checkOut, guests,
            minPrice, maxPrice, minRating,
            sort, page, size
        );
        return searchService.search(req);
    }
}
