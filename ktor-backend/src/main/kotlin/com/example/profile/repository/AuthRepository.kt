package com.example.profile.repository

import com.example.profile.database.DatabaseFactory.dbQuery
import com.example.profile.models.UsersTable
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt

class AuthRepository {
    suspend fun createUser(username: String, email: String, passwordHash: String): Int = dbQuery {
        UsersTable.insert {
            it[UsersTable.username] = username
            it[UsersTable.email] = email
            it[UsersTable.passwordHash] = passwordHash
        }[UsersTable.id]
    }

    suspend fun findUserByUsername(username: String) = dbQuery {
        UsersTable.select { UsersTable.username eq username }.singleOrNull()
    }

    suspend fun userExists(username: String, email: String): Boolean = dbQuery {
        UsersTable.select { (UsersTable.username eq username) or (UsersTable.email eq email) }.count() > 0
    }
}
