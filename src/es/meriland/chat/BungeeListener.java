package es.meriland.chat;

import java.io.*;
import java.util.logging.Level;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.UUID;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.event.EventPriority;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;

public class BungeeListener implements Listener {

    private final MeriChat plugin;

    public BungeeListener(MeriChat instance) {
        plugin = instance;
    }

    public void processChat(ProxiedPlayer player, String mensaje) {
        if (plugin.chatsActivados.containsKey(player.getUniqueId())) {
            ProxiedPlayer chat = plugin.getProxy().getPlayer(plugin.chatsActivados.get(player.getUniqueId()));
            sendPrivateMessage(chat, player, mensaje.split(MeriChat.CHAR)[1]);
        } else {
            sendChat(player, mensaje.replace(MeriChat.CHAR, ""));
        }
    }

    public void sendChat(ProxiedPlayer from, String mensaje) {
        try {
            BaseComponent[] msg = Parser.parse(mensaje);

            for (ProxiedPlayer target : plugin.getProxy().getPlayers()) {
                if (plugin.ignoredPlayers.get(target.getUniqueId()) != null && plugin.ignoredPlayers.get(target.getUniqueId()).contains(from.getUniqueId()))
                    continue;

                target.sendMessage(msg);
            }
        } catch (Throwable th) {
            from.sendMessage(Parser.parse("&cError interno procesando el mensaje."));
            plugin.getLogger().log(Level.SEVERE, "Error procensando el mensaje", th);
        }
    }

    public void processPrivateMsg(String targetS, String fromS, String mensaje) {
        ProxiedPlayer from = plugin.getProxy().getPlayer(fromS);
        if (from == null) return;

        if (targetS.equals("")) { //terminar chat privado
            if (!checkEndChat(from)) {
                from.sendMessage(Parser.parse(c("&dUsa /tell <Usuario> <Mensaje>!")));
            }
            return;
        }

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetS);
        if (targetS.equals(MeriChat.CHAR + "reply")) {
            UUID targetId = plugin.replyTarget.get(from.getUniqueId());
            if(targetId == null){
                from.sendMessage(Parser.parse(c("¡Nadie te ha enviado un mensaje! :(")));
                return;
            }
            target = plugin.getProxy().getPlayer(targetId);
        }

        if (target == null) {
            from.sendMessage(Parser.parse(c("&c¡Jugador no encontrado!")));
            return;
        }

        if (plugin.ignoredPlayers.get(from.getUniqueId()) != null && plugin.ignoredPlayers.get(from.getUniqueId()).contains(target.getUniqueId())) {
            from.sendMessage(Parser.parse(c("&c¡No puedes hablar a un usuario al que has ignorado!")));
            return;
        }

        if (mensaje.equals("")) { //Iniciar chat con target
            if (checkEndChat(from)) return;
            plugin.chatsActivados.put(from.getUniqueId(), target.getUniqueId());
            from.sendMessage(Parser.parse("Has iniciado una conversación privada con "+target.getName()));
            return;
        }
        sendPrivateMessage(target, from, mensaje);
    }

    public boolean checkEndChat(ProxiedPlayer p) {
        if (plugin.chatsActivados.containsKey(p.getUniqueId())) {
            ProxiedPlayer oldChat = plugin.getProxy().getPlayer(plugin.chatsActivados.get(p.getUniqueId()));
            plugin.chatsActivados.remove(p.getUniqueId());
            p.sendMessage(Parser.parse("Has terminado tu chat con " + oldChat.getName()));
            return true;
        }
        return false;
    }

    public void sendPrivateMessage(ProxiedPlayer target, ProxiedPlayer from, String mensaje) {
        from.sendMessage(Parser.parse(c("&6A " + target.getName() + ": &d" + mensaje)));

        if (plugin.ignoredPlayers.get(target.getUniqueId()) != null && plugin.ignoredPlayers.get(target.getUniqueId()).contains(from.getUniqueId())) return;

        target.sendMessage(Parser.parse(c("&6De " + from.getName() + ": &d" + mensaje)));
        plugin.setReply(target.getUniqueId(), from.getUniqueId());
    }

    public void processIgnore(String targetS, String fromS) {
        ProxiedPlayer from = plugin.getProxy().getPlayer(fromS);
        if (from == null) return;

        ProxiedPlayer target = plugin.getProxy().getPlayer(targetS);
        if (target == null) {
            from.sendMessage(Parser.parse(c("&c¡Jugador no encontrado!")));
            return;
        }

        if (target.getGroups().contains("admin") || target.getGroups().contains("tecnico") || target.getGroups().contains("moderador")) {
            from.sendMessage(Parser.parse(c("&c¡No puedes ignorar a alguien del staff!")));
            return;
        }

        ArrayList<UUID> ignorados = plugin.ignoredPlayers.get(from.getUniqueId());
        if (ignorados == null) {
            ignorados = new ArrayList<>(1);
        }

        if (ignorados.contains(target.getUniqueId())) { //Eliminar ignore
            ignorados.remove(target.getUniqueId());
            from.sendMessage(Parser.parse(c("&aYa no ignoras a " + target.getName())));
        } else {
            ignorados.add(target.getUniqueId());
            from.sendMessage(Parser.parse(c("&aAhora ignoras a " + target.getName())));
            plugin.ignoredPlayers.put(from.getUniqueId(), ignorados);
        }
        try {
            plugin.saveIgnoredConf();
        } catch (IOException ex) {}
    }

    public void processIgnoreList(String pS) {
        ProxiedPlayer p = plugin.getProxy().getPlayer(pS);
        if (p == null) return;

        ArrayList<UUID> ignorados = plugin.ignoredPlayers.get(p.getUniqueId());
        if (ignorados == null || ignorados.isEmpty()) {
            p.sendMessage(Parser.parse(c("&aNo has ignorado a nadie")));
            p.sendMessage(Parser.parse(c("&aUsa &e/ignore <usuario> &apara hacerlo")));
            return;
        }

        String usuarios = "";
        for (UUID val : ignorados) {
            usuarios += plugin.getProxy().getPlayer(val).getName() + ", ";
        }

        if (!"".equals(usuarios)) usuarios = usuarios.substring(0, usuarios.length() - 2);

        p.sendMessage(Parser.parse(c("&aUsuarios que están actualmente ignorados:")));
        p.sendMessage(Parser.parse(c("&e" + usuarios)));
    }
    /*
     * Recibir los packets de Bukkit con rangos y demás datos
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPluginMessage(PluginMessageEvent event) {
        if (event.getTag().equals(MeriChat.MAIN_CHANNEL)) {
            event.setCancelled(true);
            if (event.getReceiver() instanceof ProxiedPlayer && event.getSender() instanceof Server) {
                try {
                    ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();

                    DataInputStream in = new DataInputStream(new ByteArrayInputStream(event.getData()));
                    String subchannel = in.readUTF();
                    switch (subchannel) {
                        case MeriChat.MAIN_SUBCHANNEL:
                            processChat(player, in.readUTF());
                            break;
                        case MeriChat.PRIVATE_SUBCHANNEL:
                            processPrivateMsg(in.readUTF(), in.readUTF(), in.readUTF());
                            break;
                        case MeriChat.IGNORE_SUBCHANNEL:
                            processIgnore(in.readUTF(), in.readUTF());
                            break;
                        case MeriChat.IGNLIST_SUBCHANNEL:
                            processIgnoreList(in.readUTF());
                            break;
                    }
                } catch (IOException ex) {
                    plugin.getLogger().log(Level.SEVERE, "Error obtieniendo datos de Bukkit", ex);
                }
            }
        }
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer p = event.getPlayer();
        if (plugin.chatsActivados.containsKey(p.getUniqueId())) plugin.chatsActivados.remove(p.getUniqueId());
        if (plugin.replyTarget.containsKey(p.getUniqueId())) plugin.replyTarget.remove(p.getUniqueId());
    }

    public String c(String str) {
        return ChatColor.translateAlternateColorCodes('&', str);
    }
}
