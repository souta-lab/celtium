package org.soutalab.celtium.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.taumc.celeritas.CeleritasVintage;

import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loading/registering this command does not initialize LWJGL or the Vulkan loader. */
public final class CeltiumCommand extends CommandBase {
    @Override public String getName() { return "celtium"; }
    @Override public String getUsage(ICommandSender sender) { return "/celtium [probe]"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString("Celtium: OpenGL terrain backend. /celtium probe writes a capability report; native Vulkan terrain is under development."));
            return;
        }
        if (args.length != 1 || !"probe".equals(args[0])) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> probe(minecraft, sender));
    }

    private static void probe(Minecraft minecraft, ICommandSender sender) {
        try {
            // Optional binding classes are resolved only after an explicit command, on the render thread.
            // Stock Forge/LWJGL 2 or older relocated LWJGL setups can keep using the existing GL backend.
            Class<?> entry = Class.forName("org.soutalab.celtium.probe.CeltiumProbe");
            String json = (String) entry.getMethod("inspectCurrentContextJson").invoke(null);
            Path output = minecraft.mcDataDir.toPath().resolve("logs/celtium-capabilities.json");
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
