package facultymap

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import doobie.Transactor
import facultymap.application.AdminAuth
import facultymap.application.MapService
import facultymap.http.Api
import facultymap.infrastructure.PostgresMapRepository
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.Server
import zio.interop.catz.*

object Main extends ZIOAppDefault:
  private def required(name: String): Task[String] =
    ZIO.attempt(sys.env.getOrElse(name, throw new IllegalArgumentException(s"Missing $name")))

  private def createDataSource(url: String, user: String, password: String): Task[HikariDataSource] =
    ZIO.attempt:
      val config = new HikariConfig()
      config.setDriverClassName("org.postgresql.Driver")
      config.setJdbcUrl(url)
      config.setUsername(user)
      config.setPassword(password)
      config.setMaximumPoolSize(8)
      new HikariDataSource(config)

  def run: ZIO[Any, Throwable, Unit] = ZIO.scoped:
    for
      url <- required("DB_URL")
      user <- required("DB_USER")
      password <- required("DB_PASSWORD")
      adminUser <- required("ADMIN_USER")
      adminPassword <- required("ADMIN_PASSWORD")
      signingKey <- required("ADMIN_SIGNING_KEY")
      port <- ZIO.attempt(sys.env.getOrElse("PORT", "8080").toInt)
      dataSource <- ZIO.acquireRelease(createDataSource(url, user, password))
        (source => ZIO.succeed(source.close()))
      jdbcExecutor <- ZIO.acquireRelease(ZIO.succeed(Executors.newFixedThreadPool(8)))
        (executor => ZIO.succeed(executor.shutdown()))
      xa = Transactor.fromDataSource[Task](dataSource, ExecutionContext.fromExecutor(jdbcExecutor))
      api = new Api(new MapService(new PostgresMapRepository(xa)), new AdminAuth(adminUser, adminPassword, signingKey))
      docs = SwaggerInterpreter().fromServerEndpoints[Task](api.endpoints, "FacultyMap", "0.1.0")
      routes = ZioHttpInterpreter().toHttp(api.endpoints ++ docs)
      _ <- Server.serve(routes).provide(Server.defaultWithPort(port))
    yield ()
