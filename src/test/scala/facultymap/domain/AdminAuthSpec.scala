package facultymap.domain

import facultymap.application.AdminAuth
import zio.test.*

object AdminAuthSpec extends ZIOSpecDefault {
  def spec = suite("Авторизация администратора")(
    test("правильные данные дают действующий токен") {
      val auth = new AdminAuth("admin", "password", "a-signing-key-that-is-at-least-32-bytes-long")
      val token = auth.login("admin", "password").toOption.get._1
      assertTrue(auth.authorize(s"Bearer $token").isRight)
    },
    test("изменённый токен и неверный пароль отклоняются") {
      val auth = new AdminAuth("admin", "password", "a-signing-key-that-is-at-least-32-bytes-long")
      val token = auth.login("admin", "password").toOption.get._1
      assertTrue(auth.login("admin", "wrong").isLeft,
        auth.authorize(s"Bearer ${token}x").isLeft)
    }
  )
}
