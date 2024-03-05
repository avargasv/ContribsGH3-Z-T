package code.restService

import zio._
import code.model.DomainErrorTypes._

import java.time.Instant
import scala.io.Source.fromFile

// RestClient test layer.
// Retrieves repos by organization and contributors by repo
// reading sample json files for the "revelation" organization,
// instead of getting that info from the GitHub REST API.

final case class RestClientTest() extends RestClient {

  val baseDir = "src/main/resources/contribsGH"

  def reposByOrganization(organization: Organization): ZIO[zio.http.Client, Nothing, List[Repository]] = {
    val fileName =
      if(organization == "revelation") baseDir + "/all-repos_revelation.json"
      else baseDir + "/nonExistentFile"
    val source = fromFile(fileName)
    val contents = try (source.getLines().mkString("")) finally (source.close())
    def processPage = { responsePage: String =>
      val name_RE = s""""name":"([^"]+)"""".r
      val name_L = (for (name_RE(name) <- name_RE.findAllIn(responsePage)) yield name).toList
      val updated_at_RE = s""""updated_at":"([^"]+)"""".r
      val updated_at_L = (for (updated_at_RE(updated_at) <- updated_at_RE.findAllIn(responsePage)) yield updated_at).
        toList.map(Instant.parse(_))
      name_L.zip(updated_at_L).map(p => Repository(p._1, p._2))
    }
    val resp = processPage(contents)
    ZIO.succeed(resp)
  }

  def contributorsByRepo(organization: Organization, repo: Repository): ZIO[zio.http.Client, Nothing, List[Contributor]] = {
    val fileName= baseDir + "/contribs_" + repo.name + ".json"
    val source = fromFile(fileName)
    val contents = try (source.getLines().mkString("")) finally (source.close())
    def processPage = { responsePage: String =>
      val login_RE = """"login":"([^"]+)"""".r
      val login_I = for (login_RE(login) <- login_RE.findAllIn(responsePage)) yield login
      val contributions_RE = """"contributions":([0-9]+)""".r
      val contributions_I = for (contributions_RE(contributions) <- contributions_RE.findAllIn(responsePage)) yield contributions
      val contributors_L = login_I.zip(contributions_I).map(p => Contributor(repo.name, p._1, p._2.toInt)).toList
      contributors_L
    }
    ZIO.succeed(processPage(contents))
  }

}

object RestClientTest {
  val layer =
    ZLayer.succeed(new RestClientTest())
}
