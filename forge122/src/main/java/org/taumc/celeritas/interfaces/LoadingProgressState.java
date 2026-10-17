package org.taumc.celeritas.interfaces;

/** Task state only: a replacement renderer must still allocate its own size-dependent targets. */
public interface LoadingProgressState {
    void celtium$copyLoadingTask(LoadingProgressState previous);

    String celtium$loadingTitle();

    String celtium$loadingMessage();

    /** The saving path is allowed to report progress after Minecraft stops running. */
    boolean celtium$loadingSuccess();
}
