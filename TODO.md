# Phase 1 로드맵

## 인프라 구성 (구현 순서 1번)
- [x] Docker Compose 확장 (Redis, Kafka, Prometheus, Grafana)
- [x] build.gradle 의존성 추가 (MySQL, JPA, Actuator — Redis/Kafka는 Step별 추가)
- [x] application.yml 확장 (Actuator, Prometheus)
- [x] 테스트 설정 전환 (H2 → Testcontainers MySQL)
- [x] k6 시나리오 골격
- [x] Terraform 기본 골격 (Step 1용)
- [x] k6 EC2 → test 서버 전환 (k6 + Prometheus + Grafana 통합, t3.small)
- [x] 검증: docker compose up, gradlew build, actuator 확인

## 기본 도메인 구현 (구현 순서 2번)
- [x] Coupon 엔티티 보강 (issue(), remainingQuantity(), 생성자 검증)
- [x] Issuance 엔티티 (FK 미사용, 유니크 제약)
- [x] IssuanceStrategy 인터페이스
- [x] 발급 API (POST /api/v1/coupons/{couponId}/issue)
- [x] 상태 API (GET /api/v1/coupons/{couponId}/status)
- [x] 전략 선택 설정 (coupon.strategy 프로퍼티, @ConditionalOnProperty)
- [x] 패키지 재구성 (controller, dto, entity, exception, repository, service, strategy)

## Step 1: Pessimistic Lock
- [x] PessimisticLockStrategy 구현 (FOR UPDATE + dirty checking)
- [x] 통합 테스트 (Testcontainers, 동시성/중복/소진 검증)
- [x] k6 부하 테스트 (로컬) — 정합성 OK (100/100), p(99)=195ms, max VU 87
- [x] AWS 배포 + k6 부하 테스트
- [x] 결과 기록 + 대안 검토
- [x] 인프라 변경: t3(버스터블) → m6i/m6g(비버스터블) — CPU 크레딧 문제 해소
- [x] 서브 퀘스트: SingleUpdateStrategy 구현 + 테스트 공통화
- [x] 서브 퀘스트: Single UPDATE vs Pessimistic Lock AWS 부하 테스트 (1,000 RPS)
- [x] 서브 퀘스트: 결과 기록

## Step 2: DB + Redis
### Redis Distributed Lock
- [x] RedisLockStrategy 구현 (Redisson + TransactionTemplate)
- [x] Terraform: ElastiCache 추가
- [x] 통합 테스트
- [x] k6 부하 테스트 (AWS) — 500/1,000 RPS 모두 붕괴, DB 락보다 악화
- [x] 결과 기록 + 대안 검토

### Redis Atomic Counter
- [x] RedisCounterStrategy 구현 (RedisTemplate + INCR)
- [x] 통합 테스트
- [x] k6 부하 테스트 (AWS) — 1,000 RPS 최고 성능, 2,000 RPS에서 DB INSERT 병목
- [x] 결과 기록 + 대안 검토

## Step 3: Redis + Queue + DB
### Kafka (메인)
- [x] KafkaStrategy 구현 (Redis Lua SADD+INCR + Kafka produce + 실패 시 DECR/SREM 보상)
- [x] Kafka Consumer 구현 (DB INSERT + 멱등성 처리)
- [x] RedisCounterStrategy 중복 체크를 Redis SET으로 변경 (공정 비교)
- [x] Terraform: EC2 Kafka 추가 (MSK 대신 직접 설치, ~$16/월)
- [x] 통합 테스트 (16개 전체 통과)
- [x] k6 부하 테스트 (AWS) — 1,000/2,000/3,000 RPS, Kafka vs Redis Counter 비교
- [x] 결과 기록 — latency 15~30% 개선, 처리량 최대 +38%, CPU 병목은 미해소

### Redis Streams (대안 검토)
- [x] RedisStreamsStrategy 구현 (Lua: SADD+INCR+XADD 원자화)
- [x] Redis Streams Consumer 구현 (StreamMessageListenerContainer)
- [x] k6 부하 테스트 (AWS) — 1K~5K RPS, 4,000 RPS에서 한계
- [x] Kafka vs Redis Streams 트레이드오프 기록 — Streams는 3K까지 압도적(p95 1ms), Kafka는 7K까지 안정

## 마무리
- [x] docs/strategy-comparison.md 작성
- [x] README.md 작성

---

# Phase 2 로드맵 (숙소 검색 — 읽기 최적화 / 캐싱)

## Step 1: Baseline (DB만, 캐시 없음)

### 도메인/구현
- [x] 도메인 모델 설계 (Hotel/Room/RoomInventory/Review/Reservation)
- [x] 동시성 전략 결정 (CAS 기반 낙관적 락 — `UPDATE WHERE used < max`)
- [x] 검색 API 시그니처 (`GET /api/v1/search/hotels`)
- [x] 엔티티 5개 작성 (인덱스 어노테이션 포함)
- [x] Repository 5개 작성 (RoomInventory에 CAS decrement 메서드)
- [x] AccommodationSeeder (`@Profile("seed")`, JDBC Batch Insert, 의도적 분포)
- [x] SearchController + SearchService(NamedParameterJdbcTemplate) + DTO
- [x] 통합 테스트 9개 (정상/필터/정렬/매진/빈결과/잘못된입력)
- [x] k6 search-baseline.js (정상/Hot Key 모드)
- [x] Grafana 대시보드 (accommodation.json)

### 측정
- [x] 본 규모 시드 실행 (호텔 1만 / 1,800만 row) — `rewriteBatchedStatements=true`로 9.5분
- [x] EXPLAIN으로 인덱스 효과 검증 → LATERAL JOIN + 커버링 인덱스 적용 (5.3초 → 1.7초)
- [ ] k6 부하 테스트 (MODE=normal) — 보류, Step 2 캐싱 비교 시 함께 측정
- [ ] k6 부하 테스트 (MODE=hotkey) — 보류
- [x] Step 2 진입 동기 도출 — 쿼리 튜닝 한계 1.7초 (count 포함 ~3초), 캐싱 필요
- [x] `docs/accommodation/step1-baseline.md` 작성

## Step 2 이후 (Step 1 측정 결과 후 별도 plan)
- [ ] Step 2: 응답 통째로 Cache-Aside
- [ ] Step 3: Read Model 분리 (다단 캐싱)
- [ ] Step 4: Hot Key 분산
- [ ] Step 5: 이벤트 기반 무효화
- [ ] Step 6: Stampede / Penetration 방어
