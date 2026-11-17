package dev.phoenix.store;

import dev.phoenix.domain.Approval;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.List;

public class ApprovalRepository {

    private final JdbcClient jdbc;

    public ApprovalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(Approval a) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO approvals (scope, granted_via, user_words, granted_at, expires_at, consumed_at) "
                        + "VALUES (:scope, :via, :words, :granted, :expires, :consumed)")
                .param("scope", a.scope()).param("via", a.grantedVia()).param("words", a.userWords())
                .param("granted", Times.format(a.grantedAt())).param("expires", Times.format(a.expiresAt()))
                .param("consumed", Times.format(a.consumedAt())).update(keys);
        return keys.getKey().longValue();
    }

    public List<Approval> findByScope(String scope) {
        return jdbc.sql("SELECT id, scope, granted_via, user_words, granted_at, expires_at, consumed_at "
                        + "FROM approvals WHERE scope = :s ORDER BY id")
                .param("s", scope)
                .query((rs, i) -> new Approval(rs.getLong("id"), rs.getString("scope"), rs.getString("granted_via"),
                        rs.getString("user_words"), Times.parse(rs.getString("granted_at")),
                        Times.parse(rs.getString("expires_at")), Times.parse(rs.getString("consumed_at"))))
                .list();
    }

    public void consume(long id, Instant at) {
        jdbc.sql("UPDATE approvals SET consumed_at = :at WHERE id = :id AND consumed_at IS NULL")
                .param("at", Times.format(at)).param("id", id).update();
    }
}
