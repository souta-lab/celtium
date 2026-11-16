package org.soutalab.celtium.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.taumc.celeritas.CeleritasVintage;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loading/registering this command does not initialize LWJGL or the Vulkan loader. */
public final class CeltiumCommand extends CommandBase {
    @Override public String getName() { return "celtium"; }
    @Override public String getUsage(ICommandSender sender) { return "/celtium [vulkan]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("Celtium terrain backend: " + TerrainBackendStatus.describe()
                    + ". /celtium vulkan creates a Vulkan device for this GPU;"
                    + " native Vulkan terrain is under development."));
            return;
        }
        if (args.length != 1 || !"vulkan".equals(args[0])) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> vulkan(minecraft, sender));
    }

    /**
     * Creates a Vulkan logical device for the current GL GPU and reports the result. The class is resolved only
     * after an explicit command, so an installation without Vulkan bindings keeps starting and rendering in GL.
     */
    private static void vulkan(Minecraft minecraft, ICommandSender sender) {
        Path output = minecraft.gameDir.toPath().resolve("logs/celtium-vulkan-device.json");
        try {
            Class<?> entry = Class.forName("org.soutalab.celtium.vulkan.VulkanBootstrap");
            String json = withLiveTerrainBackend((String) entry.getMethod("bootstrapJson").invoke(null));
            JsonObject report = new JsonParser().parse(json).getAsJsonObject();
            Files.createDirectories(output.getParent());
            Files.write(output, (json + "\n").getBytes(StandardCharsets.UTF_8));
            JsonObject matched = report.has("matched") && report.get("matched").isJsonObject()
                    ? report.getAsJsonObject("matched") : null;
            String summary = report.get("status").getAsString();
            if (matched != null) {
                summary += " on " + matched.get("name").getAsString()
                        + " (device UUID match: " + matched.get("uuidMatchesOpenGl").getAsString()
                        + ", driver UUID match: " + matched.get("driverUuidMatchesOpenGl").getAsString() + ")";
            }
            sender.sendMessage(new TextComponentString("Celtium Vulkan bootstrap: " + summary
                    + ". Report: " + output + ". Terrain backend: " + TerrainBackendStatus.describe() + "."));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getTargetException() : error;
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            CeleritasVintage.logger().warn("Celtium Vulkan bootstrap unavailable; the OpenGL renderer remains active", cause);
            sender.sendMessage(new TextComponentString("Celtium Vulkan bootstrap unavailable: "
                    + cause.getClass().getSimpleName() + ". See the client log; terrain backend: "
                    + TerrainBackendStatus.name() + "."));
        }
    }

    /**
     * Fills in the terrain backend this process is actually running, and says where the value came from.
     * <p>
     * The bootstrap report used to carry a compile-time {@code effectiveTerrainBackend=opengl} and
     * {@code nativeTerrainImplemented=false}, which is the opposite of the truth in a GL-less run whose native renderer
     * is drawing the world: measured in the 268-mod E2E leg, where {@code logs/celtium-vulkan-device.json} said
     * {@code opengl} while the same run reported {@code CELTIUM_NATIVE_DEVICE name="AMD Radeon RX 6400"} and presented
     * a world frame. The bootstrap cannot see the renderer; this command runs inside the
     * client and can, so the value is set here and {@code terrainBackendSource} records that it was observed rather
     * than assumed. The acceptance fixture now checks the report against the live backend, not against a constant.
     */
    private static String withLiveTerrainBackend(String json) {
        JsonObject report = new JsonParser().parse(json).getAsJsonObject();
        boolean nativeInstalled = TerrainBackendStatus.nativeInstalled();
        report.addProperty("effectiveTerrainBackend", nativeInstalled ? "vulkan" : "opengl");
        report.addProperty("nativeTerrainImplemented", Boolean.valueOf(nativeInstalled));
        report.addProperty("terrainBackendSource", nativeInstalled
                ? "live renderer: " + TerrainBackendStatus.describe()
                : "no native terrain backend is installed in this process; terrain is drawn by the OpenGL path");
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report);
    }
}