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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rooms", indexes = {
    @Index(name = "idx_room_hotel_coverage", columnList = "hotel_id, max_coverage")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hotel_id", nullable = false,
        foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Hotel hotel;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "max_coverage", nullable = false)
    private Integer maxCoverage;

    @Column(name = "bed_config", length = 100)
    private String bedConfig;

    @Column
    private Integer area;

    public Room(Hotel hotel, String name, Integer maxCoverage, String bedConfig, Integer area) {
        if (hotel == null) {
            throw new IllegalArgumentException("호텔은 필수입니다");
        }
        if (maxCoverage == null || maxCoverage < 1) {
            throw new IllegalArgumentException("수용 인원은 1 이상이어야 합니다");
        }
        this.hotel = hotel;
        this.name = name;
        this.maxCoverage = maxCoverage;
        this.bedConfig = bedConfig;
        this.area = area;
    }
}
