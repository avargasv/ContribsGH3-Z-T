package code.restService

import zio._
import redis.embedded.RedisServer
import redis.clients.jedis.Jedis

// RedisServerClient test layer
// does not clear the cache or stops the Redis server
case class RedisServerClientTest() extends RedisServerClient {
  val redisServer = new RedisServer(6379)
  try {
    redisServer.start()
  } catch {
    // just use it if already started
    case _: Throwable => ()
  }
  val redisClient = new Jedis()
}

object RedisServerClientTest {
  val layer = ZLayer.succeed(new RedisServerClientLive())
}
