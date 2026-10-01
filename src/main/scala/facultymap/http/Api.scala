package facultymap.http

import facultymap.application.AdminAuth
import facultymap.application.MapService
import facultymap.domain.*
import io.scalaland.chimney.dsl.*
import sttp.model.StatusCode
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.*
import sttp.tapir.ztapir.*
import zio.*
import zio.json.*

final case class FloorDto(id: Long, label: String, width: Int, height: Int)
object FloorDto:
  given JsonCodec[FloorDto] = DeriveJsonCodec.gen[FloorDto]

final case class RoomDto(id: Long, floorId: Long, number: String, name: String,
  description: String, x: Int, y: Int, width: Int, height: Int, status: String, version: Int)
object RoomDto:
  given JsonCodec[RoomDto] = DeriveJsonCodec.gen[RoomDto]

final case class RoomInput(floorId: Long, number: String, name: String,
  description: String, x: Int, y: Int, width: Int, height: Int)
object RoomInput:
  given JsonCodec[RoomInput] = DeriveJsonCodec.gen[RoomInput]

final case class LoginInput(username: String, password: String)
object LoginInput:
  given JsonCodec[LoginInput] = DeriveJsonCodec.gen[LoginInput]

final case class TokenDto(token: String, expiresAtEpochSeconds: Long)
object TokenDto:
  given JsonCodec[TokenDto] = DeriveJsonCodec.gen[TokenDto]

final case class ApiError(code: String, message: String)
object ApiError:
  given JsonCodec[ApiError] = DeriveJsonCodec.gen[ApiError]

final class Api(service: MapService, auth: AdminAuth):
  private type Failure = (StatusCode, ApiError)

  private val base = endpoint.in("api" / "v1")
    .errorOut(statusCode.and(jsonBody[ApiError]))

  private val floorsEndpoint = base.get.in("floors").out(jsonBody[List[FloorDto]])
    .description("Список этажей в порядке отображения")
  private val roomsEndpoint = base.get.in("floors" / path[Long]("floorId") / "rooms")
    .out(jsonBody[List[RoomDto]]).description("Опубликованные помещения этажа")
  private val roomEndpoint = base.get.in("rooms" / path[Long]("roomId"))
    .out(jsonBody[RoomDto]).description("Опубликованное помещение")
  private val loginEndpoint = base.post.in("admin" / "login").in(jsonBody[LoginInput])
    .out(jsonBody[TokenDto]).description("Получить токен администратора")
  private val adminRoomsEndpoint = base.get.in("admin" / "floors" / path[Long]("floorId") / "rooms")
    .in(header[Option[String]]("Authorization")).out(jsonBody[List[RoomDto]])
    .description("Все помещения этажа, включая черновики")
  private val createEndpoint = base.post.in("admin" / "rooms")
    .in(header[Option[String]]("Authorization")).in(jsonBody[RoomInput])
    .out(jsonBody[RoomDto]).description("Создать черновик помещения")
  private val updateEndpoint = base.put.in("admin" / "rooms" / path[Long]("roomId"))
    .in(header[Option[String]]("Authorization")).in(jsonBody[RoomInput])
    .in(header[Int]("If-Match-Version")).out(jsonBody[RoomDto])
    .description("Изменить черновик с проверкой версии")
  private val publishEndpoint = base.post.in("admin" / "rooms" / path[Long]("roomId") / "publish")
    .in(header[Option[String]]("Authorization")).out(jsonBody[RoomDto])
    .description("Опубликовать помещение после проверки пересечений")
  private val unpublishEndpoint = base.post.in("admin" / "rooms" / path[Long]("roomId") / "unpublish")
    .in(header[Option[String]]("Authorization")).out(jsonBody[RoomDto])
    .description("Вернуть помещение в черновики")

  private def toFailure(error: MapError): Failure = error match
    case MapError.NotFound => (StatusCode.NotFound, ApiError("not_found", error.message))
    case _: MapError.InvalidInput => (StatusCode.BadRequest, ApiError("invalid_input", error.message))
    case _: MapError.Conflict => (StatusCode.Conflict, ApiError("conflict", error.message))
    case MapError.Unauthorized => (StatusCode.Unauthorized, ApiError("unauthorized", error.message))
    case MapError.Internal => (StatusCode.InternalServerError, ApiError("internal", error.message))

  private def result[A](effect: IO[MapError, A]): IO[Failure, A] = effect.mapError(toFailure)

  private def authorize(value: Option[String]): IO[MapError, Unit] =
    ZIO.fromEither(value.toRight(MapError.Unauthorized).flatMap(auth.authorize))

  val endpoints: List[ZServerEndpoint[Any, Any]] = List(
    floorsEndpoint.zServerLogic(_ =>
      service.floors.map(_.map(_.transformInto[FloorDto])).mapError(_ => toFailure(MapError.Internal))),
    roomsEndpoint.zServerLogic(floorId =>
      result(service.publicRooms(floorId)).map(_.map(_.transformInto[RoomDto]))),
    roomEndpoint.zServerLogic(roomId =>
      result(service.publicRoom(roomId)).map(_.transformInto[RoomDto])),
    loginEndpoint.zServerLogic(input =>
      result(ZIO.fromEither(auth.login(input.username, input.password)))
        .map:
          case (token, expiresAt) => TokenDto(token, expiresAt)
    ),
    adminRoomsEndpoint.zServerLogic:
      case (floorId, authorization) =>
        result(authorize(authorization) *> service.adminRooms(floorId))
          .map(_.map(_.transformInto[RoomDto])),
    createEndpoint.zServerLogic:
      case (authorization, input) =>
        result(authorize(authorization) *> service.create(input.transformInto[RoomDraft]))
          .map(_.transformInto[RoomDto]),
    updateEndpoint.zServerLogic:
      case (roomId, authorization, input, version) =>
        result(authorize(authorization) *> service.update(roomId, version, input.transformInto[RoomDraft]))
          .map(_.transformInto[RoomDto]),
    publishEndpoint.zServerLogic:
      case (roomId, authorization) =>
        result(authorize(authorization) *> service.publish(roomId, published = true))
          .map(_.transformInto[RoomDto]),
    unpublishEndpoint.zServerLogic:
      case (roomId, authorization) =>
        result(authorize(authorization) *> service.publish(roomId, published = false))
          .map(_.transformInto[RoomDto])
  )
