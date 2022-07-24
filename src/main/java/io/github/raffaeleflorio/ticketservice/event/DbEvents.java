package io.github.raffaeleflorio.ticketservice.event;

import io.github.raffaeleflorio.ticketservice.BookedTickets;
import io.github.raffaeleflorio.ticketservice.Event;
import io.github.raffaeleflorio.ticketservice.Events;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.*;
import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * {@link Events} backed by a relational database
 *
 * @author Raffaele Florio (raffaeleflorio@protonmail.com)
 */
@ApplicationScoped
final class DbEvents implements Events {

  private final DataSource dataSource;
  private final Function<UUID, Event> eventFn;
  private final String eventsSelectQuery;
  private final Supplier<UUID> eventIdSupplier;

  /**
   * Builds events
   *
   * @param dataSource    The datasource
   * @param bookedTickets The booked tickets
   * @author Raffaele Florio (raffaeleflorio@protonmail.com)
   */
  @Inject
  DbEvents(final DataSource dataSource, final BookedTickets bookedTickets) {
    this(
      dataSource,
      id -> new DbEvent(id, dataSource, bookedTickets),
      "SELECT ID FROM EVENTS",
      UUID::randomUUID
    );
  }

  DbEvents(
    final DataSource dataSource,
    final Function<UUID, Event> eventFn,
    final String eventsIdSelectQuery,
    final Supplier<UUID> eventIdSupplier
  ) {
    this.dataSource = dataSource;
    this.eventFn = eventFn;
    this.eventsSelectQuery = eventsIdSelectQuery;
    this.eventIdSupplier = eventIdSupplier;
  }

  @Override
  public Uni<Event> event(final JsonObject event) {
    try (var preparedStatement = this.preparedStatement(
      "INSERT INTO EVENTS",
      "(ID, EXTERNAL_ID, ORIGIN, TITLE, DESCRIPTION, POSTER, EVENT_TIMESTAMP, MAX_TICKETS)",
      "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
    )) {
      return this.event(preparedStatement, event);
    } catch (SQLException e) {
      return Uni.createFrom().failure(new RuntimeException(e));
    }
  }

  private PreparedStatement preparedStatement(final String... preparedStatementPieces) throws SQLException {
    return this.dataSource
      .getConnection()
      .prepareStatement(String.join(" ", preparedStatementPieces));
  }

  private Uni<Event> event(final PreparedStatement preparedStatement, final JsonObject event) throws SQLException {
    var id = this.eventIdSupplier.get();
    preparedStatement.setObject(1, id);
    preparedStatement.setString(2, event.getString("externalId"));
    preparedStatement.setString(3, event.getString("origin"));
    preparedStatement.setString(4, event.getString("title"));
    preparedStatement.setString(5, event.getString("description"));
    preparedStatement.setString(6, event.getString("poster"));
    preparedStatement.setObject(7, OffsetDateTime.parse(event.getString("date")));
    preparedStatement.setInt(8, event.getInt("maxTickets"));
    if (preparedStatement.executeUpdate() > 0) {
      return Uni.createFrom().item(this.eventFn.apply(id));
    } else {
      return Uni.createFrom().failure(new RuntimeException("Unable to add an event"));
    }
  }

  @Override
  public Uni<JsonObject> asJsonObject() {
    try (var preparedStatement = this.preparedStatement(this.eventsSelectQuery)) {
      return this.asJsonArray(preparedStatement.executeQuery())
        .onItem().transform(jsonArray -> Json.createObjectBuilder().add("events", jsonArray))
        .onItem().transform(JsonObjectBuilder::build);
    } catch (SQLException e) {
      return Uni.createFrom().failure(new RuntimeException(e));
    }
  }

  private Uni<JsonArray> asJsonArray(final ResultSet rs) throws SQLException {
    return this.events(rs)
      .onItem().transformToUniAndMerge(Event::asJsonObject)
      .collect().in(Json::createArrayBuilder, JsonArrayBuilder::add)
      .onItem().transform(JsonArrayBuilder::build);
  }

  private Multi<Event> events(final ResultSet rs) throws SQLException {
    var stream = Stream.<Event>builder();
    while (rs.next()) {
      stream.add(this.eventFn.apply(rs.getObject("ID", UUID.class)));
    }
    return Multi.createFrom().items(stream.build());
  }

  @Override
  public Multi<Event> event(final UUID id) {
    try (var preparedStatement = this.preparedStatement(
      this.eventsSelectQuery,
      this.eventsSelectQuery.contains(" WHERE ") ? "AND ID=?" : "WHERE ID=?"
    )) {
      preparedStatement.setObject(1, id);
      return this.event(preparedStatement.executeQuery());
    } catch (SQLException e) {
      return Multi.createFrom().failure(new RuntimeException(e));
    }
  }

  private Multi<Event> event(final ResultSet rs) throws SQLException {
    if (rs.next()) {
      return Multi.createFrom().item(this.eventFn.apply(rs.getObject("ID", UUID.class)));
    } else {
      return Multi.createFrom().empty();
    }
  }

  @Override
  public Events available() {
    return new DbEvents(
      this.dataSource,
      this.eventFn,
      "SELECT ID FROM EVENTS WHERE EVENT_TIMESTAMP >= NOW()",
      this.eventIdSupplier
    );
  }
}