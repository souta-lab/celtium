package org.taumc.celeritas.core;

import org.soutalab.celtium.patch.PatchErrorRecorder;
import org.soutalab.celtium.patch.PatchLedger;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.taumc.celeritas.mixin.CeleritasVintageMixinPlugin;

/**
 * Records a failed celtium mixin into the patch ledger (item 356-C), so the next launch skips the failing
 * group by name instead of dying the same way at the same class. Registered from
 * {@link CeleritasLoadingPlugin#getMixinConfigs()}, before any config is prepared, so a failure while the
 * very first config is prepared is still caught.
 * <p>
 * This handler is <b>record-only</b>. Both entry points return the {@code ErrorAction} they were handed,
 * unchanged, in every case: this launch still stops on the failure. That is deliberate, and it is the
 * design's central judgement - by the time one mixin of a group has failed, the group's other mixins may
 * already be merged into the target class, and the groups are written on the assumption that all of them
 * are there. Downgrading the failure to a warning would trade a crash that names its cause for a session
 * of silently half-patched rendering. The launch stops; it stops having repaired the next one.
 * <p>
 * Only celtium's own mixins ({@code org.taumc.celeritas.mixin.}) are recorded. Another mod's failure is
 * passed through untouched - no record, no line - so this handler can never alter the outcome of a failure
 * that is not celtium's. The decisions themselves (which group, whether it may be quarantined at all, what
 * the line says) live in {@link PatchErrorRecorder} beside the ledger, where the offline suite pins them;
 * this class only adapts Mixin's callback shape to it and logs the resulting line.
 * <p>
 * Nothing here may throw: the handler runs inside Mixin's own error handling, and a second failure there
 * would bury the original one. Recording is wrapped accordingly, and the ledger is loaded once per launch
 * (a failed load is remembered, not retried per failure).
 */
public class CeltiumMixinErrorHandler implements IMixinErrorHandler {

    private static PatchLedger celtium$ledger;
    private static boolean celtium$ledgerLoaded;

    @Override
    public ErrorAction onPrepareError(IMixinConfig config, Throwable th, IMixinInfo mixin,
                                      ErrorAction action) {
        return celtium$record("prepare", mixin, th, action);
    }

    @Override
    public ErrorAction onApplyError(String targetClassName, Throwable th, IMixinInfo mixin,
                                    ErrorAction action) {
        return celtium$record("apply", mixin, th, action);
    }

    private static ErrorAction celtium$record(String phase, IMixinInfo mixin, Throwable error,
                                              ErrorAction action) {
        try {
            String mixinClassName = mixin == null ? null : mixin.getClassName();
            if (PatchErrorRecorder.isOwnMixin(mixinClassName)) {
                PatchErrorRecorder.Result result =
                        PatchErrorRecorder.record(celtium$patchLedger(), phase, mixinClassName, error);
                if (!result.line().isEmpty()) {
                    CeleritasVintageMixinPlugin.LOGGER.error(result.line());
                }
            }
        } catch (Throwable ignored) {
            // Recording is a note beside the failure, never a second failure on top of it.
        }
        return action;
    }

    /** {@return the launch's patch ledger, loaded once; null when the load itself failed}. */
    private static synchronized PatchLedger celtium$patchLedger() {
        if (!celtium$ledgerLoaded) {
            celtium$ledgerLoaded = true;
            try {
                celtium$ledger = PatchLedger.load();
            } catch (Throwable ignored) {
                celtium$ledger = null;
            }
        }
        return celtium$ledger;
    }
}
