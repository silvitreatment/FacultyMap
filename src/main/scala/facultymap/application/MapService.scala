package facultymap.application

import facultymap.domain.*
import zio.*

trait MapRepository:
  def floors: Task[List[Floor]]
  def floor(id: Long): Task[Option[Floor]]
  def rooms(floorId: Long, publishedOnly: Boolean): Task[List[Room]]
  def room(id: Long, publishedOnly: Boolean): Task[Option[Room]]
  def create(draft: RoomDraft): Task[Room]
  def update(id: Long, expectedVersion: Int, draft: RoomDraft): Task[Option[Room]]
  def setPublished(id: Long, published: Boolean): Task[Either[MapError, Room]]

final class MapService(repository: MapRepository):
  def floors: Task[List[Floor]] = repository.floors

  def publicRooms(floorId: Long): IO[MapError, List[Room]] =
    for
      _ <- requireFloor(floorId)
      rooms <- repository.rooms(floorId, publishedOnly = true).mapError(_ => MapError.Internal)
    yield rooms

  def publicRoom(id: Long): IO[MapError, Room] =
    repository.room(id, publishedOnly = true).mapError(_ => MapError.Internal)
      .someOrFail(MapError.NotFound)

  def adminRooms(floorId: Long): IO[MapError, List[Room]] =
    for
      _ <- requireFloor(floorId)
      rooms <- repository.rooms(floorId, publishedOnly = false).mapError(_ => MapError.Internal)
    yield rooms

  def create(draft: RoomDraft): IO[MapError, Room] =
    for
      floor <- requireFloor(draft.floorId)
      _ <- ZIO.fromEither(MapRules.validate(draft, floor))
      room <- repository.create(normalize(draft)).mapError(databaseError)
    yield room

  def update(id: Long, version: Int, draft: RoomDraft): IO[MapError, Room] =
    for
      existing <- repository.room(id, publishedOnly = false).mapError(_ => MapError.Internal)
        .someOrFail(MapError.NotFound)
      _ <- ZIO.fail(MapError.Conflict("Опубликованное помещение сначала снимите с публикации"))
        .when(existing.status == "published")
      floor <- requireFloor(draft.floorId)
      _ <- ZIO.fromEither(MapRules.validate(draft, floor))
      room <- repository.update(id, version, normalize(draft)).mapError(databaseError)
        .someOrFail(MapError.Conflict("Версия изменилась; перечитайте помещение"))
    yield room

  def publish(id: Long, published: Boolean): IO[MapError, Room] =
    repository.setPublished(id, published).mapError(databaseError).flatMap(ZIO.fromEither)

  private def requireFloor(id: Long): IO[MapError, Floor] =
    repository.floor(id).mapError(_ => MapError.Internal).someOrFail(MapError.NotFound)

  private def normalize(draft: RoomDraft): RoomDraft =
    draft.copy(number = draft.number.trim, name = draft.name.trim, description = draft.description.trim)

  private def databaseError(error: Throwable): MapError = error match
    case sql: java.sql.SQLException if sql.getSQLState == "23505" =>
      MapError.Conflict("Номер помещения уже занят на этом этаже")
    case _ => MapError.Internal
