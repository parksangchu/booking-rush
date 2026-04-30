-- 커버링 인덱스 추가: PK fetch 제거 목적
-- (room_id, date) 진입 후 used_quantity/max_quantity/price를 인덱스에서 직접 읽음
-- 추가 비용: 약 500MB (1,825만 row × 약 28 byte)
ALTER TABLE room_inventories
    ADD INDEX idx_inv_room_date_cover (room_id, date, used_quantity, max_quantity, price);
