package code.restService

import zio._
import redis.embedded.RedisServer
import redis.clients.jedis.Jedis

// RedisServerClient layer
// at release time, clears the cache and stops the Redis server
trait RedisServerClient {
  val redisServer: RedisServer
  val redisClient: Jedis
}

case class RedisServerClientLive() extends RedisServerClient {
  val redisServer = new RedisServer(6379)
  try {
    redisServer.start()
  } catch {
    // just use it if already started
    case _: Throwable => ()
  }
  val redisClient = new Jedis()
}

object RedisServerClientLive {
  def releaseRSCAux(rsc: RedisServerClientLive): Unit = {
    rsc.redisClient.flushAll()
    rsc.redisClient.close()
    rsc.redisServer.stop()
  }
  def acquireRSC: ZIO[Any, Nothing, RedisServerClientLive] = ZIO.succeed(new RedisServerClientLive())
  def releaseRSC(rsc: RedisServerClientLive): ZIO[Any, Nothing, Unit] = ZIO.succeed(releaseRSCAux(rsc))

  val redisServerClientLive = ZIO.acquireRelease(acquireRSC)(releaseRSC)
  val layer = ZLayer.scoped(redisServerClientLive)
}
