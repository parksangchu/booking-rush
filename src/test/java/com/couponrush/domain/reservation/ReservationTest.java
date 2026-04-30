package com.couponrush.domain.reservation;

import com.couponrush.domain.reservation.entity.Reservation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reservation 엔티티 단위 테스트")
class ReservationTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 100L;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 5, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 5, 3);
    private static final Integer TOTAL_PRICE = 200_000;

    @Test
    @DisplayName("정상 생성")
    void create() {
        Reservation r = new Reservation(USER_ID, ROOM_ID, CHECK_IN, CHECK_OUT, TOTAL_PRICE);

        assertThat(r.getUserId()).isEqualTo(USER_ID);
        assertThat(r.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(r.getCheckIn()).isEqualTo(CHECK_IN);
        assertThat(r.getCheckOut()).isEqualTo(CHECK_OUT);
        assertThat(r.getTotalPrice()).isEqualTo(TOTAL_PRICE);
        assertThat(r.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("userId가 null이면 예외")
    void userIdNull() {
        assertThatThrownBy(() ->
            new Reservation(null, ROOM_ID, CHECK_IN, CHECK_OUT, TOTAL_PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("roomId가 null이면 예외")
    void roomIdNull() {
        assertThatThrownBy(() ->
            new Reservation(USER_ID, null, CHECK_IN, CHECK_OUT, TOTAL_PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("checkIn이 null이면 예외")
    void checkInNull() {
        assertThatThrownBy(() ->
            new Reservation(USER_ID, ROOM_ID, null, CHECK_OUT, TOTAL_PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("checkOut이 checkIn보다 이르면 예외")
    void invalidDateRange() {
        assertThatThrownBy(() -> new Reservation(USER_ID, ROOM_ID,
            LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 1), TOTAL_PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("checkIn == checkOut이면 예외 (당일 체크인/아웃 불가)")
    void sameDay() {
        assertThatThrownBy(() ->
            new Reservation(USER_ID, ROOM_ID, CHECK_IN, CHECK_IN, TOTAL_PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("totalPrice가 음수면 예외")
    void negativePrice() {
        assertThatThrownBy(() ->
            new Reservation(USER_ID, ROOM_ID, CHECK_IN, CHECK_OUT, -1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("totalPrice가 null이면 예외")
    void priceNull() {
        assertThatThrownBy(() ->
            new Reservation(USER_ID, ROOM_ID, CHECK_IN, CHECK_OUT, null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
