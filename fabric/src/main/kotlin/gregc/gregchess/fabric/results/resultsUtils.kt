package gregc.gregchess.fabric.results

import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.results.MatchResults
import net.minecraft.text.Text

val MatchResults.text: Text get() = Text.translatable("end_reason.${endReason.module.namespace}.${endReason.name}", *args.toTypedArray())
