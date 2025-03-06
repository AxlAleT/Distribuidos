import javax.net.ssl.*;
import java.security.KeyStore;
import java.io.*;
import java.net.*;

public class AdministradorTraficoSSL {
    // Arguments:
    // args[0]: local port to listen for a client.
    // args[1]: server1 IP (its response will be forwarded to the client)
    // args[2]: server1 port.
    // args[3]: server2 IP (its response, if any, will be discarded)
    // args[4]: server2 port.
    static int puerto_local;
    static String server1_ip;
    static int server1_port;
    static String server2_ip;
    static int server2_port;

    static SSLServerSocketFactory sslServerSocketFactory;

    // Static block to initialize the SSL context using server.jks.
    static {
        try {
            // Load the server's keystore containing its certificate and private key.
            KeyStore serverKeyStore = KeyStore.getInstance("JKS");
            serverKeyStore.load(new FileInputStream("keystore_servidor.jks"), "1234567".toCharArray());
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(serverKeyStore, "1234567".toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);
            sslServerSocketFactory = sslContext.getServerSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // This thread reads data from the secure client connection and forwards it to both servers.
    static class Worker_1 extends Thread {
        SSLSocket cliente_1;

        Worker_1(SSLSocket cliente_1) {
            this.cliente_1 = cliente_1;
        }

        public void run() {
            Socket server1Socket = null;
            Socket server2Socket = null;
            try {
                // Connect to server1 and server2 using plain sockets.
                server1Socket = new Socket(server1_ip, server1_port);
                server2Socket = new Socket(server2_ip, server2_port);

                // Forward server1's response back to the client.
                new Worker_2(cliente_1, server1Socket).start();
                // Discard server2's response.
                new Worker_3(server2Socket).start();

                InputStream entradaCliente = cliente_1.getInputStream();
                OutputStream salidaServer1 = server1Socket.getOutputStream();
                OutputStream salidaServer2 = server2Socket.getOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = entradaCliente.read(buffer)) != -1) {
                    salidaServer1.write(buffer, 0, n);
                    salidaServer1.flush();
                    salidaServer2.write(buffer, 0, n);
                    salidaServer2.flush();
                }
            } catch (IOException e) {
                // Error handling (e.g., logging)
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

    // This thread forwards the response from server1 back to the secure client.
    static class Worker_2 extends Thread {
        SSLSocket cliente_1;
        Socket server1Socket;

        Worker_2(SSLSocket cliente_1, Socket server1Socket) {
            this.cliente_1 = cliente_1;
            this.server1Socket = server1Socket;
        }

        public void run() {
            try {
                InputStream entradaServer1 = server1Socket.getInputStream();
                OutputStream salidaCliente = cliente_1.getOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = entradaServer1.read(buffer)) != -1) {
                    salidaCliente.write(buffer, 0, n);
                    salidaCliente.flush();
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

    // This thread reads and discards any response from server2.
    static class Worker_3 extends Thread {
        Socket server2Socket;

        Worker_3(Socket server2Socket) {
            this.server2Socket = server2Socket;
        }

        public void run() {
            try {
                InputStream entradaServer2 = server2Socket.getInputStream();
                byte[] buffer = new byte[4096];
                while (entradaServer2.read(buffer) != -1) {
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
            System.err.println("Uso:\njava AdministradorTraficoSSL <puerto-local> <server1_ip> <server1_port> <server2_ip> <server2_port>");
            System.exit(1);
        }
        puerto_local = Integer.parseInt(args[0]);
        server1_ip = args[1];
        server1_port = Integer.parseInt(args[2]);
        server2_ip = args[3];
        server2_port = Integer.parseInt(args[4]);
        System.out.println("puerto_local: " + puerto_local + ", server1: " + server1_ip + ":" + server1_port +
                ", server2: " + server2_ip + ":" + server2_port);

        // Create an SSL server socket for incoming secure client connections.
        SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(puerto_local);
        // Client authentication is not required.
        sslServerSocket.setNeedClientAuth(false);

        while (true) {
            // Accept a secure connection from the client.
            SSLSocket cliente_1 = (SSLSocket) sslServerSocket.accept();
            new Worker_1(cliente_1).start();
        }
    }
}
