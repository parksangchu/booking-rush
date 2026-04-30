package com.couponrush.domain.accommodation;

import com.couponrush.IntegrationTestBase;
import com.couponrush.domain.accommodation.dto.HotelCardDto;
import com.couponrush.domain.accommodation.dto.SearchRequest;
import com.couponrush.domain.accommodation.dto.SearchResponse;
import com.couponrush.domain.accommodation.entity.Hotel;
import com.couponrush.domain.accommodation.entity.Room;
import com.couponrush.domain.accommodation.entity.RoomInventory;
import com.couponrush.domain.accommodation.exception.SearchException;
import com.couponrush.domain.accommodation.repository.HotelRepository;
import com.couponrush.domain.accommodation.repository.RoomInventoryRepository;
import com.couponrush.domain.accommodation.repository.RoomRepository;
import com.couponrush.domain.accommodation.service.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SearchService 통합 테스트 (Step 1 Baseline)")
class SearchServiceTest extends IntegrationTestBase {

    @Autowired
    private SearchService searchService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomInventoryRepository inventoryRepository;

    private static final LocalDate CHECK_IN = LocalDate.of(2026, 5, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 5, 3);  // 2박

    @AfterEach
    void cleanup() {
        inventoryRepository.deleteAllInBatch();
        roomRepository.deleteAllInBatch();
        hotelRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("region 필터: 일치하는 호텔만 결과에 포함된다")
    void searchByRegion() {
        // given: 제주 1개, 부산 1개
        Hotel jeju = saveHotel("제주", 4.5, 100);
        Hotel busan = saveHotel("부산", 4.5, 100);
        Room jejuRoom = saveRoom(jeju, 2);
        Room busanRoom = saveRoom(busan, 2);
        saveInventoryRange(jejuRoom, 100000);
        saveInventoryRange(busanRoom, 100000);

        // when
        SearchResponse resp = searchService.search(buildRequest("제주", null, null, "popular"));

        // then
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).region()).isEqualTo("제주");
        assertThat(resp.total()).isEqualTo(1);
    }

    @Test
    @DisplayName("인원 필터: max_coverage 미달 객실은 제외된다")
    void searchByGuests() {
        Hotel small = saveHotel("제주", 4.5, 100);
        Hotel big = saveHotel("제주", 4.5, 100);
        saveInventoryRange(saveRoom(small, 2), 100000);   // 2명
        saveInventoryRange(saveRoom(big, 4), 100000);     // 4명

        // 4명 검색 → big만 남아야 함
        SearchRequest req = new SearchRequest("제주", CHECK_IN, CHECK_OUT, 4,
            null, null, null, "popular", 1, 20);
        SearchResponse resp = searchService.search(req);

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).hotelId()).isEqualTo(big.getId());
    }

    @Test
    @DisplayName("가격 필터: min/max 범위 밖 호텔은 제외된다")
    void searchByPrice() {
        Hotel cheap = saveHotel("제주", 4.0, 50);
        Hotel mid = saveHotel("제주", 4.0, 50);
        Hotel expensive = saveHotel("제주", 4.0, 50);
        saveInventoryRange(saveRoom(cheap, 2), 50000);
        saveInventoryRange(saveRoom(mid, 2), 150000);
        saveInventoryRange(saveRoom(expensive, 2), 500000);

        SearchRequest req = new SearchRequest("제주", CHECK_IN, CHECK_OUT, 2,
            100000, 200000, null, "popular", 1, 20);
        SearchResponse resp = searchService.search(req);

        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).hotelId()).isEqualTo(mid.getId());
    }

    @Test
    @DisplayName("정렬 popular: rating_count 내림차순")
    void sortByPopular() {
        Hotel low = saveHotel("제주", 4.5, 10);
        Hotel high = saveHotel("제주", 4.5, 1000);
        Hotel mid = saveHotel("제주", 4.5, 100);
        saveInventoryRange(saveRoom(low, 2), 100000);
        saveInventoryRange(saveRoom(high, 2), 100000);
        saveInventoryRange(saveRoom(mid, 2), 100000);

        SearchResponse resp = searchService.search(buildRequest("제주", null, null, "popular"));

        assertThat(resp.items()).extracting(HotelCardDto::hotelId)
            .containsExactly(high.getId(), mid.getId(), low.getId());
    }

    @Test
    @DisplayName("정렬 price_asc: from_price 오름차순")
    void sortByPriceAsc() {
        Hotel a = saveHotel("제주", 4.5, 100);
        Hotel b = saveHotel("제주", 4.5, 100);
        Hotel c = saveHotel("제주", 4.5, 100);
        saveInventoryRange(saveRoom(a, 2), 200000);
        saveInventoryRange(saveRoom(b, 2), 80000);
        saveInventoryRange(saveRoom(c, 2), 130000);

        SearchResponse resp = searchService.search(buildRequest("제주", null, null, "price_asc"));

        assertThat(resp.items()).extracting(HotelCardDto::hotelId)
            .containsExactly(b.getId(), c.getId(), a.getId());
    }

    @Test
    @Transactional
    @DisplayName("매진 날짜가 하나라도 있으면 결과에서 제외된다")
    void excludeSoldOutDates() {
        Hotel hotel = saveHotel("제주", 4.5, 100);
        Room room = saveRoom(hotel, 2);
        // 5/1: 가용, 5/2: 매진
        inventoryRepository.save(new RoomInventory(room, CHECK_IN, 100000, 1));
        RoomInventory soldOut = new RoomInventory(room, CHECK_IN.plusDays(1), 100000, 1);
        inventoryRepository.save(soldOut);
        // CAS로 used_quantity = max_quantity (매진)
        int affected = inventoryRepository.decrementAvailability(soldOut.getId());
        assertThat(affected).isEqualTo(1);

        SearchResponse resp = searchService.search(buildRequest("제주", null, null, "popular"));

        assertThat(resp.items()).isEmpty();
    }

    @Test
    @DisplayName("빈 결과: 일치하는 호텔이 없으면 빈 리스트")
    void emptyResult() {
        Hotel hotel = saveHotel("부산", 4.5, 100);
        saveInventoryRange(saveRoom(hotel, 2), 100000);

        SearchResponse resp = searchService.search(buildRequest("제주", null, null, "popular"));

        assertThat(resp.items()).isEmpty();
        assertThat(resp.total()).isZero();
    }

    @Test
    @DisplayName("잘못된 정렬값은 SearchException")
    void invalidSort() {
        Hotel hotel = saveHotel("제주", 4.5, 100);
        saveInventoryRange(saveRoom(hotel, 2), 100000);

        assertThatThrownBy(() -> searchService.search(
            buildRequest("제주", null, null, "unknown")
        )).isInstanceOf(SearchException.class);
    }

    @Test
    @DisplayName("check_out이 check_in보다 이르면 SearchException")
    void invalidDateRange() {
        SearchRequest req = new SearchRequest("제주",
            LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 1),
            2, null, null, null, "popular", 1, 20);

        assertThatThrownBy(() -> searchService.search(req))
            .isInstanceOf(SearchException.class);
    }

    // ---------- helpers ----------

    private Hotel saveHotel(String region, double ratingAvg, int ratingCount) {
        return hotelRepository.save(new Hotel(
            "Hotel " + System.nanoTime(), region, region + "시",
            ratingAvg, ratingCount, "thumb"));
    }

    private Room saveRoom(Hotel hotel, int maxCoverage) {
        return roomRepository.save(new Room(hotel, "스탠다드", maxCoverage, "Queen", 30));
    }

    private void saveInventoryRange(Room room, int price) {
        for (LocalDate d = CHECK_IN; d.isBefore(CHECK_OUT); d = d.plusDays(1)) {
            inventoryRepository.save(new RoomInventory(room, d, price, 1));
        }
    }

    private SearchRequest buildRequest(String region, Integer minPrice, Integer maxPrice, String sort) {
        return new SearchRequest(region, CHECK_IN, CHECK_OUT, 2,
            minPrice, maxPrice, null, sort, 1, 20);
    }
}
