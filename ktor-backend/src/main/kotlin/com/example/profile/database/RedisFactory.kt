package com.example.profile.database

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig

object RedisFactory {
    private lateinit var pool: JedisPool

    fun init() {
        val config = JedisPoolConfig().apply {
            maxTotal = 128
            maxIdle = 128
            minIdle = 16
        }
        pool = JedisPool(config, "localhost", 6379)
    }

    fun getJedis() = pool.resource
}
