package eventstreams

import play.api.libs.functional.syntax._
import play.api.libs.json._

package object auth {

  implicit val securedDomainWrites: Writes[SecuredDomain] =
    (__ \ "id").write[String].contramap(_.moduleId)
  implicit val functionPermissionWrites: Writes[FunctionPermission] =
    (__ \ "t").write[String].contramap(_.topicPattern)
  implicit val securedDomainPermissionsWrites: Writes[DomainPermissions] = (
    (__ \ "d").write[SecuredDomain] ~ (__ \ "p").write[Seq[FunctionPermission]])(unlift(DomainPermissions.unapply))
  implicit val rolePermissionsWrites: Writes[RolePermissions] =
    (__ \ "p").write[Seq[DomainPermissions]].contramap(_.domainPermissions)


  implicit val securedDomainReads: Reads[SecuredDomain] =
    (__ \ "id").read[String].map(SecuredDomain)
  implicit val functionPermissionReads: Reads[FunctionPermission] =
    (__ \ "t").read[String].map(FunctionPermission)
  implicit val securedDomainPermissionsReads: Reads[DomainPermissions] = (
    (__ \ "d").read[SecuredDomain] ~ (__ \ "p").read[Seq[FunctionPermission]])(DomainPermissions)
  implicit val rolePermissionsReads: Reads[RolePermissions] =
    (__ \ "p").read[Seq[DomainPermissions]].map(RolePermissions)

}
