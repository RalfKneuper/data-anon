package com.github.dataanon.jdbc

import com.github.dataanon.DbConfig
import com.github.dataanon.Record
import com.github.dataanon.Table
import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import org.reactivestreams.Subscription
import reactor.core.publisher.BaseSubscriber
import reactor.core.publisher.SignalType
import java.sql.PreparedStatement


class TableWriter(dbConfig: DbConfig, val table: Table, totalNoOfRecords: Long, val progressBar: Boolean) : BaseSubscriber<Record>() {
    private var conn = dbConfig.conn()
    private lateinit var stmt: PreparedStatement
    private lateinit var fields: List<String>
    private val pb = ProgressBar(table.name, totalNoOfRecords, ProgressBarStyle.ASCII)
    private var batchIndex = 0
    private val BATCH_COUNT = 1000

    init {
        conn.autoCommit = false
    }

    override fun hookOnSubscribe(subscription: Subscription?) {
        if (progressBar) pb.start()
        val sql = table.generateWriteQuery()
        println(sql)
        this.stmt = conn.prepareStatement(sql)
        this.fields = table.allColumns()
        request(1)
    }

    override fun hookOnNext(record: Record) {
        batchIndex++
        fields.forEachIndexed { i, f ->
            val field = record.find(f)
            stmt.setObject(i + 1, field.newValue)
        }
        stmt.addBatch()

        if (batchIndex % BATCH_COUNT == 0) {
            stmt.executeBatch()
            conn.commit()
            stmt.clearBatch()
            batchIndex = 0
        }
        if (progressBar) pb.step()
        request(1)
    }

    override fun hookFinally(type: SignalType?) {
        if (progressBar) pb.stop()
        stmt.executeBatch()
        conn.commit()
        stmt.clearBatch()
        stmt.close()
        conn.close()
    }
}