package facultymap.domain

import zio.test.*

object MapRulesSpec extends ZIOSpecDefault {
  private val floor = Floor(1, "1 этаж", 1000, 1000)
  private val draft = RoomDraft(1, "101", "Лекционная", "", 10, 20, 100, 80)
  private val room = Room(1, 1, "101", "Лекционная", "", 10, 20, 100, 80, "draft", 1)

  def spec = suite("Правила карты")(
    test("прямоугольник должен быть внутри этажа") {
      assertTrue(MapRules.validate(draft, floor).isRight,
        MapRules.validate(draft.copy(x = 950), floor).isLeft,
        MapRules.validate(draft.copy(width = 0), floor).isLeft)
    },
    test("касание границ не считается пересечением") {
      assertTrue(!room.rectangle.overlaps(Rectangle(110, 20, 50, 50)))
    },
    test("пересекающиеся помещения нельзя публиковать") {
      val other = room.copy(id = 2, x = 50, status = "published")
      assertTrue(MapRules.validatePublication(room, List(other)).isLeft)
    }
  )
}
