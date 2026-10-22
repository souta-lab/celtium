package org.soutalab.celtium.vulkan;

/**
 * {@code -Dceltium.vulkan.eventTrace=true}: print every SDL event the window subsystem reports that can end a session.
 * <p>
 * The launcher's window closes a few frames after it goes fullscreen, and the pump records that as a close request
 * either from {@code SDL_EVENT_QUIT} (256) or from {@code SDL_EVENT_WINDOW_CLOSE_REQUESTED} (528) - one flag, two
 * senders. An event watch sees the raw event, so the two can be told apart, and the window events around it say
 * whether the compositor asked for the close or SDL gave up on the display.
 */
final class CeltiumSdlEventTrace {
    private static org.lwjgl.sdl.SDL_EventFilter filter;

    private CeltiumSdlEventTrace() {
    }

    static void installOnce() {
        if (!Boolean.getBoolean("celtium.vulkan.eventTrace") || filter != null) {
            return;
        }
        filter = org.lwjgl.sdl.SDL_EventFilter.create((userdata, event) -> {
            int type = org.lwjgl.system.MemoryUtil.memGetInt(event);
            boolean interesting = type == 256 || (type >= 512 && type < 1024) || type == 2304
                    || (type >= 4096 && type <= 4100);
            if (interesting) {
                StringBuilder detail = new StringBuilder();
                if (type >= 514 && type <= 536) {
                    long windowEvent = event;
                    detail.append(" windowID=").append(org.lwjgl.system.MemoryUtil.memGetInt(windowEvent + 12));
                    detail.append(" data1=").append(org.lwjgl.system.MemoryUtil.memGetInt(windowEvent + 16));
                    detail.append(" data2=").append(org.lwjgl.system.MemoryUtil.memGetInt(windowEvent + 20));
                }
                System.out.println("[Celtium] CELTIUM_SDL_EVENT type=" + type + detail);
            }
            return true;
        });
        org.lwjgl.sdl.SDLEvents.SDL_AddEventWatch(filter, 0L);
        System.out.println("[Celtium] CELTIUM_SDL_EVENT watch installed (quit and window events are printed)");
    }
}
