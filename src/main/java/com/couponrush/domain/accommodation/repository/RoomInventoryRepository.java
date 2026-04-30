package com.couponrush.domain.accommodation.repository;

import com.couponrush.domain.accommodation.entity.RoomInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface RoomInventoryRepository extends JpaRepository<RoomInventory, Long> {

    /**
     * CAS 기반 낙관적 락 — 단일 날짜 재고 차감.
     * affected_rows == 1 이면 성공, 0이면 매진/충돌.
     */
    @Modifying
    @Query(value = """
            UPDATE room_inventories
            SET used_quantity = used_quantity + 1
            WHERE id = :id AND used_quantity < max_quantity
            """, nativeQuery = true)
    int decrementAvailability(@Param("id") Long id);

    /**
     * CAS 기반 낙관적 락 — 다중 날짜(연박) 재고 차감.
     * affected_rows == nights 이면 모든 날짜 성공, 미달이면 부분 매진 → ROLLBACK 필요.
     */
    @Modifying
    @Query(value = """
            UPDATE room_inventories
            SET used_quantity = used_quantity + 1
            WHERE room_id = :roomId
              AND date >= :checkIn
              AND date < :checkOut
              AND used_quantity < max_quantity
            """, nativeQuery = true)
    int decrementAvailabilityRange(@Param("roomId") Long roomId,
                                   @Param("checkIn") LocalDate checkIn,
                                   @Param("checkOut") LocalDate checkOut);
}
