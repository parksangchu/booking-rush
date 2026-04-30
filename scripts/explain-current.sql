-- 현재 쿼리 (서브쿼리 inline view) — region 필터 푸시다운 안 됨
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
