## 2024-06-25 - Batch SQLite Reads and Writes for N+1 Query Optimization
**Optimization:** Replaced N+1 individual queries and upserts with batched `IN` queries and `upsertDevices` for Wi-Fi/Bluetooth scans.
**Learning:** Iterating over networks and hitting SQLite sequentially (N+1 problem) introduces significant CPU and IO overhead on mobile devices. Fetching existing records in bulk and persisting batched inserts/updates drastically reduces transaction overhead.
**Expected Impact:** 10x-100x speedup when persisting large network scans (e.g. hundreds of APs or BLE devices) depending on database size.
