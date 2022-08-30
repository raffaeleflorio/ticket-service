package io.github.raffaeleflorio.ticketservice.database.events;

import io.github.raffaeleflorio.ticketservice.Event;
import io.smallrye.mutiny.Uni;

import javax.json.Json;
import javax.json.JsonObject;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * An {@link Event} backed by a relational database
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
final class DbEvent implements Event {

  private final UUID id;
  private final DataSource dataSource;
  private final Supplier<OffsetDateTime> nowSupplier;
  private final Supplier<UUID> newTicketIdSupplier;

  /**
   * Builds an event
   *
   * @param id          The event's id
   * @param dataSource  The datasource
   * @param nowSupplier The supplier of now as an {@link OffsetDateTime}
   */
  DbEvent(final UUID id, final DataSource dataSource, final Supplier<OffsetDateTime> nowSupplier) {
    this(id, dataSource, nowSupplier, UUID::randomUUID);
  }

  DbEvent(
    final UUID id,
    final DataSource dataSource,
    final Supplier<OffsetDateTime> nowSupplier,
    final Supplier<UUID> newTicketIdSupplier
  ) {
    this.id = id;
    this.dataSource = dataSource;
    this.nowSupplier = nowSupplier;
    this.newTicketIdSupplier = newTicketIdSupplier;
  }

  @Override
  public Uni<JsonObject> asJsonObject() {
    try (
      var connection = this.dataSource.getConnection();
      var preparedStatement = this.preparedStatement(
        connection,
        "SELECT ID, TITLE, DESCRIPTION, POSTER, EVENT_TIMESTAMP, MAX_TICKETS, SOLD_TICKETS",
        "FROM EVENTS",
        "WHERE ID=?"
      )
    ) {
      preparedStatement.setObject(1, this.id);
      return this.asJsonObject(preparedStatement.executeQuery());
    } catch (SQLException e) {
      return Uni.createFrom().failure(new RuntimeException(e));
    }
  }

  private PreparedStatement preparedStatement(
    final Connection connection,
    final String... preparedStatementPieces
  ) throws SQLException {
    return connection.prepareStatement(String.join(" ", preparedStatementPieces));
  }

  private Uni<JsonObject> asJsonObject(final ResultSet rs) throws SQLException {
    if (rs.next()) {
      return Uni.createFrom().item(Json.createObjectBuilder()
        .add("id", rs.getString("ID"))
        .add("title", rs.getString("TITLE"))
        .add("description", rs.getString("DESCRIPTION"))
        .add("poster", rs.getString("POSTER"))
        .add("date", rs.getString("EVENT_TIMESTAMP"))
        .add("availableTickets", rs.getInt("MAX_TICKETS") - rs.getInt("SOLD_TICKETS"))
        .build()
      );
    } else {
      return Uni.createFrom().failure(new RuntimeException("Unable to fetch the event"));
    }
  }

  @Override
  public Uni<UUID> ticket(final UUID participant) {
    try (var connection = this.dataSource.getConnection()) {
      return this.ticket(participant, connection);
    } catch (SQLException e) {
      return Uni.createFrom().failure(new RuntimeException(e));
    }
  }

  private Uni<UUID> ticket(final UUID participant, final Connection connection) throws SQLException {
    try (
      var updateSoldTicketsStatement = this.preparedStatement(
        connection,
        "UPDATE EVENTS",
        "SET SOLD_TICKETS = SOLD_TICKETS + 1",
        "WHERE ID=? AND SOLD_TICKETS < MAX_TICKETS AND EVENT_TIMESTAMP >= NOW()"
      );
      var insertTicketStatement = this.preparedStatement(
        connection,
        "INSERT INTO TICKETS",
        "(ID, EVENT_ID, PARTICIPANT_ID, CREATION_TIMESTAMP)",
        "VALUES (?, ?, ?, ?)"
      )
    ) {
      connection.setAutoCommit(false);
      var ticketId = this.newTicketIdSupplier.get();
      updateSoldTicketsStatement.setObject(1, this.id);
      insertTicketStatement.setObject(1, ticketId);
      insertTicketStatement.setObject(2, this.id);
      insertTicketStatement.setObject(3, participant);
      insertTicketStatement.setObject(4, this.nowSupplier.get());
      if (updateSoldTicketsStatement.executeUpdate() > 0 && insertTicketStatement.executeUpdate() > 0) {
        connection.commit();
        return Uni.createFrom().item(ticketId);
      }
      connection.rollback();
      return Uni.createFrom().failure(new RuntimeException("Unable to book a ticket"));
    } catch (SQLException e) {
      connection.rollback();
      return Uni.createFrom().failure(new RuntimeException(e));
    }
  }
}
