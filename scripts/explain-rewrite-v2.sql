-- 재작성 v2: LATERAL JOIN — 호텔/객실 단위로 인벤토리 조회
-- 각 (h, r) 쌍에 대해 그 객실의 인벤토리만 보고 평균/카운트 계산
EXPLAIN ANALYZE
SELECT h.id, h.name, h.region, h.city, h.thumbnail_url,
       h.rating_avg, h.rating_count,
       MIN(room_avg.avg_price) AS from_price
FROM hotels h
JOIN rooms r ON r.hotel_id = h.id AND r.max_coverage >= 2
JOIN LATERAL (
    SELECT AVG(ri.price) AS avg_price, COUNT(*) AS cnt
    FROM room_inventories ri
    WHERE ri.room_id = r.id
      AND ri.date >= '2026-05-01' AND ri.date < '2026-05-03'
      AND ri.used_quantity < ri.max_quantity
) room_avg ON room_avg.cnt = DATEDIFF('2026-05-03', '2026-05-01')
WHERE h.region = '제주' AND h.rating_avg >= 0
GROUP BY h.id
HAVING MIN(room_avg.avg_price) BETWEEN 0 AND 999999999
ORDER BY h.rating_count DESC
LIMIT 20 OFFSET 0;
