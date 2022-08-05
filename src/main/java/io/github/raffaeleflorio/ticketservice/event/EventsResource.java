package io.github.raffaeleflorio.ticketservice.event;

import io.github.raffaeleflorio.ticketservice.Butter;
import io.github.raffaeleflorio.ticketservice.Events;
import io.github.raffaeleflorio.ticketservice.NewEvent;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.*;
import java.util.UUID;
import java.util.function.Function;

@Path("/events")
public final class EventsResource {

  private final Events events;
  private final Function<JsonObject, NewEvent> newEventFn;

  @Inject
  public EventsResource(final Events events, @RestClient final Butter butter) {
    this(events, notification -> new NewEventFromButterWebHook(notification, butter));
  }

  EventsResource(final Events events, final Function<JsonObject, NewEvent> newEventFn) {
    this.events = events;
    this.newEventFn = newEventFn;
  }

  @POST
  @RolesAllowed("BUTTER")
  public Uni<Void> addEvent(final JsonObject eventPage) {
    return this.newEventFn.apply(eventPage).update(this.events);
  }

  @GET
  public Uni<JsonObject> availableEvents() {
    return this.events.available().asJsonObject();
  }

  @POST
  @Path("/{id}/tickets")
  public Uni<RestResponse<Void>> bookTicket(
    @PathParam("id") final UUID id,
    @HeaderParam("participant") final UUID participant
  ) {
    return this.events.available().event(id)
      .onItem().transformToUniAndMerge(event -> event.ticket(participant))
      .onItem().transform(ticketId -> RestResponse.<Void>accepted())
      .onFailure().recoverWithItem(() -> RestResponse.status(409))
      .onCompletion().ifEmpty().continueWith(RestResponse.notFound())
      .toUni();
  }
}
