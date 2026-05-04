# Step 1: Baseline (DB만, 캐시 없음) — 검색 쿼리 튜닝

| 항목 | 내용 |
|------|------|
| 상태 | resolved |
| 최종 수정 | 2026-04-30 |

## 배경

Phase 2 캐싱 시나리오의 출발점. **"왜 캐싱이 필요한가"의 동기를 실측으로 확보**하기 위해, 우선 캐시 없이 DB 단일 쿼리로 숙소 검색을 구현하고 응답 시간을 측정한다.

핵심 질문 두 가지:
1. 캐싱 도입 전, **순수 SQL 튜닝(쿼리 재작성 + 인덱스)으로 어디까지 풀 수 있는가**?
2. 그래도 남는 비용은 무엇이고, 캐싱이 그것을 어떻게 해결할 수 있는가?

## 환경

- MySQL 8.4 (Docker), 단일 노드, 로컬
- 데이터: 호텔 10,000개 / 객실 50,000개(호텔당 5) / 인벤토리 1,825만 행(객실 × 365일) / 리뷰 30만
- 시드 분포: 제주 30% / 강릉·부산 15% / 서울 10% / 기타 30%
- 테스트 쿼리: `region=제주, check_in=2026-05-01, check_out=2026-05-03, guests=2, sort=popular`

핵심 숫자:

| 숫자 | 의미 |
|---|---|
| 50,000 | 전체 객실 |
| 14,610 | 제주 객실 (region 필터 통과 후) |
| 100,000 | 5/1~5/2 범위 **전체** 객실 인벤토리 (50,000 × 2일) |
| 29,220 | 5/1~5/2 범위 **제주** 객실 인벤토리 (14,610 × 2일) |

## 타임라인

### 2026-04-30: 초기 쿼리 — 14초

**SQL 패턴**:

```sql
SELECT h.id, ..., MIN(rp.avg_price)
FROM hotels h
JOIN rooms r ON r.hotel_id = h.id
JOIN (                                           -- inline view
    SELECT ri.room_id, AVG(ri.price) AS avg_price
    FROM room_inventories ri
    WHERE ri.date >= '2026-05-01' AND ri.date < '2026-05-03'
    GROUP BY ri.room_id
) rp ON rp.room_id = r.id
WHERE h.region = '제주'
```

**EXPLAIN ANALYZE 핵심 (5,288ms)**:

```
-> Materialize  (5,247ms, rows=50,000)        ← 99% 시간 여기
   -> Aggregate (GROUP BY ri.room_id)
      -> Index range scan on idx_inventory_date
         rows=100,000 (5,118ms)                ← 5/1~5/2 전체 인벤토리
```

**원인**: inline view가 먼저 materialize 되면서 **region 필터를 푸시다운하지 못함**.
- `idx_inventory_date`는 `(date)` 단일 인덱스 → 5/1~5/2 범위에 들어오는 모든 행(100,000 = 50,000 객실 × 2일)을 다 가져옴
- GROUP BY로 50,000 객실 평균을 만든 뒤, 다음 단계에서 region 필터로 14,610개만 살아남음
- **나머지 35,390 객실 평균은 계산 후 버려짐 (낭비 71%)**

핵심: 인벤토리 인덱스에 region이 없으니 옵티마이저가 region 필터를 inventory 스캔 전에 적용할 방법이 없다. 쿼리 구조를 바꿔야 한다.

---

### 2026-04-30: LATERAL JOIN으로 재작성 — 2,401ms (-54%)

**SQL 재작성**:

```sql
JOIN LATERAL (
    SELECT AVG(ri.price) AS avg_price, COUNT(*) AS cnt
    FROM room_inventories ri
    WHERE ri.room_id = r.id              -- ← 외부 r.id를 안쪽에서 참조
      AND ri.date >= ? AND ri.date < ?
      AND ri.used_quantity < ri.max_quantity
) room_avg ON room_avg.cnt = DATEDIFF(?, ?)
```

**LATERAL이 외부에서 참조하는 컬럼은 `r.id` 하나뿐**. h는 외부 join chain의 region 필터에서만 작동하고, LATERAL 안쪽엔 들어가지 않는다. 결과적으로 region='제주' 통과한 14,610개 r에 대해서만 LATERAL이 실행된다.

**`DATEDIFF` 비교의 의미**: WHERE의 `used < max` 조건 때문에 매진된 날짜는 row 자체가 안 나온다. cnt < 전체 일수면 한 날 이상 매진 → ON 조건에서 탈락. 즉 "연박 전 기간 가용" 객실만 통과.

**EXPLAIN ANALYZE 핵심 (2,401ms)**:

```
-> Nested loop inner join  (rows=14,610)
   -> Index lookup on h (region='제주')  rows=2,922
   -> Covering index lookup on r  rows=14,610 누적
   -> Materialize (per row from r)  loops=14,610
      -> Index lookup on ri using uk_room_date (room_id=r.id),
         with index condition: (ri.date BETWEEN ...)     ← ICP 작동
         rows=2 loops=14,610
```

**ICP (Index Condition Pushdown)**: MySQL 5.6+ 최적화. 인덱스에 포함된 컬럼의 WHERE 조건을 인덱스 leaf 단계에서 직접 평가해 PK fetch를 줄임. 여기선 `uk_room_date(room_id, date)` 인덱스에서 room_id로 진입 후, date 조건도 같이 인덱스에서 평가 → 룸당 정확히 2행만 통과.

**핵심 변화**:

| 항목 | Before (inline view) | After (LATERAL) |
|---|---|---|
| 사용 인덱스 | `idx_inventory_date` (date) | `uk_room_date` (room_id, date) |
| 진입 키 | date 범위 → 전 region 객실 | r.id → 제주 객실만 |
| 인덱스에서 읽는 행 | 100,000 | 29,220 (룸당 2 × 14,610) |
| ICP | 없음 | ✅ |

여전히 한 가지 비용: `uk_room_date`는 (room_id, date)만 담음. used/max/price는 PK B+Tree로 점프해야 가져옴 → **14,610번의 random PK fetch**.

---

### 2026-04-30: 커버링 인덱스 추가 — 1,672ms (-68%)

```sql
ALTER TABLE room_inventories
    ADD INDEX idx_inv_room_date_cover
        (room_id, date, used_quantity, max_quantity, price);
```

**비용**: 1,825만 row × 약 28 byte ≈ 500MB. 검색이 가격/재고 변경보다 훨씬 잦으므로 trade-off 수용.

**EXPLAIN ANALYZE 핵심 (1,672ms)**:

```
-> Covering index lookup on ri using idx_inv_room_date_cover (room_id=r.id)
   rows=365 loops=14,610                       ← 룸당 1년치 다 읽음 (ICP 미작동)
-> Filter: (date BETWEEN ... and used < max)
   rows=2 loops=14,610
```

**의외의 결과**: 새 인덱스에선 ICP가 사라지고 룸당 365행을 다 읽는다. 그런데 더 빠르다.

---

### 2026-04-30: 의외의 발견 — 커버링 vs ICP 비교

커버링 인덱스를 invisible 처리해 `uk_room_date`를 강제로 쓰게 한 뒤 비교:

| 인덱스 | ICP | 인덱스 행/룸 | PK fetch | 시간 |
|---|---|---|---|---|
| `uk_room_date` | ✅ | 2 | **14,610번** | 4,750ms |
| `idx_inv_room_date_cover` | ❌ | 365 | **0번** | 1,672ms |

커버링 쪽이 인덱스 행을 180배 더 읽는데도 3배 빠르다. 이유는 IO 패턴 차이:

- `uk_room_date` 케이스: 인덱스 leaf 2행 (시퀀셜) **+ 14,610번의 PK 점프 (random)**. PK B+Tree 안에서 임의 위치로 점프하면 매번 다른 leaf 블록 액세스.
- 커버링 케이스: 룸당 365행은 같은 leaf 블록(보통 16KB)에 묶여 있어 시퀀셜 read. PK 점프 0회.

> **핵심 깨달음**: 인덱스 선택은 단순히 "행 수"가 아니라 **IO 패턴(시퀀셜 vs 랜덤)**을 본다. EXPLAIN 행 수만 보고 ICP 쪽을 골랐으면 잘못된 결정이었을 것.

**면접 카드**: *"커버링 인덱스가 인덱스 행을 180배 더 읽는데도 PK random IO 14,610번을 제거해서 3배 빨랐다. invisible 인덱스로 두 케이스를 직접 측정 (1,672ms vs 4,750ms)."*

---
 
## 현재 결론

| 단계 | EXPLAIN 시간 | 누적 개선 |
|---|---|---|
| Before (inline view materialize) | 5,288ms | — |
| LATERAL JOIN | 2,401ms | -54% |
| + 커버링 인덱스 | 1,672ms | -68% |

* 위는 EXPLAIN ANALYZE 시간. 실제 HTTP 응답은 count 쿼리 한 번 더 → 2.9~3.3초.

**튜닝으로 풀린 것**: region 필터 푸시다운 (LATERAL), PK random fetch 제거 (커버링).

**튜닝으로 풀리지 않는 것 (캐싱 동기)**:
- 매 요청마다 LATERAL 14,610번 + 인덱스 행 533만 행 스캔
- count 쿼리 중복 비용 (페이지네이션 본질)
- 동시 요청 시 DB CPU·IO 선형 증가
- "제주 / 5/1~5/3 / 2명" 같은 인기 조합은 **매번 같은 결과**인데 매번 풀스택 계산

→ **Cache-Aside (Step 2) 진입**. Hit 시 1ms 미만 예상, miss는 1.7초 + Redis 왕복. 키 cardinality 폭발의 한계는 Step 3(Read Model 분리)에서 다룬다.

## 참고 파일

- 검색 쿼리: `src/main/java/com/couponrush/domain/accommodation/repository/HotelSearchRepositoryImpl.java`
- 인덱스 정의: `src/main/java/com/couponrush/domain/accommodation/entity/RoomInventory.java`
- EXPLAIN 스크립트: `scripts/explain-current.sql`, `scripts/explain-rewrite-v2.sql`
- 시드: `src/main/java/com/couponrush/domain/accommodation/seed/AccommodationSeeder.java`
