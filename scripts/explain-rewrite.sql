-- 재작성 v1: STRAIGHT_JOIN + 서브쿼리 풀어쓰기
-- hotel(region 필터) → room(hotel_id 진입) → inventory(uk_room_date 진입) 순서 강제
-- 외곽 GROUP BY를 hotel/room 단위로 두고, 객실별 평균/COUNT 검사를 GROUP BY로 처리
EXPLAIN ANALYZE
SELECT h.id, h.name, h.region, h.city, h.thumbnail_url,
       h.rating_avg, h.rating_count,
       MIN(room_avg.avg_price) AS from_price
FROM hotels h STRAIGHT_JOIN rooms r ON r.hotel_id = h.id AND r.max_coverage >= 2
STRAIGHT_JOIN (
    SELECT ri.room_id, AVG(ri.price) AS avg_price, COUNT(*) AS cnt
    FROM room_inventories ri
    WHERE ri.date >= '2026-05-01' AND ri.date < '2026-05-03'
      AND ri.used_quantity < ri.max_quantity
    GROUP BY ri.room_id
) room_avg ON room_avg.room_id = r.id
   AND room_avg.cnt = DATEDIFF('2026-05-03', '2026-05-01')
WHERE h.region = '제주' AND h.rating_avg >= 0
GROUP BY h.id
HAVING MIN(room_avg.avg_price) BETWEEN 0 AND 999999999
ORDER BY h.rating_count DESC
LIMIT 20 OFFSET 0;
