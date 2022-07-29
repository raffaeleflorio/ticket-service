package io.github.raffaeleflorio.ticketservice;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.json.JsonObject;
import java.util.UUID;

/**
 * An event
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
public interface Event {

  /**
   * Provides its external id given an origin
   *
   * @param origin The origin
   * @return The external id or empty
   */
  Multi<String> externalId(String origin);

  /**
   * Emits its {@link JsonObject} representation
   *
   * @return Its {@link JsonObject} representation
   */
  Uni<JsonObject> asJsonObject();

  /**
   * Books a ticket
   *
   * @param participant The participant id
   * @return The ticket's id
   */
  Uni<UUID> book(UUID participant);
}
