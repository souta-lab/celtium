package org.soutalab.celtium.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
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
    @Override public String getUsage(ICommandSender sender) { return "/celtium [probe|vulkan]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("Celtium terrain backend: " + TerrainBackendStatus.describe()
                    + ". /celtium probe writes a capability report, /celtium vulkan creates a Vulkan device for this GPU;"
                    + " native Vulkan terrain is under development."));
            return;
        }
        if (args.length != 1 || (!"probe".equals(args[0]) && !"vulkan".equals(args[0]))) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if ("probe".equals(args[0])) {
            minecraft.addScheduledTask(() -> probe(minecraft, sender));
        } else {
            minecraft.addScheduledTask(() -> vulkan(minecraft, sender));
        }
    }

    /**
     * Creates a Vulkan logical device for the current GL GPU and reports the result. The class is resolved only
     * after an explicit command, so an installation without Vulkan bindings keeps starting and rendering in GL.
     */
    private static void vulkan(Minecraft minecraft, ICommandSender sender) {
        Path output = minecraft.gameDir.toPath().resolve("logs/celtium-vulkan-device.json");
        try {
            Class<?> entry = Class.forName("org.soutalab.celtium.vulkan.VulkanBootstrap");
            String json = (String) entry.getMethod("bootstrapJson").invoke(null);
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
                    + ". Report: " + output + ". Terrain still renders with OpenGL."));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getTargetException() : error;
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            CeleritasVintage.logger().warn("Celtium Vulkan bootstrap unavailable; the OpenGL renderer remains active", cause);
            sender.sendMessage(new TextComponentString("Celtium Vulkan bootstrap unavailable: "
                    + cause.getClass().getSimpleName() + ". See the client log; terrain remains OpenGL."));
        }
    }

    private static void probe(Minecraft minecraft, ICommandSender sender) {
        try {
            // Optional binding classes are resolved only after an explicit command, on the render thread.
            // Stock Forge/LWJGL 2 or older relocated LWJGL setups can keep using the existing GL backend.
            Class<?> entry = Class.forName("org.soutalab.celtium.probe.CeltiumProbe");
            String json = (String) entry.getMethod("inspectCurrentContextJson").invoke(null);
            Path output = minecraft.gameDir.toPath().resolve("logs/celtium-capabilities.json");
            Files.createDirectories(output.getParent());
            Files.write(output, (json + "\n").getBytes(StandardCharsets.UTF_8));
            sender.sendMessage(new TextComponentString("Celtium capability report: " + output + ". This does not verify shared depth or enable Vulkan terrain."));
        } catch (ReflectiveOperationException | java.io.IOException | RuntimeException | LinkageError error) {
            Throwable cause = error instanceof InvocationTargetException ? ((InvocationTargetException) error).getTargetException() : error;
            if (cause instanceof VirtualMachineError) throw (VirtualMachineError) cause;
            if (cause instanceof ThreadDeath) throw (ThreadDeath) cause;
            CeleritasVintage.logger().warn("Celtium capability probe failed; the OpenGL renderer remains active", cause);
            sender.sendMessage(new TextComponentString("Celtium probe unavailable: " + cause.getClass().getSimpleName() + ". See the client log; terrain remains OpenGL."));
        }
    }
}
