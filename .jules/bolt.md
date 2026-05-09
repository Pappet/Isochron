## 2024-11-23 - Compose Recomposition Bottlenecks
**Learning:** Compose `@Composable` functions execute extremely frequently, and placing raw `.map { ... }.toSet()` or `.count { ... }` directly within the `@Composable` scope leads to massive memory allocations and GC churn as well as unnecessary CPU processing for calculating values.
**Action:** Always wrap `O(N)` list operations like `.map`, `.toSet`, `.count`, and `.sumOf` with `remember { ... }` in Jetpack Compose, using the appropriate underlying collection as the key, especially when dealing with lists of devices which can change or be large.

## 2024-11-23 - Room Flow Operators within Composition
**Learning:** Using Flow operators such as `map { it.size }.collectAsState()` directly within a `@Composable` block causes a Lint Error (`FlowOperatorInvokedInComposition`), because the Flow operator creates a new flow on every recomposition leading to infinite loops and massive resource leaks.
**Action:** Instead of mapping the flow size in the compose function, write a custom `@Query` in the Room DAO that performs the calculation natively using `SELECT COUNT(*)`, such as `observeDeviceCountByCategory(category: DeviceCategory): Flow<Int>`, and then directly observe the scalar result using `.collectAsState()` to avoid client-side allocation and the FlowOperatorInvokedInComposition error.

## 2024-11-24 - Unmemoized Collection Operations During Composition
**Learning:** Missed `O(N)` operations like `.sumOf`, `.map`, and `.filter` within Compose `MapScreen`, `MonitorScreen`, `LanScreen`, and `BleDetailScreen` cause memory allocation on every recomposition. Specifically operations combining properties inside conditional branches, or when generating nested view models.
**Action:** Consistently enforce the rule to wrap any `O(N)` collection iteration within a `@Composable` scope in a `remember { ... }` block, passing all external dependencies of the block as keys to `remember`.
