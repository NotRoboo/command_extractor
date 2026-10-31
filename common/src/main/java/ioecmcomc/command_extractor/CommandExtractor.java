package ioecmcomc.command_extractor;

import static net.minecraft.commands.Commands.literal;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.base.Suppliers;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;

import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.chat.Component;

public class CommandExtractor {
    public static final String MOD_ID = "command_extractor";
    // We can use this if we don't want to use DeferredRegister
    public static final Supplier<RegistrarManager> REGISTRIES = Suppliers.memoize(() -> RegistrarManager.get(MOD_ID));
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void init() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registry, selection) -> {
            dispatcher.register(
                    literal("cmdextr")
                            .then(literal("all")
                                    .executes(ctx -> {
                                        return extractCommands(ctx, true);
                                    }))
                            .executes(ctx -> {
                                return extractCommands(ctx, false);
                            }));
        });

        if (Platform.getEnvironment() == Env.CLIENT) {
            ClientCommandExtractor.init();
        }

        LOGGER.info(PlatformSpecific.getConfigDirectory().toAbsolutePath().normalize().toString());
    }

    /**
     * Extract currently avaliable commands from the game to a file.
     * 
     * @param ctx     the command context
     * @param keepAll whether to keep the "cmdextr" command in the output file
     * @return 0 if succeeded, 1 if failed
     */
    private static int extractCommands(CommandContext<CommandSourceStack> ctx, boolean keepAll) {
        sendSystemMessage(ctx, "Extracting commands...");

        try {
            Commands commands = ctx.getSource().getServer().getCommands();
            CommandDispatcher<CommandSourceStack> disp = commands.getDispatcher();

            List<String> skipped = new ArrayList<>();
            JsonObject obj = serializeTree(disp, keepAll ? null : "cmdextr", skipped);
            try {
                String path = writeJson(obj, "commands.json");
                sendSystemMessage(ctx, "Extracted commands to '" + path + "'" + describeSkipped(skipped));
                return Command.SINGLE_SUCCESS;
            } catch (IOException e) {
                sendSystemMessage(ctx, "Can not create file to extract commands: " + e.getLocalizedMessage());
                LOGGER.error("Stack trace:", e);
                return 0;
            }
        } catch (Exception e) {
            sendSystemMessage(ctx, "Can not extract commands: " + e.getLocalizedMessage());
            LOGGER.error("Stack trace:", e);
            return 0;
        }
    }

    static <S> JsonObject serializeTree(CommandDispatcher<S> disp, String excluded, List<String> skipped) {
        JsonObject children = new JsonObject();
        for (CommandNode<S> node : disp.getRoot().getChildren()) {
            if (node.getName().equals(excluded)) {
                continue;
            }
            try {
                children.add(node.getName(), ArgumentUtils.serializeNodeToJson(disp, node));
            } catch (Exception e) {
                skipped.add(node.getName());
                LOGGER.warn("Skipped command '{}': {}", node.getName(), e.getLocalizedMessage());
            }
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "root");
        obj.add("children", children);
        return obj;
    }

    static String describeSkipped(List<String> skipped) {
        if (skipped.isEmpty()) {
            return "";
        }
        return " (skipped " + skipped.size() + " unsupported: " + String.join(", ", skipped) + ")";
    }

    static String writeJson(JsonObject obj, String fileName) throws IOException {
        Path dir = Path.of("extracted").toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileName);
        try (Writer writer = Files.newBufferedWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(obj, writer);
        }
        return file.toString();
    }

    private static void sendSystemMessage(CommandContext<CommandSourceStack> ctx, String message) {
        ctx.getSource().sendSystemMessage(Component.empty().append(message));
    }
}
