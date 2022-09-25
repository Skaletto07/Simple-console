package nio.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public class Terminal {

    private Path current;
    private ServerSocketChannel server;
    private Selector selector;
    private String dir = "/home/vitaly";
    private ByteBuffer buf;

    public Terminal() throws IOException {
        current = Path.of(dir);
        buf = ByteBuffer.allocate(256);
        server = ServerSocketChannel.open();
        selector = Selector.open();
        server.bind(new InetSocketAddress(8189));
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        while (server.isOpen()) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = keys.iterator();
            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                keyIterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        buf.clear();
        StringBuilder sb = new StringBuilder();

        while (true) {
            int read = channel.read(buf);
            if (read == 0) {
                break;
            }
            if (read == -1) {
                channel.close();
                return;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                sb.append((char) buf.get());
            }
            buf.clear();
        }
        System.out.println("Received: " + sb);
        String s = new String(sb);
        String[] splitWords = s.split(" ");
        String command = splitWords[0].trim();
        if (command.equals("ls")) {
            if (Files.exists(current)) {
                String files = Files.list(current)
                        .map(p -> p.getFileName().toString())
                        .collect(Collectors.joining("\n\r"));
                channel.write(ByteBuffer.wrap(files.getBytes(StandardCharsets.UTF_8)));
            }
        } else if (command.equals("cd")) {
            if (splitWords[1].trim().equals("..")) {
                current = Path.of(current.getParent().toUri());
                channel.write(ByteBuffer.wrap((current + "$").getBytes()));
            } else {
                current = Path.of(current + "/" + splitWords[1].trim());
                if (Files.exists(current)) {
                    channel.write(ByteBuffer.wrap((current + "$").getBytes()));
                } else channel.write(ByteBuffer.wrap("Something wrong".getBytes()));
            }

        } else if (command.equals("touch")) {
            Path newFile = Path.of(current + "/" + splitWords[1].trim());
            if (!Files.exists(newFile)) {
                Files.createFile(newFile);
            }
        } else if (command.equals("mkdir")) {
            Path newDir = Path.of(current + "/" + splitWords[1].trim());
            if (!Files.exists(newDir)) {
                Files.createDirectory(newDir);
            }
        } else if (command.equals("cat")) {
            Path fileForRead = Path.of(current + "/" + splitWords[1].trim());
            String file = Files.readString(fileForRead);
            channel.write(ByteBuffer.wrap(file.getBytes()));
        } else {
            byte[] bytes = command.getBytes(StandardCharsets.UTF_8);
            channel.write(ByteBuffer.wrap(bytes));
        }
    }


    private void handleAccept() throws IOException {
        SocketChannel socketChannel = server.accept();
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        System.out.println("Client accepted");
    }

    public static void main(String[] args) throws IOException {
        new Terminal();


    }

}