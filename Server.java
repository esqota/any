
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {

    private ArrayList<ConnectionHandler> connections;
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;
    private TreeMap<Integer, ChatRoom> chats;
    private int nextChatRoomID;

    public Server() {
        connections = new ArrayList<>();
        done = false;
        chats = new TreeMap<>();
        nextChatRoomID = 0;
    }

    @Override
    public void run() {
        try {
            server = new ServerSocket(9999);
            pool = Executors.newCachedThreadPool();
            while (!done) {
                Socket client = server.accept();
                ConnectionHandler handler = new ConnectionHandler(client);
                if (connections.size() < 4) {
                    connections.add(handler);
                    pool.execute(handler);
                } else {
                    PrintWriter out = new PrintWriter(client.getOutputStream(), true);
                    out.println("Server full at this time, try again later.");
                    client.close();
                }

            }
        } catch (Exception e) {
            shutdown();
        }
    }

    public void broadcast(String message) {
        for (ConnectionHandler ch : connections) {
            if (ch != null) {
                ch.sendMessage(message);
            }
        }
    }

    public void shutdown() {
        try {
            done = true;
            pool.shutdown();
            if (!server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            //ignore
        }

    }

    public void createChatRoom(String message, ConnectionHandler handler) {
        String name = "";
        String[] messageSplit = message.split(" ", 2);
        if (messageSplit.length == 2 && messageSplit[1].length() > 0) {
            name = messageSplit[1];
            ChatRoom chatRoom = new ChatRoom(name);
            chatRoom.addMember(handler.getNickname());
            chatRoom.addOut(handler.getOut());
            chats.put(nextChatRoomID, chatRoom);
            handler.sendMessage("You have created a new chat room with ID " + nextChatRoomID);
            handler.joinedChatRooms.add(nextChatRoomID);
            nextChatRoomID++;
        } else {
            handler.sendMessage("Error: invalid /create command format. Use /create [chat room name]");
        }
    }

    public void joinChatRoom(String message, ConnectionHandler handler) {
        int chatRoomID;
        String[] messageSplit = message.split(" ", 2);
        if (messageSplit.length == 2 && messageSplit[1].length() > 0) {
            chatRoomID = Integer.parseInt(messageSplit[1]);
            ChatRoom chatRoom = chats.get(chatRoomID);
            if (chatRoom == null) {
                handler.sendMessage("Chat room with ID " + chatRoomID + " does not exist");
            } else {
                //chatRoom.showMembers();
                if (chatRoom.checkMembers(handler.nickname)) {
                    handler.sendMessage("You are a member of this chat " + chatRoomID + " already!");
                } else {
                    chatRoom.addMember(handler.getNickname());
                    chatRoom.addOut(handler.getOut());
                    handler.sendMessage("You have joined chat room " + chatRoomID);
                    handler.joinedChatRooms.add(chatRoomID);
                }
            }
        } else {
            handler.sendMessage("Error: invalid /join command format. Use /join [chat room id]");
        }
    }

    public void leaveChatRoom(String message, ConnectionHandler handler) {
        int chatRoomID;
        String[] messageSplit = message.split(" ", 2);
        if (messageSplit.length == 2 && messageSplit[1].length() > 0) {
            chatRoomID = Integer.parseInt(messageSplit[1]);
            ChatRoom chatRoom = chats.get(chatRoomID);
            if (chatRoom == null) {
                handler.sendMessage("Chat room with ID " + chatRoomID + " does not exist");
            } else {
                //chatRoom.showMembers();
                if (chatRoom.checkMembers(handler.nickname)) {
                    //exist and want out
                    chatRoom.removeMember(handler.getNickname());
                    chatRoom.removeOut(handler.getOut());
                    handler.sendMessage("You have leaved chat room " + chatRoomID);
                    int index = handler.joinedChatRooms.indexOf(chatRoomID);
                    handler.joinedChatRooms.remove(chatRoomID);
                } else {
                    //member not exist 
                    handler.sendMessage("Can't leave chat id " + chatRoomID + "  not member of it!");
                }
            }
        } else {
            handler.sendMessage("Error: invalid /leave command format. Use /leave [chat room id]");
        }
    }

    class ConnectionHandler implements Runnable {

        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        private ArrayList<Integer> joinedChatRooms;

        public ConnectionHandler(Socket client) {
            this.client = client;
            joinedChatRooms = new ArrayList<>();
        }

        @Override
        public void run() {
            try {

                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out.println("please enter a nickname: ");
                nickname = in.readLine();
                System.out.println(nickname + " connected!");
                broadcast(nickname + " joined the chat!");
                String message;
                while ((message = in.readLine()) != null) {

                    if (message.startsWith("/nick")) {
                        //handel nickname
                        changeNick(message);
                    } else if (message.startsWith("/to")) {
                        // handle private message
                        sendPrivate(message);
                    } else if (message.startsWith("/chat")) {
                        // handle group chat
                        groupChat(message);
                    } else if (message.startsWith("/create")) {
                        // handle creating a new chat room
                        createChatRoom(message, this);
                    } else if (message.startsWith("/join")) {
                        // handle joining an existing chat room
                        joinChatRoom(message, this);
                    } else if (message.startsWith("/leave")) {
                        // handle joining an existing chat room
                        leaveChatRoom(message, this);
                    } else if (message.startsWith("/quit")) {
                        quitchat();
                    } else {
                        broadcast(nickname + ": " + message);
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        public void changeNick(String message) {
            String[] messageSplit = message.split(" ", 2);
            if (messageSplit.length == 2 && messageSplit[1].length() > 0) {
                broadcast(nickname + " renamed themselves to " + messageSplit[1]);
                System.out.println(nickname + " renamed themselves to " + messageSplit[1]);
                nickname = messageSplit[1];
                out.println("Successfully changed nickname to " + nickname);
            } else {
                out.println("no nickname provided!");
            }
        }

        public void sendPrivate(String message) {
            String[] messageSplit = message.split(" ", 3);
            if (messageSplit.length == 3 && messageSplit[1].length() > 0 && messageSplit[2].length() > 0) {
                String recipient = messageSplit[1];
                String msg = messageSplit[2];
                boolean found = false;
                for (ConnectionHandler ch : connections) {
                    if (ch != null && ch.nickname.equals(recipient)) {
                        ch.sendMessage("[PM from " + nickname + "]: " + msg);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    out.println("Error: recipient " + recipient + " not found.");
                }
            } else {
                out.println("Error: invalid /to command format. Use /to [recipient] [message]");
            }
        }

        public void groupChat(String message) {
            String[] messageSplit = message.split(" ", 3);
            if (messageSplit.length == 3 && messageSplit[1].length() > 0 && messageSplit[2].length() > 0) {
                int chatRoomID = Integer.parseInt(messageSplit[1]);
                ChatRoom chatRoom = chats.get(chatRoomID);
                if (chatRoom == null) {
                    out.println("Chat room with ID " + chatRoomID + " does not exist");
                } else if (!joinedChatRooms.contains(chatRoomID)) {
                    out.println("You are not a member of chat room " + chatRoomID);
                } else {
                    chatRoom.broadcast(nickname + ": " + messageSplit[2]);
                }
            } else {
                out.println("Error: invalid /chat command format. Use /chat [chat room ID] [message]");
            }
        }

        public void quitchat() {
            for (int chatRoomID : joinedChatRooms) {
                ChatRoom chatRoom = chats.get(chatRoomID);
                if (chatRoom != null) {
                    chatRoom.removeMember(nickname);
                    chatRoom.removeOut(out);
                }
            }
            broadcast(nickname + " left the chat!");
            shutdown();
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                in.close();
                out.close();
                if (!client.isClosed()) {
                    client.close();
                }
                //remove client from connections
                for (ConnectionHandler ch : connections) {
                    if (ch != null && ch.nickname.equals(nickname)) {
                        System.out.println(nickname + " disconnected!");
                        connections.remove(ch);
                        break;
                    }
                }
            } catch (IOException e) {
                //ignore
            }

        }

        public String getNickname() {
            return nickname;
        }

        public PrintWriter getOut() {
            return out;
        }

    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
    }
}

class ChatRoom {

    private String name;
    private ArrayList<String> members;
    private ArrayList<PrintWriter> membersOut;

    public ChatRoom(String name) {
        this.name = name;
        this.members = new ArrayList<>();
        this.membersOut = new ArrayList<>();
    }

    public void addMember(String nickname) {
        members.add(nickname);
    }

    public void removeMember(String nickname) {
        int index = members.indexOf(nickname);
        if (index >= 0) {
            members.remove(index);
            membersOut.remove(index);
            broadcast(nickname + " has left the chat.");
        }
    }

    public void broadcast(String message) {
        for (PrintWriter out : membersOut) {
            out.println("[" + name + "]" + message);
        }
    }

    public void addOut(PrintWriter out) {
        membersOut.add(out);
    }

    public void removeOut(PrintWriter out) {
        membersOut.remove(out);
    }

    public void showMembers() {
        for (String mem : members) {
            System.out.println(mem);
        }
    }

    public boolean checkMembers(String nickname) {
        return members.contains(nickname);
    }

    public void shutdown() {
        try {
            for (PrintWriter out : membersOut) {
                out.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
