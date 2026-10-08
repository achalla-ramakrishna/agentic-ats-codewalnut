package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Short database-only steps; provider calls never hold task leases inside a transaction. */
@Repository
@RequiredArgsConstructor
public class DocumentStorageRepository {
    private final JdbcTemplate jdbc;

    public void enqueue(Target type, UUID target, byte[] data, String sha256) {
        jdbc.update("INSERT IGNORE INTO background_task (id,target_type,target_id,storage_key,sha256,size_bytes) VALUES (?,?,?,?,?,?)",
                bytes(UUID.randomUUID()), type.name(), bytes(target), type.key(target), sha256, data.length);
    }

    public Optional<BackgroundTask> find(Target type, UUID target) {
        return jdbc.query("SELECT * FROM background_task WHERE target_type=? AND target_id=?", this::map,
                type.name(), bytes(target)).stream().findFirst();
    }

    public List<BackgroundTask> due() {
        return jdbc.query("SELECT * FROM background_task WHERE status='PENDING' AND run_after<=CURRENT_TIMESTAMP(6) "
                + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP(6)) ORDER BY created_at,id LIMIT 10", this::map);
    }

    public boolean claim(UUID id, UUID token) {
        return jdbc.update("UPDATE background_task SET lease_token=?,lease_until=TIMESTAMPADD(MINUTE,5,CURRENT_TIMESTAMP(6)) "
                + "WHERE id=? AND status='PENDING' AND run_after<=CURRENT_TIMESTAMP(6) "
                + "AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP(6))", bytes(token), bytes(id)) == 1;
    }

    public void complete(UUID id, UUID token, boolean skipped) {
        jdbc.update("UPDATE background_task SET status=?,verified_at=?,lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?",
                skipped ? "SKIPPED" : "READY", skipped ? null : Timestamp.from(Instant.now()), bytes(id), bytes(token));
    }

    public void fail(UUID id, UUID token, int attempts) {
        jdbc.update("UPDATE background_task SET status=?,attempts=attempts+1,run_after=TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP(6)),"
                + "lease_token=NULL,lease_until=NULL WHERE id=? AND lease_token=?", attempts >= 7 ? "FAILED" : "PENDING",
                Math.min(3600, 30 * (1 << Math.min(attempts, 7))), bytes(id), bytes(token));
    }

    public int retryFailed() {
        return jdbc.update("UPDATE background_task SET status='PENDING',attempts=0,run_after=CURRENT_TIMESTAMP(6) WHERE status='FAILED'");
    }

    public byte[] staged(Target type, UUID id) {
        // Table names come solely from the enum, never request input.
        List<byte[]> rows = jdbc.query("SELECT data FROM " + type.table() + " WHERE id=?", (rs, row) -> rs.getBytes(1), bytes(id));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<UUID> unqueued(Target type, int limit) {
        return jdbc.query("SELECT d.id FROM " + type.table() + " d LEFT JOIN background_task t ON t.target_type=? AND t.target_id=d.id "
                + "WHERE d.data IS NOT NULL AND t.id IS NULL ORDER BY d.id LIMIT ?", (rs, row) -> uuid(rs.getBytes(1)), type.name(), limit);
    }

    public Map<String, Long> counts() {
        var counts = new java.util.LinkedHashMap<String, Long>();
        jdbc.query("SELECT status,COUNT(*) FROM background_task GROUP BY status", rs -> {
            counts.put(rs.getString(1), rs.getLong(2));
        });
        for (Target type : Target.values()) {
            Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + type.table()
                    + " d LEFT JOIN background_task t ON t.target_type=? AND t.target_id=d.id WHERE d.data IS NOT NULL AND t.id IS NULL",
                    Long.class, type.name());
            counts.put("UNQUEUED_" + type.name(), count);
        }
        return counts;
    }

    private BackgroundTask map(ResultSet rs, int row) throws SQLException {
        Timestamp verified = rs.getTimestamp("verified_at");
        return new BackgroundTask(uuid(rs.getBytes("id")), Target.valueOf(rs.getString("target_type")),
                uuid(rs.getBytes("target_id")), rs.getString("storage_key"), rs.getString("sha256"),
                rs.getLong("size_bytes"), rs.getString("status"), rs.getInt("attempts"), verified == null ? null : verified.toInstant());
    }

    public static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    public static UUID uuid(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
