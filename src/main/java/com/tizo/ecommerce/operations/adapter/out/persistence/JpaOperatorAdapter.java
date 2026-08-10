package com.tizo.ecommerce.operations.adapter.out.persistence;

import com.tizo.ecommerce.operations.domain.Operator;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JpaOperatorAdapter {

    private final JdbcClient jdbc;

    public JpaOperatorAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Operator> find(Boolean active, String search) {
        boolean filterActive = active != null;
        String term = search == null ? "" : "%" + search.toLowerCase() + "%";
        return jdbc.sql("""
                        SELECT id, display_name, email, role, active
                        FROM operator_account
                        WHERE (:filterActive = FALSE OR active = :active)
                          AND (:search = '' OR LOWER(display_name) LIKE :search OR LOWER(email) LIKE :search)
                        ORDER BY active DESC, display_name ASC
                        """)
                .param("filterActive", filterActive)
                .param("active", active == null || active)
                .param("search", term)
                .query((row, number) -> new Operator(
                        row.getString("id"), row.getString("display_name"), row.getString("email"),
                        null, normalizeRole(row.getString("role")), row.getBoolean("active")))
                .list();
    }

    private String normalizeRole(String role) {
        return "SUPERVISOR".equals(role) ? "SUPERVISOR" : "OPERATOR";
    }
}
