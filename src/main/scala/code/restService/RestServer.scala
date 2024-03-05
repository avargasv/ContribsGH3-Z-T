package code.restService

import zio._
import zio.http._
import zio.http.codec.{Doc, HttpCodec}
import zio.http.codec.HttpCodec.{paramInt, paramStr}
import zio.http.endpoint.openapi.OpenAPIGen
import zio.macros._
import code.model.DomainErrorTypes._

import java.time.Duration
import java.util.Date

// RestServer layer
@accessible
trait RestServer {
  val runServer: ZIO[Any, Throwable, ExitCode]
  def contributorsByOrganization(organization: Organization, groupLevel: String, minContribs: Int):
    ZIO[Client, ErrorTypeH, List[Contributor]]
  def groupContributors(organization: Organization,
                        groupLevel: String,
                        minContribs: Int,
                        contributorsDetailed: List[Contributor]): List[Contributor]
}

final case class RestServerLive(restClient: RestClient, restServerCache: RestServerCache) extends RestServer {

  private val sdf = new java.text.SimpleDateFormat("dd-MM-yyyy hh:mm:ss")

  // retrieve contributors by repo using ZIO HTTP and a Redis cache
  def contributorsByOrganization(organization: Organization, groupLevel: String, minContribs: Int):
    ZIO[Client, ErrorTypeH, List[Contributor]] = for {
      initialInstant <- Clock.instant
      _ <- ZIO.debug(s"ContribsGH3-Z - Starting REST API call at ${sdf.format(Date.from(initialInstant))} - organization='$organization'")
      repos <- restClient.reposByOrganization(organization)
      _ <- ZIO.debug(s"ContribsGH3-Z - # of repos=${repos.length}")
      contributorsDetailed <- contributorsDetailedZIOWithCache(organization, repos)
      contributors = groupContributors(organization, groupLevel, minContribs, contributorsDetailed)
      finalInstant <- Clock.instant
      _ <- ZIO.debug(s"ContribsGH3-Z - Finished REST API call at ${sdf.format(Date.from(finalInstant))} - organization='$organization'")
      _ <- ZIO.debug(f"ContribsGH3-Z - Time elapsed from start to finish: ${Duration.between(initialInstant, finalInstant).toMillis / 1000.0}%3.2f seconds")
  } yield contributors

  private def contributorsDetailedZIOWithCache(organization: Organization, repos: List[Repository]):
    ZIO[Client, ErrorTypeH, List[Contributor]] = {

    val (reposUpdatedInCache, reposNotUpdatedInCache) = repos.partition(restServerCache.repoUpdatedInCache(organization, _))
    val contributorsDetailed_L_1: List[List[Contributor]] =
      reposUpdatedInCache.map { repo =>
        restServerCache.retrieveContributorsFromCache(organization, repo)
      }
    val contributorsDetailed_L_Z_2 =
      reposNotUpdatedInCache.map { repo =>
        restClient.contributorsByRepo(organization, repo)
      }

    // retrieve contributors by repo in parallel
    val contributorsDetailed_Z_L_2 = ZIO.collectAllPar(contributorsDetailed_L_Z_2).withParallelism(8)
    // retrieve contributors by repo sequentially
    //val contributorsDetailed_Z_L_2 = ZIO.collectAll(contributorsDetailed_L_Z_2)

    for {
      contributorsDetailed_L_2 <- contributorsDetailed_Z_L_2
      _ <- ZIO.succeed(restServerCache.updateCache(organization, reposNotUpdatedInCache, contributorsDetailed_L_2))
    } yield (contributorsDetailed_L_1 ++ contributorsDetailed_L_2).flatten

  }

  // group - sort list of contributors
  def groupContributors(organization: Organization,
                                groupLevel: String,
                                minContribs: Int,
                                contributorsDetailed: List[Contributor]): List[Contributor] = {
    val (contributorsGroupedAboveMin, contributorsGroupedBelowMin) = contributorsDetailed.
      map(c => if (groupLevel == "repo") c else c.copy(repo = s"All $organization repos")).
      groupBy(c => (c.repo, c.contributor)).
      view.mapValues(_.foldLeft(0)((acc, elt) => acc + elt.contributions)).
      map(p => Contributor(p._1._1, p._1._2, p._2)).
      partition(_.contributions >= minContribs)
    val contributorsGrouped = {
      (
        contributorsGroupedAboveMin
          ++
          contributorsGroupedBelowMin.
            map(c => c.copy(contributor = "Other contributors")).
            groupBy(c => (c.repo, c.contributor)).
            view.mapValues(_.foldLeft(0)((acc, elt) => acc + elt.contributions)).
            map(p => Contributor(p._1._1, p._1._2, p._2))
        ).toList.sortWith { (c1: Contributor, c2: Contributor) =>
        if (c1.repo != c2.repo) c1.repo < c2.repo
        else if (c1.contributor == "Other contributors") false
        else if (c1.contributions != c2.contributions) c1.contributions >= c2.contributions
        else c1.contributor < c2.contributor
      }
    }
    contributorsGrouped
  }

  // ZIO HTTP handler definition for the endpoint of the REST service
  object EndPointsServer extends EndPoints {
    private def handleGetContributorsEndPoint(organization: Organization,
                                              groupLevel: Option[String],
                                              minContribs: Option[Int]): ZIO[Client, ErrorTypeH, List[Contributor]] = {
      ZIO.logSpan("getContributors") {
        contributorsByOrganization(organization,
          groupLevel.getOrElse("organization"),
          minContribs.getOrElse(0))
      } @@ ZIOAspect.annotated("organization" -> organization,
        "groupLevel" -> groupLevel.toString,
        "minContribs" -> minContribs.toString)
    }
    val handlerContribsGH3Z: Handler[Client, ErrorTypeH, (Organization, Option[String], Option[Int]), List[Contributor]] =
      handler(handleGetContributorsEndPoint _)
    val routesContribsGH3Z: Routes[Client, Nothing] = Routes(getContributorsEndPoint.implement(handlerContribsGH3Z))
    val appContribsGH3Z: HttpApp[Client] = routesContribsGH3Z.sandbox.toHttpApp
  }

  // ZIO HTTP server definition
  import EndPointsServer._
  val port: Int = 8080
  val runServer: ZIO[Any, Throwable, ExitCode] = for {
    _ <- ZIO.debug(s"Starting server on http://0.0.0.0:$port")
    _ <- Server.serve(appContribsGH3Z).provide(Server.defaultWithPort(port), Client.default)
  } yield ExitCode.success

}

object RestServerLive {
  val layer =
    ZLayer.fromFunction(RestServerLive(_, _))
}

// REST service implementation as a running instance of a ZIO HTTP server
// with all dependencies provided as ZIO layers
object ContribsGH3Z extends ZIOAppDefault {

  override val run = {
    ZIO.serviceWithZIO[RestServer](_.runServer).
      provide(
        RestClientLive.layer,
        RestServerLive.layer,
        RestServerCacheLive.layer,
        RedisServerClientLive.layer
      )
  }

}

// ZIO HTTP Endpoints API definition for the REST service
import zio.http.endpoint.Endpoint

trait EndPoints {
  val gl = paramStr("group-level").optional ?? Doc.p("Grouping level parameter")
  val mc = paramInt("min-contribs").optional ?? Doc.p("Minimum contributions parameter")

  val getContributorsEndPoint =
    Endpoint(Method.GET / "org" / string("organization") / "contributors").
      query(gl).
      query(mc).
      outErrors[ErrorTypeH](
        HttpCodec.error[ErrorTypeH.OrganizationNotFound](Status.NotFound),
        HttpCodec.error[ErrorTypeH.LimitExceeded](Status.Forbidden),
        HttpCodec.error[ErrorTypeH.UnexpectedError](Status.InternalServerError)
      ).
      out[List[Contributor]](Doc.p("List of contributions")) ?? Doc.p("REST-API endpoint")
}

object EndPointDoc extends ZIOAppDefault with EndPoints {
  val docs = OpenAPIGen.fromEndpoints(getContributorsEndPoint)
  val run = Console.printLine(docs.toJson)
}
