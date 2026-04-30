package com.couponrush.domain.review;

import com.couponrush.domain.review.entity.Review;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Review 엔티티 단위 테스트")
class ReviewTest {

    private static final Long HOTEL_ID = 1L;
    private static final Long USER_ID = 100L;

    @Test
    @DisplayName("정상 생성")
    void create() {
        Review r = new Review(HOTEL_ID, USER_ID, 5, "좋아요");

        assertThat(r.getHotelId()).isEqualTo(HOTEL_ID);
        assertThat(r.getUserId()).isEqualTo(USER_ID);
        assertThat(r.getRating()).isEqualTo(5);
        assertThat(r.getContent()).isEqualTo("좋아요");
        assertThat(r.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("hotelId가 null이면 예외")
    void hotelIdNull() {
        assertThatThrownBy(() -> new Review(null, USER_ID, 5, "내용"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("userId가 null이면 예외")
    void userIdNull() {
        assertThatThrownBy(() -> new Review(HOTEL_ID, null, 5, "내용"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rating이 1 미만이면 예외")
    void ratingTooLow() {
        assertThatThrownBy(() -> new Review(HOTEL_ID, USER_ID, 0, "내용"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rating이 5 초과면 예외")
    void ratingTooHigh() {
        assertThatThrownBy(() -> new Review(HOTEL_ID, USER_ID, 6, "내용"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rating이 null이면 예외")
    void ratingNull() {
        assertThatThrownBy(() -> new Review(HOTEL_ID, USER_ID, null, "내용"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
