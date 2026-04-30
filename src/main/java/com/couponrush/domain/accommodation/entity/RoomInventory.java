package com.couponrush.domain.accommodation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "room_inventories",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_room_date", columnNames = {"room_id", "date"})
    },
    indexes = {
        @Index(name = "idx_inventory_date", columnList = "date"),
        @Index(name = "idx_inv_room_date_cover",
            columnList = "room_id, date, used_quantity, max_quantity, price")
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Room room;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer price;

    @Column(name = "max_quantity", nullable = false)
    private Integer maxQuantity;

    @Column(name = "used_quantity", nullable = false)
    private Integer usedQuantity;

    public RoomInventory(Room room, LocalDate date, Integer price, Integer maxQuantity) {
        if (room == null) {
            throw new IllegalArgumentException("객실은 필수입니다");
        }
        if (date == null) {
            throw new IllegalArgumentException("날짜는 필수입니다");
        }
        if (price == null || price < 0) {
            throw new IllegalArgumentException("가격은 0 이상이어야 합니다");
        }
        if (maxQuantity == null || maxQuantity < 1) {
            throw new IllegalArgumentException("최대 수량은 1 이상이어야 합니다");
        }
        this.room = room;
        this.date = date;
        this.price = price;
        this.maxQuantity = maxQuantity;
        this.usedQuantity = 0;
    }

    public boolean isAvailable() {
        return usedQuantity < maxQuantity;
    }
}
