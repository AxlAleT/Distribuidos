import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Proxy que reenvía datos del cliente a dos servidores remotos y devuelve
 * la respuesta del primer servidor al cliente.
 */
public class AdministradorTrafico {
    private static String host1;
    private static int port1;
    private static String host2;
    private static int port2;
    private static int localPort;
    private static final int BUFFER_SIZE = 4096;

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("Uso: java AdministradorTrafico " +
                    "<host-remoto-1> <puerto-remoto-1> " +
                    "<host-remoto-2> <puerto-remoto-2> <puerto-local>");
            System.exit(1);
        }

        host1 = args[0];
        port1 = Integer.parseInt(args[1]);
        host2 = args[2];
        port2 = Integer.parseInt(args[3]);
        localPort = Integer.parseInt(args[4]);

        System.out.printf(
            "Iniciando proxy en puerto %d, reenviando a %s:%d y %s:%d\n",
            localPort, host1, port1, host2, port2
        );

        ExecutorService executor = Executors.newCachedThreadPool();

        try (ServerSocket serverSocket = new ServerSocket(localPort)) {
            while (true) {
                // Acepta nueva conexión de cliente y crea sockets remotos en try-with-resources
                try (Socket client = serverSocket.accept();
                     Socket s1 = new Socket(host1, port1);
                     Socket s2 = new Socket(host2, port2)) {

                    // Reenvío de respuestas: servidor1 -> cliente
                    executor.submit(new StreamForwarder(
                        s1.getInputStream(), client.getOutputStream(), BUFFER_SIZE
                    ));

                    // Drenar respuestas de servidor2 para evitar bloqueo
                    executor.submit(new StreamForwarder(
                        s2.getInputStream(), null, BUFFER_SIZE
                    ));

                    // Reenvío de datos del cliente a ambos servidores
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    InputStream inClient = client.getInputStream();
                    OutputStream out1 = s1.getOutputStream();
                    OutputStream out2 = s2.getOutputStream();

                    while ((len = inClient.read(buffer)) != -1) {
                        out1.write(buffer, 0, len);
                        out1.flush();
                        out2.write(buffer, 0, len);
                        out2.flush();
                    }

                } catch (IOException e) {
                    System.err.println("Error en conexión de cliente: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo iniciar el servidor en el puerto " + localPort + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Runnable que copia datos desde un InputStream a un OutputStream.
     * Si el OutputStream es null, simplemente drena el InputStream.
     */
    static class StreamForwarder implements Runnable {
        private final InputStream in;
        private final OutputStream out;
        private final int bufSize;

        StreamForwarder(InputStream in, OutputStream out, int bufSize) {
            this.in = in;
            this.out = out;
            this.bufSize = bufSize;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[bufSize];
            int len;
            try {
                while ((len = in.read(buffer)) != -1) {
                    if (out != null) {
                        out.write(buffer, 0, len);
                        out.flush();
                    }
                }
            } catch (IOException e) {
                // Puede ocurrir al cerrar sockets; registrar para depuración
                System.err.println("StreamForwarder error: " + e.getMessage());
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {}
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }
}
