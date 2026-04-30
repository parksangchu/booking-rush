package com.couponrush.domain.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservations", indexes = {
    @Index(name = "idx_reservation_user", columnList = "user_id"),
    @Index(name = "idx_reservation_room", columnList = "room_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "total_price", nullable = false)
    private Integer totalPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Reservation(Long userId, Long roomId, LocalDate checkIn, LocalDate checkOut, Integer totalPrice) {
        if (userId == null) {
            throw new IllegalArgumentException("사용자는 필수입니다");
        }
        if (roomId == null) {
            throw new IllegalArgumentException("객실은 필수입니다");
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("체크아웃은 체크인 이후여야 합니다");
        }
        if (totalPrice == null || totalPrice < 0) {
            throw new IllegalArgumentException("총 가격은 0 이상이어야 합니다");
        }
        this.userId = userId;
        this.roomId = roomId;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.totalPrice = totalPrice;
        this.createdAt = LocalDateTime.now();
    }
}
