package ares.interpreter

import ares.RedisCommands
//import ares.RedisCommands.ops._
import cats.Id
import redis.clients.jedis.Jedis

class JedisInterpreter(jedis: Jedis) extends RedisCommands.Interp[Id] {

  /*
  override def get(key: String): Id[Option[String]] = Option(jedis.get(key))

  override def set(key: String, value: String): Id[Option[Boolean]] = {
    Option(jedis.set(key, value)).map(_ == "OK")
  }
  */
  override def get(key: String): Id[Option[String]] = Option(jedis.get(key))

  override def set(key: String, value: String): Id[Option[Boolean]] = Option(jedis.set(key, value)).map(_ == "OK")
}
