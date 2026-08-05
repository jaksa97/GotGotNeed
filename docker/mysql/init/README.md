# MySQL initialization scripts

Any `.sql` (or `.sh`/`.sql.gz`) file placed in this directory is executed, in
alphabetical order, by the official MySQL image **once**, the first time the
container starts against a brand-new (empty) `gotgotneed-mysql-data` volume.

This is intended for optional bootstrap data (e.g. lookup tables, seed/demo
rows) — it is **not** a replacement for the app's own schema migrations
(Flyway, under `backend/src/main/resources/db/migration`), which remain the
source of truth for schema changes.

Naming convention, so ordering stays predictable:

```
01_seed_lookup_tables.sql
02_seed_demo_data.sql
```

If you need to re-run these scripts after editing them, you must first drop
the volume (this destroys all current data):

```bash
docker compose down -v
docker compose up -d
```
