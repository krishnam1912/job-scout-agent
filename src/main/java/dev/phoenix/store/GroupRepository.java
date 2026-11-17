package dev.phoenix.store;

import dev.phoenix.domain.JobGroup;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroupRepository {

    private final JdbcClient jdbc;

    public GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(String company, String normTitle, int[] signature, Instant createdAt) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO job_groups (company, norm_title, minhash, created_at) VALUES (:c, :t, :m, :at)")
                .param("c", company).param("t", normTitle).param("m", encode(signature))
                .param("at", Times.format(createdAt)).update(keys);
        return keys.getKey().longValue();
    }

    public List<JobGroup> findByCompany(String company) {
        return jdbc.sql("SELECT id, company, norm_title, minhash, created_at FROM job_groups WHERE company = :c ORDER BY id")
                .param("c", company)
                .query((rs, i) -> new JobGroup(rs.getLong("id"), rs.getString("company"), rs.getString("norm_title"),
                        decode(rs.getBytes("minhash")), Times.parse(rs.getString("created_at"))))
                .list();
    }

    /** True when some member has moved past candidate stage, so a new posting of the same role must not resurface. */
    public boolean hasEngagedMember(long groupId) {
        Integer n = jdbc.sql("SELECT COUNT(*) FROM jobs WHERE group_id = :g AND state NOT IN "
                        + "('DISCOVERED','FILTERED_OUT','CANDIDATE')")
                .param("g", groupId).query(Integer.class).single();
        return n > 0;
    }

    /** Ids of groups that have at least one member beyond candidate stage; their other members must not resurface. */
    public Set<Long> engagedGroupIds() {
        return new HashSet<>(jdbc.sql("SELECT DISTINCT group_id FROM jobs WHERE group_id IS NOT NULL AND state NOT IN "
                        + "('DISCOVERED','FILTERED_OUT','CANDIDATE')")
                .query(Long.class).list());
    }

    static byte[] encode(int[] signature) {
        ByteBuffer buf = ByteBuffer.allocate(signature.length * Integer.BYTES);
        for (int v : signature) {
            buf.putInt(v);
        }
        return buf.array();
    }

    static int[] decode(byte[] bytes) {
        if (bytes == null) {
            return new int[0];
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        int[] out = new int[bytes.length / Integer.BYTES];
        for (int i = 0; i < out.length; i++) {
            out[i] = buf.getInt();
        }
        return out;
    }
}
