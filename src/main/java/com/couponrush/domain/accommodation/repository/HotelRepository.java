package com.couponrush.domain.accommodation.repository;

import com.couponrush.domain.accommodation.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HotelRepository extends JpaRepository<Hotel, Long> {
}
