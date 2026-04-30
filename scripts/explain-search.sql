-- 검색 쿼리 EXPLAIN ANALYZE
-- 실행: docker exec coupon-rush-mysql mysql --default-character-set=utf8mb4 -ucoupon -pcoupon coupon_rush < scripts/explain-search.sql

-- 1. 데이터 행 수 확인
SELECT 'hotels' AS tbl, COUNT(*) AS cnt FROM hotels
UNION ALL SELECT 'rooms', COUNT(*) FROM rooms
UNION ALL SELECT 'room_inventories', COUNT(*) FROM room_inventories
UNION ALL SELECT 'reviews', COUNT(*) FROM reviews;

-- 2. 인덱스 확인
SHOW INDEX FROM hotels;
SHOW INDEX FROM rooms;
SHOW INDEX FROM room_inventories;

-- 3. 검색 쿼리 EXPLAIN ANALYZE (제주, 5/1~5/3, 2명, popular)
EXPLAIN ANALYZE
SELECT h.id, h.name, h.region, h.city, h.thumbnail_url,
       h.rating_avg, h.rating_count,
       MIN(rp.avg_price) AS from_price
FROM hotels h
JOIN rooms r ON r.hotel_id = h.id AND r.max_coverage >= 2
JOIN (
    SELECT ri.room_id, AVG(ri.price) AS avg_price
    FROM room_inventories ri
    WHERE ri.date >= '2026-05-01' AND ri.date < '2026-05-03'
      AND ri.used_quantity < ri.max_quantity
    GROUP BY ri.room_id
    HAVING COUNT(*) = DATEDIFF('2026-05-03', '2026-05-01')
) rp ON rp.room_id = r.id
WHERE h.region = '제주' AND h.rating_avg >= 0
GROUP BY h.id
HAVING MIN(rp.avg_price) BETWEEN 0 AND 999999999
ORDER BY h.rating_count DESC
LIMIT 20 OFFSET 0;
