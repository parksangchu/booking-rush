package com.couponrush.domain.review.entity;

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
@Table(name = "reviews", indexes = {
    @Index(name = "idx_review_hotel", columnList = "hotel_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Review(Long hotelId, Long userId, Integer rating, String content) {
        if (hotelId == null) {
            throw new IllegalArgumentException("호텔은 필수입니다");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자는 필수입니다");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1~5 사이여야 합니다");
        }
        this.hotelId = hotelId;
        this.userId = userId;
        this.rating = rating;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }
}
