package facultymap.domain

final case class Floor(id: Long, label: String, width: Int, height: Int)

final case class Rectangle(x: Int, y: Int, width: Int, height: Int):
  def inside(floor: Floor): Boolean =
    x >= 0 && y >= 0 && width > 0 && height > 0 &&
      x.toLong + width <= floor.width && y.toLong + height <= floor.height

  def overlaps(other: Rectangle): Boolean =
    x.toLong < other.x.toLong + other.width && other.x.toLong < x.toLong + width &&
      y.toLong < other.y.toLong + other.height && other.y.toLong < y.toLong + height

final case class Room(
  id: Long,
  floorId: Long,
  number: String,
  name: String,
  description: String,
  x: Int,
  y: Int,
  width: Int,
  height: Int,
  status: String,
  version: Int
):
  def rectangle: Rectangle = Rectangle(x, y, width, height)

final case class RoomDraft(
  floorId: Long,
  number: String,
  name: String,
  description: String,
  x: Int,
  y: Int,
  width: Int,
  height: Int
):
  def rectangle: Rectangle = Rectangle(x, y, width, height)

enum MapError(val message: String):
  case NotFound extends MapError("Объект не найден")
  case InvalidInput(details: String) extends MapError(details)
  case Conflict(details: String) extends MapError(details)
  case Unauthorized extends MapError("Требуется авторизация")
  case Internal extends MapError("Внутренняя ошибка сервера")

object MapRules:
  def validate(draft: RoomDraft, floor: Floor): Either[MapError, Unit] =
    val number = draft.number.trim
    val name = draft.name.trim
    if number.isEmpty || number.length > 32 || name.isEmpty || name.length > 160 then
      Left(MapError.InvalidInput("Номер и название обязательны; максимум 32 и 160 символов"))
    else if draft.description.length > 4000 then
      Left(MapError.InvalidInput("Описание длиннее 4000 символов"))
    else if !draft.rectangle.inside(floor) then
      Left(MapError.InvalidInput("Помещение должно целиком находиться внутри этажа"))
    else Right(())

  def validatePublication(room: Room, otherPublished: List[Room]): Either[MapError, Unit] =
    if otherPublished.exists(other => other.id != room.id && other.rectangle.overlaps(room.rectangle)) then
      Left(MapError.Conflict("Помещение пересекается с опубликованным помещением"))
    else Right(())
