import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.net.Socket;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLHandshakeException;

class AdministradorTraficoSSL {
    static int puerto_local;
    static String host_server1;
    static int puerto_server1;
    static String host_server2;
    static int puerto_server2;
    static class Worker_1 extends Thread {
        Socket cliente;
        Worker_1(Socket cliente) {
            this.cliente = cliente;
        }
        public void run() {
            Socket s1 = null, s2 = null;
            try {
                s1 = new Socket(host_server1, puerto_server1);
                s2 = new Socket(host_server2, puerto_server2);
                new Worker_2(cliente, s1).start();
                new Worker_3(s2).start();
                InputStream in = cliente.getInputStream();
                OutputStream out1 = s1.getOutputStream();
                OutputStream out2 = s2.getOutputStream();
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out1.write(buffer, 0, n);
                    out1.flush();
                    out2.write(buffer, 0, n);
                    out2.flush();
                }
            } catch (IOException e) {
            } finally {
                try {
                    if (cliente != null) cliente.close();
                    if (s1 != null) s1.close();
                    if (s2 != null) s2.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }
    static class Worker_2 extends Thread {
        Socket cliente, s1;
        Worker_2(Socket cliente, Socket s1) {
            this.cliente = cliente;
            this.s1 = s1;
        }
        public void run() {
            try {
                InputStream in = s1.getInputStream();
                OutputStream out = cliente.getOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    out.flush();
                }
            } catch (IOException e) {
            } finally {
                try {
                    if (cliente != null) cliente.close();
                    if (s1 != null) s1.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }
    static class Worker_3 extends Thread {
        Socket s2;
        Worker_3(Socket s2) {
            this.s2 = s2;
        }
        public void run() {
            try {
                InputStream in = s2.getInputStream();
                byte[] buffer = new byte[1024];
                while (in.read(buffer) != -1) {
                }
            } catch (IOException e) {
            } finally {
                try {
                    if (s2 != null) s2.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Uso:\njava AdministradorTraficoSSL <puerto-local> <server1-ip> <server1-port> <server2-ip> <server2-port>");
            System.exit(1);
        }
        puerto_local = Integer.parseInt(args[0]);
        host_server1 = args[1];
        puerto_server1 = Integer.parseInt(args[2]);
        host_server2 = args[3];
        puerto_server2 = Integer.parseInt(args[4]);
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream("keystore_servidor.jks"), "1234567".toCharArray());
        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, "1234567".toCharArray());
        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(kmf.getKeyManagers(), null, null);
        SSLServerSocketFactory ssf = sc.getServerSocketFactory();
        SSLServerSocket ss = (SSLServerSocket) ssf.createServerSocket(puerto_local);
        while (true) {
            Socket cliente = ss.accept();
            try {
                ((SSLSocket) cliente).startHandshake();
            } catch (SSLHandshakeException e) {
                try { cliente.close(); } catch (IOException ex) {}
                continue;
            }
            new Worker_1(cliente).start();
        }
    }
}
