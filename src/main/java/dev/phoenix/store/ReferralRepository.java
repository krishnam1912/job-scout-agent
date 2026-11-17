package dev.phoenix.store;

import dev.phoenix.domain.Referral;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

public class ReferralRepository {

    private final JdbcClient jdbc;

    public ReferralRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(Referral r) {
        Integer n = jdbc.sql("SELECT COUNT(*) FROM referrals WHERE name = :n AND COALESCE(company,'') = :c "
                        + "AND COALESCE(job_ref,'') = :j AND COALESCE(last_contact,'') = :l")
                .param("n", r.name()).param("c", nz(r.company())).param("j", nz(r.jobRef()))
                .param("l", nz(r.lastContact())).query(Integer.class).single();
        return n > 0;
    }

    public void insert(Referral r) {
        jdbc.sql("INSERT INTO referrals (name, connection, company, job_ref, status, last_contact, follow_up, note) "
                        + "VALUES (:n, :conn, :c, :j, :s, :l, :f, :note)")
                .param("n", r.name()).param("conn", r.connection()).param("c", r.company()).param("j", r.jobRef())
                .param("s", r.status()).param("l", r.lastContact()).param("f", r.followUp()).param("note", r.note())
                .update();
    }

    public List<Referral> findAll() {
        return jdbc.sql("SELECT name, connection, company, job_ref, status, last_contact, follow_up, note FROM referrals ORDER BY id")
                .query((rs, i) -> new Referral(rs.getString("name"), rs.getString("connection"), rs.getString("company"),
                        rs.getString("job_ref"), rs.getString("status"), rs.getString("last_contact"),
                        rs.getString("follow_up"), rs.getString("note")))
                .list();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
