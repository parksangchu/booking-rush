package com.couponrush.domain.accommodation.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Slf4j
@Component
@Profile("seed")
@RequiredArgsConstructor
public class AccommodationSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Value("${accommodation.seed.hotel-count:1000}")
    private int hotelCount;

    @Value("${accommodation.seed.rooms-per-hotel:5}")
    private int roomsPerHotel;

    @Value("${accommodation.seed.reviews-per-hotel:30}")
    private int reviewsPerHotel;

    @Value("${accommodation.seed.year-days:365}")
    private int yearDays;

    @Value("${accommodation.seed.start-date:2026-05-01}")
    private String startDateStr;

    @Value("${accommodation.seed.batch-size:5000}")
    private int batchSize;

    private static final List<RegionInfo> REGIONS = List.of(
        new RegionInfo("제주", 30, List.of("제주시", "서귀포시")),
        new RegionInfo("강릉", 15, List.of("강릉시", "양양군")),
        new RegionInfo("부산", 15, List.of("해운대구", "광안리", "남포동")),
        new RegionInfo("서울", 10, List.of("강남구", "마포구", "종로구")),
        new RegionInfo("경주", 8, List.of("경주시")),
        new RegionInfo("여수", 7, List.of("여수시")),
        new RegionInfo("속초", 5, List.of("속초시")),
        new RegionInfo("기타", 10, List.of("기타지역"))
    );

    private static final String[] ROOM_NAMES = {"스탠다드 더블", "스탠다드 트윈", "디럭스 더블", "스위트", "패밀리"};
    private static final int[] ROOM_COVERAGES = {2, 2, 2, 2, 4};

    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Accommodation Seeder 시작 ===");
        log.info("hotelCount={}, roomsPerHotel={}, yearDays={}, batchSize={}",
            hotelCount, roomsPerHotel, yearDays, batchSize);

        Random rnd = new Random();

        cleanAll();

        long t0 = System.currentTimeMillis();
        long hotelStartId = seedHotels(rnd);
        log.info("hotels: {}ms (startId={})", System.currentTimeMillis() - t0, hotelStartId);

        long t1 = System.currentTimeMillis();
        long roomStartId = seedRooms(rnd, hotelStartId);
        log.info("rooms: {}ms (startId={})", System.currentTimeMillis() - t1, roomStartId);

        long t2 = System.currentTimeMillis();
        int invCount = seedInventories(rnd, roomStartId);
        log.info("inventories: {}ms ({} rows)", System.currentTimeMillis() - t2, invCount);

        long t3 = System.currentTimeMillis();
        int reviewCount = seedReviews(rnd, hotelStartId);
        log.info("reviews: {}ms ({} rows)", System.currentTimeMillis() - t3, reviewCount);

        log.info("=== Accommodation Seeder 완료 ({}ms) ===", System.currentTimeMillis() - t0);
    }

    private void cleanAll() {
        log.info("기존 데이터 삭제 중 (TRUNCATE — AUTO_INCREMENT 리셋)...");
        jdbc.execute("TRUNCATE TABLE reviews");
        jdbc.execute("TRUNCATE TABLE room_inventories");
        jdbc.execute("TRUNCATE TABLE rooms");
        jdbc.execute("TRUNCATE TABLE hotels");
    }

    private long seedHotels(Random rnd) {
        Long currentMaxId = jdbc.queryForObject(
            "SELECT COALESCE(MAX(id), 0) FROM hotels", Long.class);
        long startId = (currentMaxId == null ? 0 : currentMaxId) + 1;

        String sql = """
            INSERT INTO hotels (name, region, city,
                rating_avg, rating_count, thumbnail_url, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        int popularThreshold = (int) (hotelCount * 0.1);
        List<Object[]> batch = new ArrayList<>(batchSize);
        LocalDateTime now = LocalDateTime.now();

        for (int i = 1; i <= hotelCount; i++) {
            RegionInfo region = pickRegion(rnd);
            String city = region.cities.get(rnd.nextInt(region.cities.size()));
            boolean popular = i <= popularThreshold;

            double rating = popular
                ? 4.0 + rnd.nextDouble()
                : 3.0 + rnd.nextDouble() * 2.0;
            int reviewCount = popular
                ? 100 + rnd.nextInt(900)
                : 5 + rnd.nextInt(100);

            batch.add(new Object[] {
                "Hotel " + i + " " + region.region,
                region.region,
                city,
                Math.round(rating * 10.0) / 10.0,
                reviewCount,
                "https://example.com/hotel/" + i + ".jpg",
                Timestamp.valueOf(now)
            });

            if (batch.size() >= batchSize) {
                jdbc.batchUpdate(sql, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);
        return startId;
    }

    private long seedRooms(Random rnd, long hotelStartId) {
        Long currentMaxId = jdbc.queryForObject(
            "SELECT COALESCE(MAX(id), 0) FROM rooms", Long.class);
        long startId = (currentMaxId == null ? 0 : currentMaxId) + 1;

        String sql = """
            INSERT INTO rooms (hotel_id, name, max_coverage, bed_config, area)
            VALUES (?, ?, ?, ?, ?)
            """;

        List<Object[]> batch = new ArrayList<>(batchSize);
        for (long hotelOffset = 0; hotelOffset < hotelCount; hotelOffset++) {
            long hotelId = hotelStartId + hotelOffset;
            for (int r = 0; r < roomsPerHotel; r++) {
                int idx = r % ROOM_NAMES.length;
                batch.add(new Object[] {
                    hotelId,
                    ROOM_NAMES[idx],
                    ROOM_COVERAGES[idx],
                    "Queen Bed",
                    25 + rnd.nextInt(40)
                });
                if (batch.size() >= batchSize) {
                    jdbc.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);
        return startId;
    }

    private int seedInventories(Random rnd, long roomStartId) {
        String sql = """
            INSERT INTO room_inventories (room_id, date, price, max_quantity, used_quantity)
            VALUES (?, ?, ?, ?, ?)
            """;

        LocalDate startDate = LocalDate.parse(startDateStr);
        int totalRooms = hotelCount * roomsPerHotel;
        int totalRows = 0;
        List<Object[]> batch = new ArrayList<>(batchSize);

        for (int roomOffset = 0; roomOffset < totalRooms; roomOffset++) {
            long roomId = roomStartId + roomOffset;
            int basePrice = 50000 + rnd.nextInt(450000);
            int maxQty = 1 + rnd.nextInt(3);

            for (int d = 0; d < yearDays; d++) {
                LocalDate date = startDate.plusDays(d);
                int dayOfWeek = date.getDayOfWeek().getValue();
                double mult = (dayOfWeek >= 5)
                    ? 1.5 + rnd.nextDouble() * 0.5
                    : 1.0;
                int price = (int) (basePrice * mult);

                batch.add(new Object[] {
                    roomId,
                    Date.valueOf(date),
                    price,
                    maxQty,
                    0
                });
                totalRows++;

                if (batch.size() >= batchSize) {
                    jdbc.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);
        return totalRows;
    }

    private int seedReviews(Random rnd, long hotelStartId) {
        String sql = """
            INSERT INTO reviews (hotel_id, user_id, rating, content, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;

        int popularThreshold = (int) (hotelCount * 0.1);
        int popularReviews = reviewsPerHotel * 6;
        int normalReviews = reviewsPerHotel / 2;
        int totalRows = 0;
        List<Object[]> batch = new ArrayList<>(batchSize);
        LocalDateTime now = LocalDateTime.now();

        for (long hotelOffset = 0; hotelOffset < hotelCount; hotelOffset++) {
            long hotelId = hotelStartId + hotelOffset;
            boolean popular = hotelOffset < popularThreshold;
            int count = popular ? popularReviews : normalReviews;

            for (int r = 0; r < count; r++) {
                int rating = popular
                    ? 4 + rnd.nextInt(2)
                    : 2 + rnd.nextInt(4);

                batch.add(new Object[] {
                    hotelId,
                    (long) (rnd.nextInt(100000) + 1),
                    rating,
                    "리뷰 내용 #" + r,
                    Timestamp.valueOf(now.minusDays(rnd.nextInt(365)))
                });
                totalRows++;

                if (batch.size() >= batchSize) {
                    jdbc.batchUpdate(sql, batch);
                    batch.clear();
                }
            }
        }
        if (!batch.isEmpty()) jdbc.batchUpdate(sql, batch);
        return totalRows;
    }

    private RegionInfo pickRegion(Random rnd) {
        int totalWeight = REGIONS.stream().mapToInt(r -> r.weight).sum();
        int pick = rnd.nextInt(totalWeight) + 1;
        int cumulative = 0;
        for (RegionInfo r : REGIONS) {
            cumulative += r.weight;
            if (pick <= cumulative) return r;
        }
        return REGIONS.getLast();
    }

    private record RegionInfo(String region, int weight, List<String> cities) {}
}
