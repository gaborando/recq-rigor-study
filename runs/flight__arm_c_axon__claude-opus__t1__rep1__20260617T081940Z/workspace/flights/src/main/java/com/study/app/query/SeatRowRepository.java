package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRowRepository extends JpaRepository<SeatRow, String> {
    List<SeatRow> findByFlightId(String flightId);
}
