package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.FlightView;

public class GetFlightQuery extends Query<Single<FlightView>> {
    private String flightId;

    public GetFlightQuery() {}
    public GetFlightQuery(String flightId) { this.flightId = flightId; }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
}
