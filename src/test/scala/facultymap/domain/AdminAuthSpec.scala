package facultymap.domain

import facultymap.application.AdminAuth
import zio.test.*

object AdminAuthSpec extends ZIOSpecDefault:
  private val auth = new AdminAuth("admin", "password", "a-signing-key-that-is-at-least-32-bytes-long")
  private val token = auth.login("admin", "password").toOption.get._1
  private val tamperedToken = token + "x"

  def spec = suite("Авторизация администратора")(
    test("правильные данные дают действующий токен")(
      assertTrue(auth.authorize(s"Bearer $token").isRight)
    ),
    test("изменённый токен и неверный пароль отклоняются")(
      assertTrue(
        auth.login("admin", "wrong").isLeft,
        auth.authorize(s"Bearer $tamperedToken").isLeft
      )
    )
  )
