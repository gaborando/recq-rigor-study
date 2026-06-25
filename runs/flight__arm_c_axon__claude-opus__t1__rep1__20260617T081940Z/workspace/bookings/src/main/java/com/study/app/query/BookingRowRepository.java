package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRowRepository extends JpaRepository<BookingRow, String> {

    @Query("select count(b) from BookingRow b where b.status = 'CONFIRMED'")
    long countConfirmed();

    @Query("select count(b) from BookingRow b where b.status = 'REJECTED'")
    long countRejected();

    @Query("select coalesce(sum(b.total), 0) from BookingRow b where b.status = 'CONFIRMED'")
    long revenue();
}
