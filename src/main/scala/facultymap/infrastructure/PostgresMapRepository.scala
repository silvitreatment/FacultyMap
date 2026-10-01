package facultymap.infrastructure

import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import facultymap.application.MapRepository
import facultymap.domain.*
import zio.*
import zio.interop.catz.*

final class PostgresMapRepository(xa: Transactor[Task]) extends MapRepository:
  private type RoomRow = (Long, Long, String, String, String, Int, Int, Int, Int, String, Int)

  private def toRoom(row: RoomRow): Room =
    Room(row._1, row._2, row._3, row._4, row._5, row._6, row._7,
      row._8, row._9, row._10, row._11)

  def floors: Task[List[Floor]] =
    sql"SELECT id, label, width, height FROM floors ORDER BY id"
      .query[Floor].to[List].transact(xa)

  def floor(id: Long): Task[Option[Floor]] =
    sql"SELECT id, label, width, height FROM floors WHERE id = $id"
      .query[Floor].option.transact(xa)

  def rooms(floorId: Long, publishedOnly: Boolean): Task[List[Room]] =
    sql"""
      SELECT id, floor_id, number, name, description, x, y, width, height, status, version
      FROM rooms
      WHERE floor_id = $floorId
        AND ($publishedOnly = false OR status = 'published')
      ORDER BY number
    """.query[RoomRow].to[List].map(_.map(toRoom)).transact(xa)

  def room(id: Long, publishedOnly: Boolean): Task[Option[Room]] =
    sql"""
      SELECT id, floor_id, number, name, description, x, y, width, height, status, version
      FROM rooms
      WHERE id = $id
        AND ($publishedOnly = false OR status = 'published')
    """.query[RoomRow].option.map(_.map(toRoom)).transact(xa)

  def create(draft: RoomDraft): Task[Room] =
    val floorId = draft.floorId
    val number = draft.number
    val name = draft.name
    val description = draft.description
    val x = draft.x
    val y = draft.y
    val width = draft.width
    val height = draft.height

    sql"""
      INSERT INTO rooms (floor_id, number, name, description, x, y, width, height)
      VALUES ($floorId, $number, $name, $description, $x, $y, $width, $height)
      RETURNING id, floor_id, number, name, description, x, y, width, height, status, version
    """.query[RoomRow].unique.map(toRoom).transact(xa)

  def update(id: Long, expectedVersion: Int, draft: RoomDraft): Task[Option[Room]] =
    val floorId = draft.floorId
    val number = draft.number
    val name = draft.name
    val description = draft.description
    val x = draft.x
    val y = draft.y
    val width = draft.width
    val height = draft.height

    sql"""
      UPDATE rooms
      SET floor_id = $floorId, number = $number, name = $name, description = $description,
          x = $x, y = $y, width = $width, height = $height, version = version + 1
      WHERE id = $id AND version = $expectedVersion AND status = 'draft'
      RETURNING id, floor_id, number, name, description, x, y, width, height, status, version
    """.query[RoomRow].option.map(_.map(toRoom)).transact(xa)

  def setPublished(id: Long, published: Boolean): Task[Either[MapError, Room]] =
    val transaction: ConnectionIO[Either[MapError, Room]] =
      for
        lockedRoom <- sql"""
          SELECT id, floor_id, number, name, description, x, y, width, height, status, version
          FROM rooms
          WHERE id = $id
          FOR UPDATE
        """.query[RoomRow].option.map(_.map(toRoom))
        result <- lockedRoom match
          case None =>
            (Left(MapError.NotFound): Either[MapError, Room]).pure[ConnectionIO]
          case Some(room) =>
            val floorId = room.floorId
            val rightEdge = room.x.toLong + room.width
            val bottomEdge = room.y.toLong + room.height
            val leftEdge = room.x
            val topEdge = room.y

            for
              // Locking the floor serializes concurrent publication on that floor.
              _ <- sql"SELECT id FROM floors WHERE id = $floorId FOR UPDATE".query[Long].unique
              overlap <-
                if published then
                  sql"""
                    SELECT EXISTS (
                      SELECT 1 FROM rooms
                      WHERE floor_id = $floorId AND id <> $id AND status = 'published'
                        AND x < $rightEdge AND x + width > $leftEdge
                        AND y < $bottomEdge AND y + height > $topEdge
                    )
                  """.query[Boolean].unique
                else false.pure[ConnectionIO]
              updated <-
                if overlap then
                  val conflict: Either[MapError, Room] =
                    Left(MapError.Conflict("Помещение пересекается с опубликованным помещением"))
                  conflict.pure[ConnectionIO]
                else
                  val status = if published then "published" else "draft"
                  val result: Either[MapError, Room] =
                    Right(room.copy(status = status, version = room.version + 1))
                  sql"UPDATE rooms SET status = $status, version = version + 1 WHERE id = $id"
                    .update.run.as(result)
            yield updated
      yield result

    transaction.transact(xa)
