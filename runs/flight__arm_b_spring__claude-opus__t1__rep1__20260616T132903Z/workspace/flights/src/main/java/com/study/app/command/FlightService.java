package com.study.app.command;

import com.study.app.domain.Flight;
import com.study.app.domain.Seat;
import com.study.app.query.FlightRepository;
import com.study.app.query.SeatRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FlightService {

    /** Outcome of a hold attempt. */
    public enum HoldResult { HELD, TAKEN, UNKNOWN }

    private final FlightRepository flights;
    private final SeatRepository seats;
    private final long holdTimeoutSeconds;

    private static final char[] COLS = {'A', 'B', 'C', 'D', 'E', 'F'};

    public FlightService(FlightRepository flights, SeatRepository seats,
                         @Value("${HOLD_TIMEOUT_SECONDS:20}") long holdTimeoutSeconds) {
        this.flights = flights;
        this.seats = seats;
        this.holdTimeoutSeconds = holdTimeoutSeconds;
    }

    @Transactional
    public Flight createFlight(int seatCount, int seatPrice) {
        String id = UUID.randomUUID().toString();
        Flight flight = flights.save(new Flight(id, seatCount, seatPrice));
        List<Seat> toCreate = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            int row = i / COLS.length + 1;
            char col = COLS[i % COLS.length];
            toCreate.add(new Seat(id, row + "" + col, i));
        }
        seats.saveAll(toCreate);
        return flight;
    }

    @Transactional(readOnly = true)
    public Optional<Flight> getFlight(String id) {
        return flights.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Seat> getSeats(String flightId) {
        return seats.findByFlightIdOrderByIdx(flightId);
    }

    /**
     * Acquire the per-seat HOLD for a booking. Idempotent: re-holding a seat
     * already held/owned by the same booking succeeds again. Returns the
     * flight's seat price on success so the caller can charge it.
     */
    @Transactional
    public Hold hold(String flightId, String label, String bookingId) {
        Optional<Flight> flight = flights.findById(flightId);
        if (flight.isEmpty()) return new Hold(HoldResult.UNKNOWN, 0);
        Optional<Seat> opt = seats.lockByFlightAndLabel(flightId, label);
        if (opt.isEmpty()) return new Hold(HoldResult.UNKNOWN, 0);

        Seat seat = opt.get();
        int price = flight.get().getSeatPrice();
        Instant now = Instant.now();

        if (Seat.BOOKED.equals(seat.getStatus())) {
            return bookingId.equals(seat.getOwnerBookingId())
                    ? new Hold(HoldResult.HELD, price)   // already ours
                    : new Hold(HoldResult.TAKEN, price);
        }
        if (seat.isHeldActive(now)) {
            return bookingId.equals(seat.getHoldBookingId())
                    ? new Hold(HoldResult.HELD, price)   // idempotent re-hold
                    : new Hold(HoldResult.TAKEN, price);
        }
        // available, or a previous hold has expired -> we take it
        seat.setStatus(Seat.HELD);
        seat.setHoldBookingId(bookingId);
        seat.setHoldExpiresAt(now.plus(holdTimeoutSeconds, ChronoUnit.SECONDS));
        seat.setOwnerBookingId(null);
        return new Hold(HoldResult.HELD, price);
    }

    /** Compensation: release a hold made by this booking. Idempotent. */
    @Transactional
    public void release(String flightId, String label, String bookingId) {
        Optional<Seat> opt = seats.lockByFlightAndLabel(flightId, label);
        if (opt.isEmpty()) return;
        Seat seat = opt.get();
        if (Seat.HELD.equals(seat.getStatus()) && bookingId.equals(seat.getHoldBookingId())) {
            seat.setStatus(Seat.AVAILABLE);
            seat.setHoldBookingId(null);
            seat.setHoldExpiresAt(null);
        }
    }

    /** Promote this booking's active hold to permanent ownership. Idempotent. */
    @Transactional
    public boolean confirm(String flightId, String label, String bookingId) {
        Optional<Seat> opt = seats.lockByFlightAndLabel(flightId, label);
        if (opt.isEmpty()) return false;
        Seat seat = opt.get();
        if (Seat.BOOKED.equals(seat.getStatus())) {
            return bookingId.equals(seat.getOwnerBookingId());
        }
        Instant now = Instant.now();
        if (seat.isHeldActive(now) && bookingId.equals(seat.getHoldBookingId())) {
            seat.setStatus(Seat.BOOKED);
            seat.setOwnerBookingId(bookingId);
            seat.setHoldExpiresAt(null);
            return true;
        }
        return false; // hold lost / expired
    }

    /** Backstop sweeper: free seats whose holds expired without completing. */
    @Scheduled(fixedDelayString = "${HOLD_SWEEP_MS:2000}")
    @Transactional
    public void expireHolds() {
        List<Seat> expired = seats.findExpiredHolds(Instant.now());
        for (Seat s : expired) {
            Optional<Seat> locked = seats.lockByFlightAndLabel(s.getFlightId(), s.getLabel());
            if (locked.isEmpty()) continue;
            Seat seat = locked.get();
            if (Seat.HELD.equals(seat.getStatus())
                    && seat.getHoldExpiresAt() != null
                    && seat.getHoldExpiresAt().isBefore(Instant.now())) {
                seat.setStatus(Seat.AVAILABLE);
                seat.setHoldBookingId(null);
                seat.setHoldExpiresAt(null);
            }
        }
    }

    public record Hold(HoldResult result, int seatPrice) {}
}
