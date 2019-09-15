package de.erdbeerbaerlp.dcintegration;

import de.erdbeerbaerlp.dcintegration.commands.DiscordCommand;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.*;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.exceptions.PermissionException;
import net.dv8tion.jda.core.hooks.EventListener;
import net.dv8tion.jda.core.managers.ChannelManager;
import net.dv8tion.jda.core.requests.RequestFuture;
import net.dv8tion.jda.core.utils.PermissionUtil;
import net.dv8tion.jda.webhook.WebhookClient;
import net.dv8tion.jda.webhook.WebhookMessageBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.util.text.event.HoverEvent.Action;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

@SuppressWarnings("ConstantConditions")
public class Discord implements EventListener {
    private final JDA jda;
    private final List<DiscordCommand> commands = new ArrayList<>();
    public boolean isKilled = false;
    /**
     * This thread is used to update the channel description
     */
    Thread updateChannelDesc = new Thread() {
        {
            this.setName("[DC INTEGRATION] Channel Description Updater");
            this.setDaemon(false);
            this.setPriority(MAX_PRIORITY);
        }

        private double getAverageTickCount() {
            MinecraftServer minecraftServer = ServerLifecycleHooks.getCurrentServer();
            //noinspection IntegerDivisionInFloatingPointContext
            return LongStream.of(minecraftServer.tickTimeArray).sum() / minecraftServer.tickTimeArray.length * 1.0E-6D;
        }

        private double getAverageTPS() {
            return Math.min(1000.0 / getAverageTickCount(), 20);
        }

        public void run() {
            try {
                while (true) {
                    getChannelManager().setTopic(
                            Configuration.INSTANCE.description.get()
                                    .replace("%tps%", "" + Math.round(getAverageTPS()))
                                    .replace("%online%", "" + ServerLifecycleHooks.getCurrentServer().getOnlinePlayerNames().length)
                                    .replace("%max%", "" + ServerLifecycleHooks.getCurrentServer().getMaxPlayers())
                                    .replace("%motd%", ServerLifecycleHooks.getCurrentServer().getMOTD())
                                    .replace("%uptime%", DiscordIntegration.getUptime())
                    ).complete();
                    sleep(500);
                }
            } catch (InterruptedException | RuntimeException ignored) {

            }
        }
    };

    /**
     * Constructor for this class
     */
    Discord() throws LoginException, InterruptedException {
        final JDABuilder b = new JDABuilder(Configuration.INSTANCE.botToken.get());
        switch (Configuration.INSTANCE.botPresenceType.get()) {
            case DISABLED:
                break;
            case LISTENING:
                b.setGame(Game.listening(Configuration.INSTANCE.botPresenceName.get()));
                break;
            case PLAYING:
                b.setGame(Game.playing(Configuration.INSTANCE.botPresenceName.get()));
                break;
            case WATCHING:
                b.setGame(Game.watching(Configuration.INSTANCE.botPresenceName.get()));
                break;
        }
        this.jda = b.build().awaitReady();
        System.out.println("Bot Ready");
        jda.addEventListener(this);
        if (!PermissionUtil.checkPermission(getChannel(), getChannel().getGuild().getMember(jda.getSelfUser()), Permission.MESSAGE_READ, Permission.MESSAGE_WRITE, Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_MANAGE)) {
            System.err.println("ERROR! Bot does not have all permissions to work!");
            throw new PermissionException("Bot requires message read, message write, embed links and manage messages");
        }
        if (Configuration.INSTANCE.botModifyDescription.get())
            if (!PermissionUtil.checkPermission(getChannel(), getChannel().getGuild().getMember(jda.getSelfUser()), Permission.MANAGE_CHANNEL)) {
                Configuration.setValueAndSave(DiscordIntegration.cfg, Configuration.INSTANCE.botModifyDescription.getPath(), false);
                System.err.println("ERROR! Bot does not have permission to manage channel, disabling channel description");
            }
        if (Configuration.INSTANCE.enableWebhook.get())
            if (!PermissionUtil.checkPermission(getChannel(), getChannel().getGuild().getMember(jda.getSelfUser()), Permission.MANAGE_WEBHOOKS)) {
                Configuration.setValueAndSave(DiscordIntegration.cfg, Configuration.INSTANCE.enableWebhook.getPath(), false);
                System.err.println("ERROR! Bot does not have permission to manage webhooks, disabling webhook");
            }
    }
    /*/*
     * This thread is used to detect auto shutdown status using ftb utilities
     *//*
	Thread ftbUtilitiesShutdownDetectThread = new Thread() {
		{
			setName("[DC INTEGRATION] FTB Utilities shutdown detector");
			setDaemon(true);
			setPriority(MAX_PRIORITY);
		}

		public void run() {
			while (!isKilled) {
				final long timeLeft = TimeUnit.MILLISECONDS.toSeconds(FTBUtilitiesUniverseData.shutdownTime - Instant.now().toEpochMilli());
				if (timeLeft == 120)
					sendMessage(Configuration.FTB_UTILITIES.SHUTDOWN_MSG_2MINUTES, Configuration.FTB_UTILITIES.FTB_AVATAR_ICON, "FTB Utilities", null);
				else if (timeLeft == 10) {
					sendMessage(Configuration.FTB_UTILITIES.SHUTDOWN_MSG_10SECONDS, Configuration.FTB_UTILITIES.FTB_AVATAR_ICON, "FTB Utilities", null);
					break;
				}

				try {
					sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException ignored) {
                }
			}
			interrupt();
		}
	};
	/**
	 * This thread is used to detect AFK states using ftb utilities
	 *//*
	Thread ftbUtilitiesAFKDetectThread = new Thread() {
		{
			setName("[DC INTEGRATION] FTB Utilities AFK detector");
			setDaemon(true);
			setPriority(MAX_PRIORITY);
		}

		public void run() {
			if (!Configuration.FTB_UTILITIES.DISCORD_AFK_MSG_ENABLED) return;
            final Map<EntityPlayerMP, Entry<Long, Boolean>> timers = new HashMap<>();
			final Universe universe = Universe.get();
			while (!isKilled) {
				for (EntityPlayerMP player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                    try {
                        final FTBUtilitiesPlayerData data = FTBUtilitiesPlayerData.get(Objects.requireNonNull(universe.getPlayer(player)));
						if (timers.containsKey(player) && data.afkTime < timers.get(player).getKey() && timers.get(player).getValue())
							sendMessage(Configuration.FTB_UTILITIES.DISCORD_AFK_MSG_END.replace("%player%", player.getName()), Configuration.FTB_UTILITIES.FTB_AVATAR_ICON, "FTB Utilities", null);
                        timers.put(player, new SimpleEntry<>(data.afkTime, (timers.containsKey(player) ? timers.get(player).getValue() : false)));
                    } catch (NullPointerException ignored) {
                    }
                }
                final List<EntityPlayerMP> toRemove = new ArrayList<>();
				timers.keySet().forEach((p) -> {
					if (!ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().contains(p)) {
						toRemove.add(p);
					} else {
						final boolean afk = timers.get(p).getKey() >= Ticks.get(FTBUtilitiesConfig.afk.notification_timer).millis();
						if (afk && !timers.get(p).getValue()) sendMessage(Configuration.FTB_UTILITIES.DISCORD_AFK_MSG
								.replace("%player%", p.getName()), Configuration.FTB_UTILITIES.FTB_AVATAR_ICON, "FTB Utilities", null);
                        timers.put(p, new SimpleEntry<>(timers.get(p).getKey(), afk));

					}
				});
				for (EntityPlayerMP p : toRemove) {
					timers.remove(p);
				}
				try {
					sleep(900);
                } catch (InterruptedException ignored) {
                }
			}
		}
	};
*/

    /**
     * Sends a message when *not* using a webhook and returns it as RequestFuture<Message> or null when using a webhook
     *
     * @param msg message
     * @return Sent message
     */
    public RequestFuture<Message> sendMessageReturns(String msg) {
        if (Configuration.INSTANCE.enableWebhook.get())
            return null;
        else
            return getChannel().sendMessage(msg).submit();
    }

    /**
     * Sends a message as player
     *
     * @param player Player
     * @param msg    Message
     */
    public void sendMessage(PlayerEntity player, String msg) {
        sendMessage(player.getName().getUnformattedComponentText(), player.getUniqueID().toString(), msg);
    }

    /**
     * Sends a message as server
     *
     * @param msg Message
     */
    public void sendMessage(String msg) {
        sendMessage(Configuration.INSTANCE.serverName.get(), "0000000", msg);
    }

    /**
     * Sends a message to discord with custom avatar url (when using a webhook)
     *
     * @param msg       Message
     * @param avatarURL URL of the avatar image
     * @param name      Name of the fake player
     * @param nullMe    Just pass null, required to differ from a different method
     */
    public void sendMessage(String msg, String avatarURL, String name, @Nullable Void nullMe) {
        if (isKilled) return;
        try {
            if (Configuration.INSTANCE.enableWebhook.get()) {
                final WebhookMessageBuilder b = new WebhookMessageBuilder();
                b.setContent(msg);
                b.setUsername(name);
                b.setAvatarUrl(avatarURL);
                final WebhookClient cli = getWebhook().newClient().build();
                cli.send(b.build());
                cli.close();
            } else
                getChannel().sendMessage(
                        Configuration.INSTANCE.msgChatMessage.get()
                                .replace("%player%", name)
                                .replace("%msg%", msg)
                ).complete();
        } catch (Exception e) {

        }
    }

    /**
     * Sends a message to discord
     *
     * @param playerName the name of the player
     * @param UUID       the player uuid
     * @param msg        the message to send
     */
    public void sendMessage(String playerName, String UUID, String msg) {
        if (isKilled) return;
        try {
            if (Configuration.INSTANCE.enableWebhook.get()) {
                if (playerName.equals(Configuration.INSTANCE.serverName.get()) && UUID.equals("0000000")) {
                    final WebhookMessageBuilder b = new WebhookMessageBuilder();
                    b.setContent(msg);
                    b.setUsername(Configuration.INSTANCE.serverName.get());
                    b.setAvatarUrl(Configuration.INSTANCE.serverAvatar.get());
                    final WebhookClient cli = getWebhook().newClient().build();
                    cli.send(b.build());
                    cli.close();
                } else {
                    final WebhookMessageBuilder b = new WebhookMessageBuilder();
                    b.setContent(msg);
                    b.setUsername(playerName);
                    b.setAvatarUrl("https://minotar.net/avatar/" + UUID);
                    final WebhookClient cli = getWebhook().newClient().build();
                    cli.send(b.build());
                    cli.close();
                }
            } else if (playerName.equals(Configuration.INSTANCE.serverName.get()) && UUID.equals("0000000")) {
                getChannel().sendMessage(msg).complete();
            } else {
                getChannel().sendMessage(
                        Configuration.INSTANCE.msgChatMessage.get()
                                .replace("%player%", playerName)
                                .replace("%msg%", msg)
                ).complete();
            }
        } catch (Exception e) {

        }
    }

    /**
     * Kills the discord bot
     */
    void kill() {
        stopThreads();
        this.isKilled = true;
        jda.shutdown();
    }

    /**
     * Event handler to handle messages
     */
    @Override
    public void onEvent(Event event) {
        if (isKilled) return;
        if (event instanceof MessageReceivedEvent) {
            final MessageReceivedEvent ev = (MessageReceivedEvent) event;
            if (getChannel().getId().equals(ev.getChannel().getId()) && !ev.isWebhookMessage() && !ev.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                if (ev.getMessage().getContentRaw().startsWith(Configuration.INSTANCE.prefix.get())) {
                    final String[] command = ev.getMessage().getContentRaw().replaceFirst(Configuration.INSTANCE.prefix.get(), "").split(" ");
                    String argumentsRaw = "";
                    for (int i = 1; i < command.length; i++) {
                        argumentsRaw = argumentsRaw + command[i] + " ";
                    }
                    argumentsRaw = argumentsRaw.trim();
                    boolean hasPermission = true;
                    boolean executed = false;
                    for (DiscordCommand cmd : commands) {

                        if (cmd.getName().equals(command[0])) {
                            if (cmd.canUserExecuteCommand(ev.getAuthor())) {
                                cmd.execute(argumentsRaw.split(" "), ev);
                                executed = true;
                            } else hasPermission = false;

                        }

                        for (String alias : cmd.getAliases()) {
                            if (alias.equals(command[0])) {
                                if (cmd.canUserExecuteCommand(ev.getAuthor())) {
                                    cmd.execute(argumentsRaw.split(" "), ev);
                                    executed = true;
                                } else hasPermission = false;
                            }
                        }
                    }
                    if (!hasPermission) {
                        sendMessage(Configuration.INSTANCE.msgNoPermission.get());
                        return;
                    }
                    if (!executed) {
                        sendMessage(Configuration.INSTANCE.msgUnknownCommand.get().replace("%prefix%", Configuration.INSTANCE.prefix.get()));
                    }

                } else
                    ServerLifecycleHooks.getCurrentServer().getPlayerList().sendMessage(
                            new StringTextComponent(Configuration.INSTANCE.ingameDiscordMsg.get()
                                    .replace("%user%", ev.getAuthor().getName())
                                    .replace("%id%", ev.getAuthor().getId())
                                    .replace("%msg%", ev.getMessage().getContentRaw())).setStyle(new Style().setHoverEvent(new HoverEvent(Action.SHOW_TEXT, new StringTextComponent("Sent by discord user \"" + ev.getAuthor().getAsTag() + "\"")))));
            }

        }
    }

    public ChannelManager getChannelManager() {
        return new ChannelManager(getChannel());
    }

    /**
     * Registers an {@link DiscordCommand}
     *
     * @param cmd command
     * @return if the registration was successful
     */
    public boolean registerCommand(DiscordCommand cmd) {
        final ArrayList<DiscordCommand> toRemove = new ArrayList<>();
        for (DiscordCommand c : commands) {
            if (!cmd.isConfigCommand() && cmd.equals(c)) return false;
            else if (cmd.isConfigCommand() && cmd.equals(c)) toRemove.add(c);
        }
        for (DiscordCommand cm : toRemove)
            commands.remove(cm);
        return commands.add(cmd);
    }
    private void reRegisterAllCommands(final List<DiscordCommand> cmds) {
        System.out.println("Reloading " + cmds.size() + " commands");
        this.commands = cmds;

        for (DiscordCommand cmd : commands) {
            cmd.discord = DiscordIntegration.discord_instance;
        }

        System.out.println("Registered " + this.commands.size() + " commands");
    }
    /**
     * Restarts the discord bot (used by reload command)
     */
    public boolean restart() {
        try {
            kill();
            DiscordIntegration.discord_instance = new Discord();
            DiscordIntegration.discord_instance.reRegisterAllCommands(this.commands);
            DiscordIntegration.registerConfigCommands();
            DiscordIntegration.discord_instance.startThreads();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Starts all threads
     */
    public void startThreads() {
        if (Configuration.GENERAL.MODIFY_CHANNEL_DESCRIPTRION) updateChannelDesc.start();
        if (Loader.isModLoaded("ftbutilities")) {
            if (FTBUtilitiesConfig.auto_shutdown.enabled) ftbUtilitiesShutdownDetectThread.start();
            if (FTBUtilitiesConfig.afk.enabled) ftbUtilitiesAFKDetectThread.start();
        }
    }
    /**
     * @return an instance of the webhook or null
     */
    @Nullable
    public Webhook getWebhook() {
        if (!Configuration.INSTANCE.enableWebhook.get()) return null;
        if (!PermissionUtil.checkPermission(getChannel(), getChannel().getGuild().getMember(jda.getSelfUser()), Permission.MANAGE_WEBHOOKS)) {
            Configuration.setValueAndSave(DiscordIntegration.cfg, Configuration.INSTANCE.enableWebhook.getPath(), false);
            System.out.println("ERROR! Bot does not have permission to manage webhooks, disabling webhook");
            return null;
        }
        for (Webhook web : getChannel().getWebhooks().complete()) {
            if (web.getName().equals("MC_DISCORD_INTEGRATION")) {
                return web;
            }
        }
        return getChannel().createWebhook("MC_DISCORD_INTEGRATION").complete();
    }

    /**
     * Used to stop all discord integration threads in background
     */
    void stopThreads() {
//		if(ftbUtilitiesAFKDetectThread.isAlive()) ftbUtilitiesAFKDetectThread.interrupt();
        if (updateChannelDesc.isAlive()) updateChannelDesc.interrupt();
//		if(ftbUtilitiesShutdownDetectThread.isAlive()) ftbUtilitiesShutdownDetectThread.interrupt();
    }

    /**
     * @return A list of all registered commands
     */
    public List<DiscordCommand> getCommandList() {
        return this.commands;
    }

    /**
     * @return The admin role of the server
     */
    public Role getAdminRole() {
        return (Configuration.INSTANCE.adminRoleId.get().equals("0") || Configuration.INSTANCE.adminRoleId.get().trim().isEmpty()) ? null : jda.getRoleById(Configuration.INSTANCE.adminRoleId.get());
    }

    /**
     * @return the specified text channel
     */
    public TextChannel getChannel() {
        return jda.getTextChannelById(Configuration.INSTANCE.botChannel.get());
    }

    public enum GameTypes {
        WATCHING, PLAYING, LISTENING, DISABLED
    }
}

