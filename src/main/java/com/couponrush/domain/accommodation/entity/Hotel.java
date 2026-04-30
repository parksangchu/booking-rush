package com.couponrush.domain.accommodation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hotels", indexes = {
    @Index(name = "idx_hotel_region_rating_count", columnList = "region, rating_count"),
    @Index(name = "idx_hotel_region_rating_avg", columnList = "region, rating_avg")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "rating_avg", nullable = false)
    private Double ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Hotel(String name, String region, String city,
                 Double ratingAvg, Integer ratingCount,
                 String thumbnailUrl) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("호텔 이름은 필수입니다");
        }
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("지역은 필수입니다");
        }
        this.name = name;
        this.region = region;
        this.city = city;
        this.ratingAvg = ratingAvg != null ? ratingAvg : 0.0;
        this.ratingCount = ratingCount != null ? ratingCount : 0;
        this.thumbnailUrl = thumbnailUrl;
        this.createdAt = LocalDateTime.now();
    }

    public void applyRating(double newAvg, int newCount) {
        this.ratingAvg = newAvg;
        this.ratingCount = newCount;
    }
}
