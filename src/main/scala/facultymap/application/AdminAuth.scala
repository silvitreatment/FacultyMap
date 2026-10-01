package facultymap.application

import facultymap.domain.MapError
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A small stateless admin session. The signing key stays on the server. */
final class AdminAuth(username: String, password: String, signingKey: String):
  require(signingKey.getBytes(UTF_8).length >= 32, "ADMIN_SIGNING_KEY must contain at least 32 bytes")

  private val encoder = Base64.getUrlEncoder.withoutPadding()
  private val random = new SecureRandom()

  def login(inputUser: String, inputPassword: String): Either[MapError, (String, Long)] =
    if !equal(inputUser, username) || !equal(inputPassword, password) then
      Left(MapError.Unauthorized)
    else
      val expiresAt = Instant.now().plusSeconds(3600).getEpochSecond
      val nonce = new Array[Byte](16)
      random.nextBytes(nonce)
      val encodedNonce = encoder.encodeToString(nonce)
      val payload = s"$expiresAt.$encodedNonce"
      val signature = encoder.encodeToString(sign(payload))
      Right((s"$payload.$signature", expiresAt))

  def authorize(header: String): Either[MapError, Unit] =
    val parts = header.stripPrefix("Bearer ").split("\\.", -1)
    if !header.startsWith("Bearer ") || parts.length != 3 then
      Left(MapError.Unauthorized)
    else
      val expiry = parts(0)
      val nonce = parts(1)
      val payload = s"$expiry.$nonce"
      val validSignature =
        try equalBytes(Base64.getUrlDecoder.decode(parts(2)), sign(payload))
        catch
          case _: IllegalArgumentException => false
      if parts(0).toLongOption.exists(_ > Instant.now().getEpochSecond) && validSignature then
        Right(())
      else Left(MapError.Unauthorized)

  private def sign(value: String): Array[Byte] =
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(signingKey.getBytes(UTF_8), "HmacSHA256"))
    mac.doFinal(value.getBytes(UTF_8))

  private def equal(left: String, right: String): Boolean =
    equalBytes(left.getBytes(UTF_8), right.getBytes(UTF_8))

  private def equalBytes(left: Array[Byte], right: Array[Byte]): Boolean =
    MessageDigest.isEqual(left, right)
