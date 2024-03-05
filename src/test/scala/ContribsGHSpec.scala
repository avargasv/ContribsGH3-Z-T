import zio._
import zio.test._
import zio.test.Assertion._

import code.restService._
import code.model.DomainErrorTypes._

object GroupContributorsSpec extends ZIOSpecDefault {

  val liveEnv = ZLayer.make[RestServer with zio.http.Client](
    zio.http.Client.default,
    RestServerLive.layer,
    RestClientLive.layer,
    RestServerCacheLive.layer,
    RedisServerClientTest.layer
  )

  val repoGen = Gen.elements("Repo1", "Repo2", "Repo3", "Repo4", "Repo5")
  val contributorGen = Gen.elements("Contributor1", "Contributor2", "Contributor3")
  val intGen = Gen.int(1, 999)
  val nrOfListElements = 50
  val nrOfLists = 100 // (default 200)

  val contributionGen = (repoGen <*> contributorGen <*> intGen).map(t => Contributor(t._1, t._2, t._3))
  val contributionListGen = Gen.listOfN(nrOfListElements)(contributionGen)

  def spec =

    suite("RestServer.groupContributors") (

      test("Total contributions equals expected total") {
        check(contributionListGen) { contributionList =>
          for {
            // could fail
            // resL <- RestServer.groupContributors("TestOrg", "organization", 1000, contributionList)
            // cannot fail for max number of contributions and size of list of contributions as configured
            resL <- RestServer.groupContributors("TestOrg", "organization", 20000, contributionList)
            expectedTotal = contributionList.map(_.contributions).sum
          } yield assertTrue(resL.head.contributions == expectedTotal)
        }
      } +
      test("Contributions by contributor are sorted in descending order") {
        check(contributionListGen) { contributionList =>
          for {
            resL <- RestServer.groupContributors("TestOrg", "organization", 1, contributionList)
            resLContribs: List[Int] = resL.map(_.contributions)
          } yield assert(resLContribs.reverse)(isSorted(Ordering[Int]))
        }
      } +
      test("Contributions by the largest contributor equals expected value") {
        check(contributionListGen) { contributionList =>
          for {
            resL <- RestServer.groupContributors("TestOrg", "organization", 1, contributionList)
            largestContributor: String = resL.head.contributor
            expectedValue = contributionList.groupBy(_.contributor).get(largestContributor).getOrElse(List[Contributor]()).map(_.contributions).sum
          } yield assertTrue(resL.head.contributions == expectedValue)
        }
      }

    ).provideShared(liveEnv) @@ TestAspect.samples(nrOfLists) @@ TestAspect.sequential

}

object ContributorsByOrganizationTestEnvSpec extends ZIOSpecDefault {

  val testEnv = ZLayer.make[RestServer with zio.http.Client](
    zio.http.Client.default,
    RestServerLive.layer,
    RestClientTest.layer,
    RestServerCacheLive.layer,
    RedisServerClientTest.layer
  )

  def spec =
    suite("RestServer.contributorsByOrganization") (

      suite("With test environment") (
        test("Without cache, returns List(Contributor(All revelation repos, Other contributors, 20399))") {
          for {
            respL <- RestServer.contributorsByOrganization("revelation", "organization", 10000)
            resp = respL.head
          } yield assertTrue(respL.length == 1) &&
                  assertTrue(resp.repo == "All revelation repos") &&
                  assertTrue(resp.contributor == "Other contributors") &&
                  assertTrue(resp.contributions == 20399)
        } +
        test("With cache, also returns List(Contributor(All revelation repos, Other contributors, 20399))") {
          for {
            respL <- RestServer.contributorsByOrganization("revelation", "organization", 10000)
            resp = respL.head
          } yield assertTrue(respL.length == 1, resp.repo == "All revelation repos",
                             resp.contributor == "Other contributors", resp.contributions == 20399)
        }
      ).provideShared(testEnv)

  ) @@ TestAspect.sequential
}

object ContributorsByOrganizationLiveEnvSpec extends ZIOSpecDefault {

  val liveEnv = ZLayer.make[RestServer with zio.http.Client](
    zio.http.Client.default,
    RestServerLive.layer,
    RestClientLive.layer,
    RestServerCacheLive.layer,
    RedisServerClientLive.layer
  )

  def spec =
    suite("RestServer.contributorsByOrganization") (

      suite("With live environment") (
        test("For organization 'xxx' fails with an ErrorTypeH error") {
          assertZIO(
            RestServer.contributorsByOrganization("xxx", "organization", 1000).exit
          )(fails(isSubtype[ErrorTypeH](anything)))
        } +
        test("For organization 'revelation' returns List(Contributor(All revelation repos, Other contributors, ???))") {
          for {
            respL <- RestServer.contributorsByOrganization("revelation", "organization", 10000)
            resp = respL.head
          } yield assertTrue(respL.length == 1) &&
                  assertTrue(resp.repo == "All revelation repos") &&
                  assertTrue(resp.contributor == "Other contributors")
        }
      ).provideShared(liveEnv)

    ) @@ TestAspect.sequential
}

