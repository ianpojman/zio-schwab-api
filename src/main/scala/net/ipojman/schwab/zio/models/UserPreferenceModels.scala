package net.ipojman.schwab.zio.models

import zio.json.*

// User preference response from GET /userPreference
case class UserPreferenceResponse(
  accounts: List[PreferenceAccount],
  streamerInfo: Option[List[StreamerInfo]] = None,
  offers: Option[List[Offer]] = None
)

object UserPreferenceResponse {
  implicit val decoder: JsonDecoder[UserPreferenceResponse] = DeriveJsonDecoder.gen[UserPreferenceResponse]
  implicit val encoder: JsonEncoder[UserPreferenceResponse] = DeriveJsonEncoder.gen[UserPreferenceResponse]
}

case class PreferenceAccount(
  accountNumber: String,
  primaryAccount: Boolean,
  `type`: String,
  nickName: Option[String] = None,
  accountColor: Option[String] = None,
  displayAcctId: Option[String] = None,
  autoPositionEffect: Option[Boolean] = None
)

object PreferenceAccount {
  implicit val decoder: JsonDecoder[PreferenceAccount] = DeriveJsonDecoder.gen[PreferenceAccount]
  implicit val encoder: JsonEncoder[PreferenceAccount] = DeriveJsonEncoder.gen[PreferenceAccount]
}

case class StreamerInfo(
  streamerSocketUrl: String,
  schwabClientCustomerId: String,
  schwabClientCorrelId: String,
  schwabClientChannel: String,
  schwabClientFunctionId: String
)

object StreamerInfo {
  implicit val decoder: JsonDecoder[StreamerInfo] = DeriveJsonDecoder.gen[StreamerInfo]
  implicit val encoder: JsonEncoder[StreamerInfo] = DeriveJsonEncoder.gen[StreamerInfo]
}

case class Offer(
  level2Permissions: Boolean,
  mktDataPermission: String
)

object Offer {
  implicit val decoder: JsonDecoder[Offer] = DeriveJsonDecoder.gen[Offer]
  implicit val encoder: JsonEncoder[Offer] = DeriveJsonEncoder.gen[Offer]
}