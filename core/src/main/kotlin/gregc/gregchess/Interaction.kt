package gregc.gregchess

import kotlin.coroutines.*

class InteractionController: Continuation<Unit> {
    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Unit>) {
        result.getOrThrow()
    }

}

fun interact(block: suspend InteractionController.() -> Unit) {
    val c = InteractionController()
    block.createCoroutine(c, c).resume(Unit)
}