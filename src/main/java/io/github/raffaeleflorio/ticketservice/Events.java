package io.github.raffaeleflorio.ticketservice;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.json.JsonObject;
import java.util.UUID;

/**
 * A collection of {@link Event}
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
public interface Events {

  /**
   * Adds an event to itself given a JSON description.
   * The description must have: externalId, origin, title, description, poster, date, maxTickets
   *
   * @param event The description to create the event
   * @return The added event
   */
  Uni<Event> event(JsonObject event);

  /**
   * Emits an event corresponded to the given id
   *
   * @param id The event's id
   * @return The event or empty
   */
  Multi<Event> event(UUID id);

  /**
   * Filters out unavailable events
   *
   * @return All available events
   */
  Events available();

  /**
   * Emits its {@link JsonObject} representation
   *
   * @return Its {@link JsonObject} representation
   */
  Uni<JsonObject> asJsonObject();
}
