package work.fking.corpa.invsnaps;

import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper;
import org.jdbi.v3.sqlite3.SQLitePlugin;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class SnapshotRepository {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS inventory_snapshot (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid VARCHAR(36) NOT NULL,
                reason VARCHAR(32) NOT NULL,
                inventory blob NOT NULL,
                created_at datetime NOT NULL
            );
            """;
    private static final String CREATE_INDEX = """
            CREATE INDEX IF NOT EXISTS idx__player_uuid ON inventory_snapshot(player_uuid);
            """;

    private static final String INSERT = """
            INSERT INTO 
                inventory_snapshot(player_uuid, reason, inventory, created_at) 
            VALUES 
                (:playerUuid, :reason, :inventory, :createdAt)
            """;

    private static final String LIST_BY_PLAYER_ID = """
            SELECT 
                id, 
                player_uuid,
                reason,
                inventory, 
                created_at 
            FROM 
                inventory_snapshot 
            WHERE 
                player_uuid = :uuid 
            ORDER BY 
                created_at DESC 
            LIMIT 5
            """;

    private static final String FIND_BY_ID = """
            SELECT
                id, 
                player_uuid,
                reason,
                inventory, 
                created_at 
            FROM 
                inventory_snapshot 
            WHERE 
                id = :id
            """;

    private final Jdbi jdbi;

    private SnapshotRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    public static SnapshotRepository create(Path directory) {
        var jdbi = Jdbi.create("jdbc:sqlite:" + directory.resolve("inventory_snapshots.sqlite")).installPlugin(new SQLitePlugin());

        try (var handle = jdbi.open()) {
            handle.execute(CREATE_TABLE);
            handle.execute(CREATE_INDEX);
        }
        jdbi.registerRowMapper(Snapshot.class, ConstructorMapper.of(Snapshot.class));
        return new SnapshotRepository(jdbi);
    }

    public Snapshot findById(int id) {
        try (var handle = jdbi.open()) {
            return handle.createQuery(FIND_BY_ID)
                         .bind("id", id)
                         .mapTo(Snapshot.class)
                         .one();
        }
    }

    public List<Snapshot> findForList(UUID playerUuid) {
        try (var handle = jdbi.open()) {
            return handle.createQuery(LIST_BY_PLAYER_ID)
                         .bind("uuid", playerUuid)
                         .mapTo(Snapshot.class)
                         .list();
        }
    }

    public void save(Snapshot snapshot) {
        try (var handle = jdbi.open()) {
            handle.createUpdate(INSERT)
                  .bindMethods(snapshot)
                  .execute();
        }
    }
}
