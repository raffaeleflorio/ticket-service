package io.github.raffaeleflorio.ticketservice;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

/**
 * Collection of booked tickets
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
public interface BookedTickets {

  /**
   * Adds a ticket to itself
   *
   * @param ticket      The ticket's id
   * @param participant The participant's id
   * @param event       The event's id
   * @return Nothing
   */
  Uni<Void> add(UUID ticket, UUID participant, UUID event);
}
