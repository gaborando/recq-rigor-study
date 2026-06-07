package com.study.app.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SchemaInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        // Drop any legacy single-column unique constraint on notifications.order_id.
        // The current schema requires (order_id, status) to allow both CONFIRMED and
        // CANCELLED notifications per order. Hibernate's ddl-auto=update adds the new
        // constraint but won't drop the old one automatically.
        jdbcTemplate.execute("""
            DO $$
            DECLARE cname text;
            BEGIN
                SELECT tc.constraint_name INTO cname
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name
                    AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = 'notifications'
                  AND tc.constraint_type = 'UNIQUE'
                  AND kcu.column_name = 'order_id'
                GROUP BY tc.constraint_name
                HAVING count(kcu.column_name) = 1;
                IF FOUND AND cname IS NOT NULL THEN
                    EXECUTE 'ALTER TABLE notifications DROP CONSTRAINT ' || quote_ident(cname);
                END IF;
            END $$;
        """);
    }
}
