package com.example.butler.data.local

import androidx.room.withTransaction

/**
 * Commandが同じ[AppDatabase]のDAOだけを使う限り、対象データと操作履歴を
 * 1つのRoomトランザクションとして確定またはロールバックする。
 */
interface OperationTransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomOperationTransactionRunner(
    private val database: AppDatabase,
) : OperationTransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction(block)
}
