package io.github.raffaeleflorio.ticketservice.bookedtickets;

import io.github.raffaeleflorio.ticketservice.BookedTickets;
import io.github.raffaeleflorio.ticketservice.Butter;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

/**
 * {@link BookedTickets} backed by butter
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
@ApplicationScoped
final class ButterBookedTickets implements BookedTickets {

  private final Butter butter;
  private final DataSource dataSource;

  /**
   * Builds sold tickets
   *
   * @param butter     The Butter's API
   * @param dataSource The datasource
   */
  ButterBookedTickets(@RestClient final Butter butter, final DataSource dataSource) {
    this.butter = butter;
    this.dataSource = dataSource;
  }

  @Override
  public Uni<Void> add(final UUID ticket, final UUID participant, final UUID event) {
    return this.slug(event)
      .onItem().transformToUni(slug -> this.createCollectionItem(ticket, participant, slug))
      .onFailure().recoverWithNull();
  }

  private Uni<String> slug(final UUID event) {
    try (var preparedStatement = this.dataSource
      .getConnection()
      .prepareStatement("SELECT EXTERNAL_ID FROM EVENTS WHERE ORIGIN='BUTTER' AND ID=?")
    ) {
      preparedStatement.setObject(1, event);
      var rs = preparedStatement.executeQuery();
      if (rs.next()) {
        return Uni.createFrom().item(rs.getString(1));
      }
      return Uni.createFrom().failure(new RuntimeException("Unable to get a event's slug"));
    } catch (SQLException e) {
      return Uni.createFrom().failure(new RuntimeException(e));
    }
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
