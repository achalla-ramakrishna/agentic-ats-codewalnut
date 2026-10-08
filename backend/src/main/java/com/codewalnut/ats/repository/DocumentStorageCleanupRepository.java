package com.codewalnut.ats.repository;

import com.codewalnut.ats.domain.BackgroundTask;
import com.codewalnut.ats.domain.BackgroundTask.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Cleanup never changes the immutable manifest or document metadata, only verified legacy bytes. */
@Repository
@RequiredArgsConstructor
public class DocumentStorageCleanupRepository {
    private final JdbcTemplate jdbc;

    public record LockedDocument(BackgroundTask manifest, byte[] bytes) {}

    public List<BackgroundTask> eligible(Instant cutoff, int limit) {
        return jdbc.query("SELECT t.* FROM background_task t JOIN candidate_document d ON d.id=t.target_id "
                + "WHERE t.target_type='DOCUMENT' AND t.status='READY' AND t.verified_at<=? AND d.data IS NOT NULL "
                + "ORDER BY t.verified_at,t.id LIMIT ?", this::manifest, Timestamp.from(cutoff), limit);
    }

    /** Called only in the short transaction after the provider verification has finished. */
    public Optional<LockedDocument> lock(BackgroundTask expected) {
        return jdbc.query("SELECT t.*,d.data AS legacy_data FROM background_task t JOIN candidate_document d ON d.id=t.target_id "
                + "WHERE t.id=? AND t.target_type='DOCUMENT' FOR UPDATE",
                (rs, row) -> new LockedDocument(manifest(rs, row), rs.getBytes("legacy_data")),
                DocumentStorageRepository.bytes(expected.id())).stream().findFirst();
    }

    public int clearLegacy(BackgroundTask manifest) {
        return jdbc.update("UPDATE candidate_document SET data=NULL WHERE id=? AND data IS NOT NULL",
                DocumentStorageRepository.bytes(manifest.targetId()));
    }

    private BackgroundTask manifest(ResultSet rs, int row) throws SQLException {
        Timestamp verified = rs.getTimestamp("verified_at");
        return new BackgroundTask(DocumentStorageRepository.uuid(rs.getBytes("id")), Target.valueOf(rs.getString("target_type")),
                DocumentStorageRepository.uuid(rs.getBytes("target_id")), rs.getString("storage_key"), rs.getString("sha256"),
                rs.getLong("size_bytes"), rs.getString("status"), rs.getInt("attempts"), verified == null ? null : verified.toInstant());
    }
}
