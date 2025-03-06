import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.ServerSocket;

class AdministradorTrafico {
    // Now, the proxy receives the following arguments:
    // args[0]: local port to listen for a client.
    // args[1]: server1 IP (the one whose response will be forwarded to the client)
    // args[2]: server1 port.
    // args[3]: server2 IP (its response, if any, will be discarded)
    // args[4]: server2 port.
    static int puerto_local;
    static String server1_ip;
    static int server1_port;
    static String server2_ip;
    static int server2_port;

    static class Worker_1 extends Thread {
        Socket cliente_1;

        Worker_1(Socket cliente_1) {
            this.cliente_1 = cliente_1;
        }

        public void run() {
            Socket server1Socket = null;
            Socket server2Socket = null;
            try {
                // Connect to server 1 and server 2.
                server1Socket = new Socket(server1_ip, server1_port);
                server2Socket = new Socket(server2_ip, server2_port);

                // Start a thread to forward the response from server 1 back to the client.
                new Worker_2(cliente_1, server1Socket).start();
                // Start a thread to consume (and discard) the response from server 2.
                new Worker_3(server2Socket).start();

                InputStream entrada_1 = cliente_1.getInputStream();
                // Write the client's data to both servers.
                OutputStream salida_server1 = server1Socket.getOutputStream();
                OutputStream salida_server2 = server2Socket.getOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = entrada_1.read(buffer)) != -1) {
                    salida_server1.write(buffer, 0, n);
                    salida_server1.flush();
                    salida_server2.write(buffer, 0, n);
                    salida_server2.flush();
                }
            } catch (IOException e) {
                // Error handling (could log the error)
            } finally {
                try {
                    if (cliente_1 != null) cliente_1.close();
                    if (server1Socket != null) server1Socket.close();
                    if (server2Socket != null) server2Socket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    // This thread handles the response from server 1 and forwards it to the client.
    static class Worker_2 extends Thread {
        Socket cliente_1, server1Socket;

        Worker_2(Socket cliente_1, Socket server1Socket) {
            this.cliente_1 = cliente_1;
            this.server1Socket = server1Socket;
        }

        public void run() {
            try {
                InputStream entrada_server1 = server1Socket.getInputStream();
                OutputStream salida_1 = cliente_1.getOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = entrada_server1.read(buffer)) != -1) {
                    salida_1.write(buffer, 0, n);
                    salida_1.flush();
                }
            } catch (IOException e) {
                // Error handling
            } finally {
                try {
                    if (cliente_1 != null) cliente_1.close();
                    if (server1Socket != null) server1Socket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    // This thread simply reads and discards any response from server 2.
    static class Worker_3 extends Thread {
        Socket server2Socket;

        Worker_3(Socket server2Socket) {
            this.server2Socket = server2Socket;
        }

        public void run() {
            try {
                InputStream entrada_server2 = server2Socket.getInputStream();
                byte[] buffer = new byte[4096];
                while (entrada_server2.read(buffer) != -1) {
                    // Discard data.
                }
            } catch (IOException e) {
                // Error handling
            } finally {
                try {
                    if (server2Socket != null) server2Socket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Uso:\njava Proxy <puerto-local> <server1_ip> <server1_port> <server2_ip> <server2_port>");
            System.exit(1);
        }
        puerto_local = Integer.parseInt(args[0]);
        server1_ip = args[1];
        server1_port = Integer.parseInt(args[2]);
        server2_ip = args[3];
        server2_port = Integer.parseInt(args[4]);
        System.out.println("puerto_local: " + puerto_local + ", server1: " + server1_ip + ":" + server1_port +
                ", server2: " + server2_ip + ":" + server2_port);
        ServerSocket ss = new ServerSocket(puerto_local);
        for (; ; ) {
            // Wait for a connection from the client.
            Socket cliente_1 = ss.accept();
            // Thread that directs traffic from the client to both servers.
            new Worker_1(cliente_1).start();
        }
    }
}
