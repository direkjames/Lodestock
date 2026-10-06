package io.github.direkjames.lodestock.paper.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/**
 * One SQLite file, one connection, one background thread. Everything is queued and written in
 * order, in batches, so the server thread never waits for the disk. Reads go through the same
 * queue, so they always see every earlier write.
 */
public final class Database {
    @FunctionalInterface
    public interface Task {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface Query<T> {
        T run(Connection connection) throws SQLException;
    }

    private record Job(String name, Task task) {}

    private static final Job STOP = new Job("stop", c -> {});
    private static final int MAX_BATCH = 200;
    private static final int SCHEMA_VERSION = 2;

    private final Logger log;
    private final Connection connection;
    private final BlockingQueue<Job> queue = new LinkedBlockingQueue<>();
    private final Thread writer;
    private volatile boolean open = true;

    public Database(File file, Logger log) throws SQLException {
        this.log = log;
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("This server has no SQLite driver (org.sqlite.JDBC).", e);
        }
        Connection opened = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try {
            try (Statement st = opened.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");   // a crash can't corrupt the file
                st.execute("PRAGMA synchronous=FULL");   // every commit is forced to disk
                st.execute("PRAGMA busy_timeout=5000");
            }
            createTables(opened);
            migrate(opened);
            opened.setAutoCommit(false);
        } catch (SQLException e) {
            try {
                opened.close();
            } catch (SQLException ignored) {
                // already failing
            }
            throw e;
        }
        this.connection = opened;
        this.writer = new Thread(this::loop, "Lodestock-Database");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    private static void createTables(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)");
            st.execute("INSERT OR IGNORE INTO meta (meta_key, meta_value) VALUES ('schema_version', '1')");
            st.execute("CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, price REAL NOT NULL, stock INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS trades (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, "
                    + "player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, action TEXT NOT NULL, item TEXT NOT NULL, "
                    + "amount INTEGER NOT NULL, money REAL NOT NULL)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_trades_player ON trades (player_uuid, time)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_trades_time ON trades (time)");
            st.execute("CREATE TABLE IF NOT EXISTS admin_log (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, "
                    + "who TEXT NOT NULL, action TEXT NOT NULL, details TEXT NOT NULL)");
        }
    }

    /** Upgrades an older database file to the current layout. Runs once at startup. */
    private static void migrate(Connection c) throws SQLException {
        int version = 1;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT meta_value FROM meta WHERE meta_key = 'schema_version'")) {
            if (rs.next()) {
                try {
                    version = Integer.parseInt(rs.getString(1).trim());
                } catch (NumberFormatException ignored) {
                    version = 1;
                }
            }
        }
        if (version >= SCHEMA_VERSION) return;
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            if (version < 2) {
                // 0.3.0: admin-set prices and stock are held back from drift / regeneration
                st.execute("ALTER TABLE items ADD COLUMN price_held INTEGER NOT NULL DEFAULT 0");
                st.execute("ALTER TABLE items ADD COLUMN stock_held INTEGER NOT NULL DEFAULT 0");
            }
            st.execute("UPDATE meta SET meta_value = '" + SCHEMA_VERSION + "' WHERE meta_key = 'schema_version'");
            c.commit();
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Queues a write. Returns immediately. */
    public void write(String name, Task task) {
        if (!open) return;
        queue.add(new Job(name, task));
    }

    /** Queues a read. It runs after every write queued before it. */
    public <T> CompletableFuture<T> query(Query<T> query) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (!open) {
            future.completeExceptionally(new IllegalStateException("The database is closed."));
            return future;
        }
        queue.add(new Job("query", c -> {
            try {
                future.complete(query.run(c));
            } catch (SQLException | RuntimeException e) {
                future.completeExceptionally(e);
            }
        }));
        return future;
    }

    /** Writes everything that is still queued, then closes the file. */
    public void close() {
        if (!open) return;
        open = false;
        queue.add(STOP);
        try {
            writer.join(10_000);
            if (writer.isAlive()) log.warning("The database did not finish saving in time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loop() {
        try {
            boolean stop = false;
            while (!stop) {
                Job first = queue.take();
                if (first == STOP) break;
                List<Job> batch = new ArrayList<>();
                batch.add(first);
                Job next;
                while (batch.size() < MAX_BATCH && (next = queue.poll()) != null) {
                    if (next == STOP) {
                        stop = true;
                        break;
                    }
                    batch.add(next);
                }
                runBatch(batch);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                log.warning("Could not close the database: " + e.getMessage());
            }
        }
    }

    private void runBatch(List<Job> batch) {
        try {
            for (Job job : batch) {
                try {
                    job.task().run(connection);
                } catch (Exception e) { // one bad job must never stop the writer thread
                    log.warning("Database task '" + job.name() + "' failed: " + e.getMessage());
                }
            }
            connection.commit();
        } catch (SQLException e) {
            log.severe("Could not save to the database: " + e.getMessage());
            try {
                connection.rollback();
            } catch (SQLException ignored) {
                // nothing more to do
            }
        }
    }
}