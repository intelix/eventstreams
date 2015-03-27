package eventstreams.core.actors

import play.api.libs.json.JsValue

case class ModelConfigSnapshot(config: JsValue, meta: JsValue, state: Option[JsValue])
