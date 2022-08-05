package io.github.raffaeleflorio.ticketservice.butter.bookedtickets;

import io.github.raffaeleflorio.ticketservice.BookedTickets;
import io.github.raffaeleflorio.ticketservice.butter.Butter;
import io.github.raffaeleflorio.ticketservice.Events;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import java.util.UUID;

/**
 * {@link BookedTickets} backed by butter
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
@ApplicationScoped
final class ButterBookedTickets implements BookedTickets {

  private final Butter butter;
  private final Events events;

  /**
   * Builds sold tickets
   *
   * @param butter The Butter's API
   * @param events The events
   */
  ButterBookedTickets(@RestClient final Butter butter, final Events events) {
    this.butter = butter;
    this.events = events;
  }

  @Override
  public Uni<Void> add(final UUID ticket, final UUID participant, final UUID event) {
    return this.slug(event)
      .onItem().transformToUniAndMerge(slug -> this.createCollectionItem(ticket, participant, slug))
      .onFailure().recoverWithCompletion()
      .toUni();
  }

  private Multi<String> slug(final UUID eventId) {
    return this.events.event(eventId)
      .onItem().transformToMultiAndMerge(event -> event.externalId("BUTTER"));
  }

  private Uni<Void> createCollectionItem(final UUID ticket, final UUID participant, final String slug) {
    return this.butter.createCollectionItem(
      Json.createObjectBuilder()
        .add("key", "ticket")
        .add("status", "published")
        .add("fields", this.newTicketFields(ticket.toString(), participant.toString(), slug))
        .build()
    ).onItem().ignore().andContinueWithNull();
  }

  private JsonArrayBuilder newTicketFields(final String id, final String participant, final String slug) {
    return Json.createArrayBuilder().add(
      Json.createObjectBuilder()
        .add("id", id)
        .add("participant", participant)
        .add("event", slug)
    );
  }
}
