package gregc.gregchess

// TODO: check this statically
@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SelfType

data class OrderConstraint<T>(
    val runBeforeAll: Boolean = false,
    val runAfterAll: Boolean = false,
    val runBefore: Set<T> = emptySet(),
    val runAfter: Set<T> = emptySet()
)