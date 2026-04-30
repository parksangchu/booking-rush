package com.couponrush.domain.accommodation.repository;

import com.couponrush.domain.accommodation.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {
}
