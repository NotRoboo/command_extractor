package ioecmcomc.command_extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;

import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;

import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ClientCommandExtractor {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String COMMAND_NAME = "cmdextrc";

    public static void init() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, context) -> {
            dispatcher.register(
                    ClientCommandRegistrationEvent.literal(COMMAND_NAME)
                            .then(ClientCommandRegistrationEvent.literal("all")
                                    .executes(ctx -> extract(true)))
                            .executes(ctx -> extract(false)));
        });
    }

    private static int extract(boolean keepAll) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            sendMessage("Join a world or server first.");
            return 0;
        }

        try {
            CommandDispatcher<ClientSuggestionProvider> disp = connection.getCommands();
            List<String> skipped = new ArrayList<>();
            JsonObject obj = CommandExtractor.serializeTree(disp, keepAll ? null : COMMAND_NAME, skipped);
            String path = CommandExtractor.writeJson(obj, "server_commands.json");
            sendMessage("Extracted server commands to '" + path + "'" + CommandExtractor.describeSkipped(skipped));
            return Command.SINGLE_SUCCESS;
        } catch (IOException e) {
            sendMessage("Can not create file to extract commands: " + e.getLocalizedMessage());
            LOGGER.error("Stack trace:", e);
        } catch (Exception e) {
            sendMessage("Can not extract commands: " + e.getLocalizedMessage());
            LOGGER.error("Stack trace:", e);
        }
        return 0;
    }

    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), false);
        }
    }
}
