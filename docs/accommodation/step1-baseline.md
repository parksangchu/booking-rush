# Step 1: Baseline (DB만, 캐시 없음) — 검색 쿼리 튜닝

| 항목 | 내용 |
|------|------|
| 상태 | resolved |
| 최종 수정 | 2026-04-30 |

## 배경

Phase 2 캐싱 시나리오의 출발점. **"왜 캐싱이 필요한가"의 동기를 실측으로 확보**하기 위해, 우선 캐시 없이 DB 단일 쿼리로 숙소 검색을 구현하고 응답 시간을 측정한다.

이 단계의 핵심 질문:
1. 캐싱 도입 전, **순수 SQL 튜닝(쿼리 재작성 + 인덱스)으로 어디까지 풀 수 있는가**?
2. 그래도 남는 비용은 무엇이고, 캐싱이 그것을 어떻게 해결할 수 있는가?

## 환경

- MySQL 8.4 (Docker), 단일 노드, 로컬
- 데이터: 호텔 10,000개, 객실 50,000개, 인벤토리 1,825만 행 (1년치), 리뷰 약 30만
- 시드 분포: 제주 30% / 강릉·부산 15% / 서울 10% / 기타 30%, 인기 호텔(상위 10%)에 리뷰 6배 집중
- 테스트 쿼리: `region=제주, check_in=2026-05-01, check_out=2026-05-03, guests=2, sort=popular`
  - 제주 호텔 ~3,000개, 객실 ~14,610개에 매칭됨

## 타임라인

### 2026-04-30: 초기 쿼리의 EXPLAIN

**문제**: 시드 후 첫 검색 호출 시 **응답 시간 약 14초**.

**EXPLAIN ANALYZE 결과 (5,288ms)**:

```
-> Sort: h.rating_count DESC  (5288ms)
    -> Aggregate using temporary table  (5286ms)
        -> Nested loop inner join  (5275ms)
            -> Index lookup on hotels (region='제주')  rows=2,922 (11ms)
            -> Covering index lookup on rooms (hotel_id)  rows=14,610 누적
            -> Index lookup on rp using <auto_key0> (room_id)  rows=14,610
                -> Materialize  (5247ms)  ← 99% 시간 여기
                    -> Aggregate (GROUP BY ri.room_id)
                        -> Filter (used < max)
                            -> Index range scan on idx_inventory_date
                               rows=100,000  (5,135ms)
```

**원인**: 인벤토리 서브쿼리가 inline view로 먼저 materialize되면서 **region 필터를 푸시다운하지 못함**. 50,000 객실 × 2일 = 10만 행을 전수 스캔하는데, 실제 필요한 건 제주 객실 14,610개에 대한 가격뿐 (약 71%가 낭비).

```sql
-- 문제 패턴
JOIN (
    SELECT ri.room_id, AVG(ri.price)
    FROM room_inventories ri              -- region 모름. 전수 스캔.
    WHERE ri.date >= ? AND ri.date < ?
    GROUP BY ri.room_id
) rp ON rp.room_id = r.id
```

### 2026-04-30: LATERAL JOIN으로 재작성

**검토한 선택지**:
- A. `STRAIGHT_JOIN` 힌트로 hotel→room→inventory 순서만 강제 → 옵티마이저가 inline view materialize를 여전히 먼저 할 가능성
- B. `JOIN LATERAL`로 (hotel, room) 단위에서 인벤토리를 상관 조회 → region 필터가 자연스럽게 푸시다운됨
- C. 두 단계 쿼리 (호텔/객실 → 인벤토리)로 애플리케이션 레벨 분리

**결정과 이유**: B 선택. SQL 한 번으로 표현 가능하고, MySQL 8.0.14+에서 LATERAL 지원. 학습 측면에서 옵티마이저의 행 처리 단위가 (room_inventories 전체) → (각 객실별)로 바뀌는 변화를 EXPLAIN으로 명확히 비교할 수 있다.

```sql
-- 재작성 (LATERAL)
JOIN LATERAL (
    SELECT AVG(ri.price) AS avg_price, COUNT(*) AS cnt
    FROM room_inventories ri
    WHERE ri.room_id = r.id                -- ← 각 (h, r) 쌍의 객실만
      AND ri.date >= ? AND ri.date < ?
      AND ri.used_quantity < ri.max_quantity
) room_avg ON room_avg.cnt = DATEDIFF(?, ?)
```

**결과 (2,401ms — 54% 감소)**:

```
-> Index lookup on ri using uk_room_date (room_id=r.id),
   with index condition: (ri.date BETWEEN ...)
   rows=2 loops=14,610   ← ICP 작동, 룸당 정확히 2행
```

기존 `uk_room_date(room_id, date)` UNIQUE 인덱스를 LATERAL이 활용했고, 인덱스 컨디션 푸시다운(ICP)으로 룸당 2행만 읽음.

### 2026-04-30: 커버링 인덱스 추가

**문제**: 여전히 LATERAL 안에서 인덱스 조회 후 PK lookup으로 used_quantity/max_quantity/price를 fetch해야 함. 14,610번의 무작위 PK 액세스 비용.

**검토**: `room_id, date` 프리픽스에 used_quantity, max_quantity, price를 모두 담은 커버링 인덱스 추가.

```sql
ALTER TABLE room_inventories
    ADD INDEX idx_inv_room_date_cover
        (room_id, date, used_quantity, max_quantity, price);
```

**비용 추정**: 1,825만 row × 약 28 byte ≈ 500MB 디스크 + 쓰기 시 인덱스 갱신 비용. 호텔 가격/재고 변경은 검색 대비 매우 드물어 trade-off 수용.

**결과 (1,672ms — 추가 22% 감소)**:

```
-> Covering index lookup on ri using idx_inv_room_date_cover (room_id=r.id)
   rows=365 loops=14,610
-> Filter: (ri.date >= ... and ri.used < ri.max)
   rows=2 loops=14,610
```

### 2026-04-30: 의외의 발견 — 커버링 vs ICP 비교

**관찰**: 새 커버링 인덱스에서는 옵티마이저가 ICP를 적용하지 않고 **룸당 365행(1년치 전체)을 다 읽음**. 이전 `uk_room_date`보다 더 많은 행을 읽는데 어떻게 더 빠른가?

**커버링 인덱스를 invisible 처리하고 비교**:

| 인덱스 선택 | ICP | 인덱스 행/룸 | PK fetch | 시간 |
|---|---|---|---|---|
| `uk_room_date` (UNIQUE) | ✅ | 2 | **14,610번** | **4,750ms** |
| `idx_inv_room_date_cover` (커버링) | ❌ | 365 | **0번** | **1,672ms** |

**결론**: 커버링 인덱스가 ICP보다 더 효과적이었다. **PK lookup의 무작위 디스크 I/O 비용이 시퀀셜 인덱스 스캔(룸당 365행)보다 비쌌다.** 인덱스 행 수만 보면 직관적으로 ICP가 빨라야 할 것 같지만, B+Tree에서 같은 leaf 블록 내부의 시퀀셜 read는 매우 저렴하고, 반대로 PK lookup은 매번 다른 leaf 블록으로 점프하는 random access다.

이 비교는 통상의 "ICP가 좋다 / 인덱스 행이 적을수록 좋다"는 단순 휴리스틱을 깬다. 면접 답변 카드: *"인덱스 선택은 행 수가 아니라 IO 패턴(시퀀셜 vs 랜덤)을 봐야 한다."*

## 현재 결론

### 정량 정리

| 단계 | 응답 시간 | 누적 개선 |
|---|---|---|
| Before (서브쿼리 materialize) | 5,288ms | — |
| LATERAL JOIN 재작성 | 2,401ms | -54% |
| + 커버링 인덱스 추가 | 1,672ms | -68% |

* 위 수치는 EXPLAIN ANALYZE의 실측 시간 (캐시 워밍 후). 실제 HTTP API 응답은 count 쿼리가 같은 SQL을 한 번 더 실행하므로 약 2.9~3.3초.

### 정성 결론

**튜닝으로 풀린 것**:
- 서브쿼리 materialize의 region 푸시다운 문제 → LATERAL로 자연 해결
- PK random lookup 비용 → 커버링 인덱스로 제거

**튜닝으로 풀리지 않는 것 (캐싱이 필요한 이유)**:
- **여전히 14,610개 객실 × 2일 = ~30,000행을 매 요청마다 집계**해야 함
- count 쿼리가 동일 비용으로 한 번 더 실행 (페이지네이션 본질)
- 동시 요청 시 DB CPU·IO가 선형 증가 → 트래픽 늘면 1.7초가 더 길어짐
- "제주 / 5/1~5/3 / 2명" 같은 인기 검색 조합은 매번 같은 결과를 만드는데도 매번 DB가 풀스택 계산

### 다음 단계 (Step 2 진입 동기)

응답 결과는 사실상 변하지 않는데(인벤토리/가격 변경 빈도 낮음) 매 요청마다 **수만 행을 집계**한다. **Cache-Aside로 (region, date_range, guests, sort, page) 키 단위 캐싱**이 자연스러운 다음 단계.

Step 2에서 측정할 것:
- Hit 시 응답 시간 (예상: 1ms 미만)
- Miss → fill 시 응답 시간 (현재 1.7초 + 직렬화·Redis 왕복)
- TTL/무효화 정책 부재 시 stale 데이터 위험

## 참고 파일

- 검색 쿼리 구현: `src/main/java/com/couponrush/domain/accommodation/repository/HotelSearchRepositoryImpl.java`
- 엔티티 인덱스 정의: `src/main/java/com/couponrush/domain/accommodation/entity/RoomInventory.java`
- EXPLAIN 스크립트: `scripts/explain-current.sql`, `scripts/explain-rewrite-v2.sql`
- 시드: `src/main/java/com/couponrush/domain/accommodation/seed/AccommodationSeeder.java`
