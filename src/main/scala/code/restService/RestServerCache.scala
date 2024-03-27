package code.restService

import zio._
import java.time.Instant
import code.model.DomainErrorTypes._
import scala.jdk.CollectionConverters._

// RestServerCache layer
trait RestServerCache {
  def repoUpdatedInCache(org:Organization, repo: Repository): Boolean
  def retrieveContributorsFromCache(org:Organization, repo: Repository): List[Contributor]
  def updateCache(organization: Organization, reposNotUpdatedInCache: List[Repository], contributors_L:
                  List[List[Contributor]]): Unit
}

case class RestServerCacheLive(redisServerClient: RedisServerClient) extends RestServerCache {

  private def contribToString(c: Contributor) = c.contributor.trim + ":" + c.contributions

  private def stringToContrib(r: Repository, s: String) = {
    val v = s.split(":").toVector
    Contributor(r.name, v(0).trim, v(1).trim.toInt)
  }

  private def buildRepoK(o: Organization, r: Repository) = o.trim + "-" + r.name

  // return true if the repository was updated in the Redis cache after it was updated on the GitHub server
  def repoUpdatedInCache(org: Organization, repo: Repository): Boolean = {
    val repoK = buildRepoK(org, repo)
    redisServerClient.redisClient.lrange(repoK, 0, 0).asScala.toList match {
      case s :: _ =>
        val cachedUpdatedAt = Instant.parse(s.substring(s.indexOf(":") + 1))
        cachedUpdatedAt.compareTo(repo.updatedAt) >= 0
      case _ => false
    }
  }

  def retrieveContributorsFromCache(org:Organization, repo: Repository): List[Contributor] = {
    val repoK = buildRepoK(org, repo)
    val res = redisServerClient.redisClient.
              lrange(repoK, 1, redisServerClient.redisClient.llen(repoK).toInt - 1).asScala.toList
    res.map(s => stringToContrib(repo, s))
  }

  private def saveContributorsToCache(org: Organization, repo: Repository, contributors: List[Contributor]) = {
    val repoK = buildRepoK(org, repo)
    redisServerClient.redisClient.del(repoK)
    contributors.foreach { c: Contributor =>
      redisServerClient.redisClient.lpush(repoK, contribToString(c))
    }
    redisServerClient.redisClient.lpush(repoK, s"updatedAt:${repo.updatedAt.toString}")
  }

  // update cache with non-existent or recently-modified repos
  def updateCache(organization: Organization, reposNotUpdatedInCache: List[Repository], contributors_L: List[List[Contributor]]): Unit = {
    contributors_L.foreach { contribs_L =>
      if (contribs_L.length > 0) {
        val repoK = organization.trim + "-" + contribs_L.head.repo
        reposNotUpdatedInCache.find(r => (organization.trim + "-" + r.name) == repoK) match {
          case Some(repo) =>
            saveContributorsToCache(organization, repo, contribs_L)
          case None =>
            ()
        }
      }
    }
  }

}

object RestServerCacheLive {
  val layer =
    ZLayer.fromFunction(RestServerCacheLive(_))
}
