package net.ipojman.schwab.zio

import zio.*
import zio.http.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*
import net.ipojman.schwab.zio.models.*

/**
 * Tests for placeOrder, replaceOrder and cancelOrder against a fake client.
 * Nothing here opens a connection, reads a token file or starts a sign-in.
 */
object OrderCallsSpec extends ZIOSpecDefault {

  private val accountHash = "TESTHASH0001"
  private val apiBase     = "https://api.example.test"

  /** One write call as the fake client received it. */
  final case class SentCall(method: Method, endpoint: String, accessToken: String, jsonBody: Option[String])

  /** A SchwabClient that records write calls and answers each with `reply`. Reads are not used here. */
  final class FakeWriteClient(sent: Ref[List[SentCall]], reply: SentCall => Task[ApiWriteResponse]) extends SchwabClient {
    private def unused[A]: Task[A] = ZIO.fail(new IllegalStateException("not used in these tests"))
    def authorize: UIO[String]                                   = ZIO.succeed("")
    def getToken(code: String): Task[TokenResponse]              = unused
    def refreshToken(refreshToken: String): Task[TokenResponse]  = unused
    def getAccessToken: Task[TokenResponse]                      = unused
    def makeApiCall[T: JsonDecoder](endpoint: String, accessToken: String): Task[T] = unused
    def makeApiCallSafe[T: JsonDecoder](endpoint: String, accessToken: String): Task[SchwabApiResponse[T]] = unused
    def makeRawApiCall(endpoint: String, accessToken: String): Task[String] = unused
    def getUserPreference(accessToken: String): Task[String]     = unused

    override def makeWriteApiCall(
      method: Method,
      endpoint: String,
      accessToken: String,
      jsonBody: Option[String]
    ): Task[ApiWriteResponse] = {
      val call = SentCall(method, endpoint, accessToken, jsonBody)
      sent.update(_ :+ call) *> reply(call)
    }
  }

  /** Holds one fresh token in memory, so ensureAuthenticated returns it without a refresh or a sign-in. */
  final class InMemoryTokenStorage(token: TokenResponse) extends TokenStorage {
    def storeToken(token: TokenResponse): Task[Unit] = ZIO.unit
    def getToken(): Task[Option[TokenResponse]]      = ZIO.some(token)
    def getTokenWithTimestamp(): Task[Option[(TokenResponse, Long)]] =
      ZIO.succeed(Some((token, java.lang.System.currentTimeMillis() / 1000)))
  }

  private val testToken = TokenResponse(access_token = "test-access-token", expires_in = Some(3600))

  /** An OrderService over the fake client, and the list of calls it sent. */
  private def orderService(reply: SentCall => Task[ApiWriteResponse]): UIO[(OrderService, Ref[List[SentCall]])] =
    for {
      sent              <- Ref.make(List.empty[SentCall])
      authSemaphore     <- Semaphore.make(1)
      authInProgress    <- Ref.make[Option[Promise[Throwable, TokenResponse]]](None)
      refreshInProgress <- Ref.make[Option[Promise[Throwable, TokenResponse]]](None)
      seamless = new SeamlessSchwabClient(
                   new FakeWriteClient(sent, reply),
                   SchwabApiConfig(clientId = "test-client", clientSecret = "test-secret", apiBaseUrl = apiBase),
                   new InMemoryTokenStorage(testToken),
                   authSemaphore,
                   authInProgress,
                   refreshInProgress
                 )
    } yield (LiveOrderService(seamless), sent)

  private def created(location: Option[String]): Task[ApiWriteResponse] =
    ZIO.succeed(ApiWriteResponse(201, location, ""))

  private def ok: Task[ApiWriteResponse] = ZIO.succeed(ApiWriteResponse(200, None, ""))

  private def ordersUrl(orderId: Long) = s"$apiBase/trader/v1/accounts/$accountHash/orders/$orderId"

  private val leg = OrderLegRequest(
    orderLegType = "EQUITY",
    instrument = OrderInstrumentRequest(symbol = "XYZ", assetType = "EQUITY"),
    instruction = "SELL",
    quantity = 10
  )

  private val stopOrder = OrderRequest(
    session = "NORMAL",
    duration = "GOOD_TILL_CANCEL",
    orderType = "STOP",
    quantity = 10,
    stopPrice = Some(BigDecimal("101.25")),
    stopType = Some("STANDARD"),
    orderLegCollection = List(leg)
  )

  private val trailingStopOrder = OrderRequest(
    session = "NORMAL",
    duration = "GOOD_TILL_CANCEL",
    orderType = "TRAILING_STOP",
    quantity = 10,
    stopPriceLinkBasis = Some("LAST"),
    stopPriceLinkType = Some("PERCENT"),
    stopPriceOffset = Some(5.0),
    stopType = Some("STANDARD"),
    orderLegCollection = List(leg)
  )

  private def bodyFields(call: SentCall): Map[String, Json] =
    call.jsonBody.flatMap(_.fromJson[Json.Obj].toOption).map(_.fields.toMap).getOrElse(Map.empty)

  def spec = suite("OrderCallsSpec")(
    suite("placeOrder")(
      test("sends a stop as POST to the account's orders path, with prices as decimal strings") {
        for {
          (service, sent) <- orderService(_ => created(Some(ordersUrl(1001))))
          orderId         <- service.placeOrder(accountHash, stopOrder)
          calls           <- sent.get
          call   = calls.head
          fields = bodyFields(call)
          leg0   = fields.get("orderLegCollection").collect { case Json.Arr(items) => items.head }
        } yield assertTrue(
          orderId == 1001L,
          calls.size == 1,
          call.method == Method.POST,
          call.endpoint == s"trader/v1/accounts/$accountHash/orders",
          call.accessToken == "test-access-token",
          fields.get("orderType").contains(Json.Str("STOP")),
          fields.get("session").contains(Json.Str("NORMAL")),
          fields.get("duration").contains(Json.Str("GOOD_TILL_CANCEL")),
          fields.get("stopPrice").contains(Json.Str("101.25")),
          fields.get("stopType").contains(Json.Str("STANDARD")),
          fields.get("orderStrategyType").contains(Json.Str("SINGLE")),
          !fields.contains("price"),
          leg0.exists(_.toJson.contains("\"instruction\":\"SELL\"")),
          leg0.exists(_.toJson.contains("\"symbol\":\"XYZ\"")),
          leg0.exists(_.toJson.contains("\"assetType\":\"EQUITY\"")),
          leg0.exists(_.toJson.contains("\"quantity\":10"))
        )
      },
      test("sends a trailing stop with its link basis, link type and offset, and no stop price") {
        for {
          (service, sent) <- orderService(_ => created(Some(ordersUrl(1002))))
          orderId         <- service.placeOrder(accountHash, trailingStopOrder)
          calls           <- sent.get
          fields = bodyFields(calls.head)
        } yield assertTrue(
          orderId == 1002L,
          calls.head.method == Method.POST,
          fields.get("orderType").contains(Json.Str("TRAILING_STOP")),
          fields.get("stopPriceLinkBasis").contains(Json.Str("LAST")),
          fields.get("stopPriceLinkType").contains(Json.Str("PERCENT")),
          fields.get("stopPriceOffset").collect { case Json.Num(v) => v.doubleValue }.contains(5.0),
          !fields.contains("stopPrice")
        )
      },
      test("reads the id from a Location header with a trailing slash or a query string") {
        for {
          (service1, _) <- orderService(_ => created(Some(ordersUrl(1003) + "/")))
          id1           <- service1.placeOrder(accountHash, stopOrder)
          (service2, _) <- orderService(_ => created(Some(ordersUrl(1004) + "?x=1")))
          id2           <- service2.placeOrder(accountHash, stopOrder)
        } yield assertTrue(id1 == 1003L, id2 == 1004L)
      },
      test("a 2xx reply with no Location fails with OrderIdNotReturned, sent once") {
        for {
          (service, sent) <- orderService(_ => created(None))
          exit            <- service.placeOrder(accountHash, stopOrder).exit
          calls           <- sent.get
        } yield assert(exit)(fails(isSubtype[OrderIdNotReturned](anything))) && assertTrue(calls.size == 1)
      },
      test("a Location that does not end in a number fails with OrderIdNotReturned") {
        for {
          (service, _) <- orderService(_ => created(Some(s"$apiBase/trader/v1/accounts/$accountHash/orders/abc")))
          exit         <- service.placeOrder(accountHash, stopOrder).exit
        } yield assert(exit)(fails(isSubtype[OrderIdNotReturned](hasField("location", (e: OrderIdNotReturned) => e.location, isSome(endsWithString("/abc"))))))
      }
    ),
    suite("replaceOrder")(
      test("sends PUT to the order's path and returns the new id from Location") {
        for {
          (service, sent) <- orderService(_ => created(Some(ordersUrl(2002))))
          newId           <- service.replaceOrder(accountHash, 2001L, stopOrder)
          calls           <- sent.get
        } yield assertTrue(
          newId.contains(2002L),
          calls.size == 1,
          calls.head.method == Method.PUT,
          calls.head.endpoint == s"trader/v1/accounts/$accountHash/orders/2001",
          bodyFields(calls.head).get("stopPrice").contains(Json.Str("101.25"))
        )
      },
      test("returns None when the reply has no Location header") {
        for {
          (service, sent) <- orderService(_ => created(None))
          newId           <- service.replaceOrder(accountHash, 2001L, stopOrder)
          calls           <- sent.get
        } yield assertTrue(newId.isEmpty, calls.size == 1)
      }
    ),
    suite("cancelOrder")(
      test("sends DELETE to the order's path with no body") {
        for {
          (service, sent) <- orderService(_ => ok)
          _               <- service.cancelOrder(accountHash, 3001L)
          calls           <- sent.get
        } yield assertTrue(
          calls.size == 1,
          calls.head.method == Method.DELETE,
          calls.head.endpoint == s"trader/v1/accounts/$accountHash/orders/3001",
          calls.head.jsonBody.isEmpty
        )
      }
    ),
    suite("errors and retries")(
      test("a non-2xx reply fails with SchwabHttpError carrying its status and body") {
        val rejection = """{"message":"Order rejected"}"""
        for {
          (service, _) <- orderService(c => ZIO.fail(new SchwabHttpError(400, s"$apiBase/${c.endpoint}", rejection)))
          exit         <- service.placeOrder(accountHash, stopOrder).exit
        } yield assert(exit)(
          fails(
            isSubtype[SchwabHttpError](
              hasField("status", (e: SchwabHttpError) => e.status, equalTo(400)) &&
                hasField("body", (e: SchwabHttpError) => e.body, equalTo(rejection))
            )
          )
        )
      },
      test("no write call is retried, on a 401 or a 500") {
        def failWith(status: Int) = (c: SentCall) => ZIO.fail(new SchwabHttpError(status, s"$apiBase/${c.endpoint}", ""))
        for {
          (s1, sent1) <- orderService(failWith(401))
          _           <- s1.placeOrder(accountHash, stopOrder).exit
          (s2, sent2) <- orderService(failWith(401))
          _           <- s2.replaceOrder(accountHash, 2001L, stopOrder).exit
          (s3, sent3) <- orderService(failWith(500))
          _           <- s3.cancelOrder(accountHash, 3001L).exit
          n1          <- sent1.get.map(_.size)
          n2          <- sent2.get.map(_.size)
          n3          <- sent3.get.map(_.size)
        } yield assertTrue(n1 == 1, n2 == 1, n3 == 1)
      }
    ),
    suite("LiveSchwabClient write request and reply")(
      test("builds a request with the method, URL, bearer token, JSON content type and body") {
        val url = ordersUrl(2001)
        for {
          request <- LiveSchwabClient.writeRequest(Method.PUT, url, "test-access-token", Some("""{"orderType":"STOP"}"""))
          body    <- request.body.asString
        } yield assertTrue(
          request.method == Method.PUT,
          request.url.encode == url,
          request.rawHeader("Authorization").contains("Bearer test-access-token"),
          request.rawHeader("Content-Type").exists(_.startsWith("application/json")),
          body == """{"orderType":"STOP"}"""
        )
      },
      test("builds a DELETE with no body and no content type") {
        for {
          request <- LiveSchwabClient.writeRequest(Method.DELETE, ordersUrl(3001), "test-access-token", None)
          body    <- request.body.asString
        } yield assertTrue(request.method == Method.DELETE, body.isEmpty, request.rawHeader("Content-Type").isEmpty)
      },
      test("a 201 reply gives its status and Location") {
        val response = Response(status = Status.Created, headers = Headers("Location", ordersUrl(1001)))
        for {
          reply <- LiveSchwabClient.readWriteResponse(ordersUrl(0), response)
        } yield assertTrue(reply == ApiWriteResponse(201, Some(ordersUrl(1001)), ""))
      },
      test("a 400 reply fails with SchwabHttpError carrying its status and body") {
        val rejection = """{"message":"Order rejected"}"""
        val response  = Response(status = Status.BadRequest, body = Body.fromString(rejection))
        for {
          exit <- LiveSchwabClient.readWriteResponse(ordersUrl(0), response).exit
        } yield assert(exit)(
          fails(
            isSubtype[SchwabHttpError](
              hasField("status", (e: SchwabHttpError) => e.status, equalTo(400)) &&
                hasField("body", (e: SchwabHttpError) => e.body, equalTo(rejection)) &&
                hasMessage(containsString("400"))
            )
          )
        )
      }
    )
  )
}
