package gregc.gregchess.fabric.results

import gregc.gregchess.registry.module
import gregc.gregchess.registry.name
import gregc.gregchess.results.MatchResults
import net.minecraft.text.Text
import net.minecraft.text.TranslatableText

val MatchResults.text: Text get() = TranslatableText("end_reason.${endReason.module.namespace}.${endReason.name}", *args.toTypedArray())
