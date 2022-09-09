package io.github.raffaeleflorio.ticketservice.client.events;

import io.github.raffaeleflorio.ticketservice.Events;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestResponse;

import javax.json.JsonObject;
import javax.ws.rs.*;
import java.util.UUID;

@Path("/events")
public final class EventsResource {

  private final Events events;

  public EventsResource(final Events events) {
    this.events = events;
  }

  @GET
  public Uni<JsonObject> upcomingEvents() {
    return this.events.upcoming().asJsonObject();
  }

  @POST
  @Path("/{id}/tickets")
  public Uni<RestResponse<Void>> bookTicket(
    @PathParam("id") final UUID id,
    @HeaderParam("participant") final UUID participant
  ) {
    return this.events.upcoming().event(id)
      .onItem().transformToUniAndMerge(event -> event.ticket(participant))
      .onItem().transform(ticketId -> RestResponse.<Void>accepted())
      .onFailure().recoverWithItem(() -> RestResponse.status(409))
      .onCompletion().ifEmpty().continueWith(RestResponse.notFound())
      .toUni();
  }
}
