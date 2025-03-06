import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;

class HTTP_Server {
    static class Worker extends Thread {
        Socket conexion;

        Worker(Socket conexion) {
            this.conexion = conexion;
        }

        int valor(String parametros, String variable) throws Exception {
            String[] p = parametros.split("&");
            for (int i = 0; i < p.length; i++) {
                String[] s = p[i].split("=");
                if (s[0].equals(variable))
                    return Integer.parseInt(s[1]);
            }
            throw new Exception("Se espera la variable: " + variable);
        }

        public void run() {
            try {
                BufferedReader entrada = new BufferedReader(new InputStreamReader(conexion.getInputStream()));
                PrintWriter salida = new PrintWriter(conexion.getOutputStream());

                String req = entrada.readLine();
                System.out.println("Petición: " + req);

                // Leer headers y capturar If-Modified-Since si está presente
                String ifModifiedSince = null;
                for (;;) {
                    String encabezado = entrada.readLine();
                    if (encabezado == null || encabezado.equals("")) break;
                    System.out.println("Encabezado: " + encabezado);
                    if (encabezado.startsWith("If-Modified-Since:")) {
                        ifModifiedSince = encabezado.substring("If-Modified-Since:".length()).trim();
                    }
                }

                // Endpoint para la suma permanece sin cambios
                if (req.startsWith("GET /suma?")) {
                    String parametros = req.split(" ")[1].split("\\?")[1];
                    String respuesta = String.valueOf(
                            valor(parametros, "a") +
                                    valor(parametros, "b") +
                                    valor(parametros, "c"));
                    salida.println("HTTP/1.1 200 OK");
                    salida.println("Access-Control-Allow-Origin: *"); // permite todos los orígenes
                    salida.println("Content-type: text/plain; charset=utf-8");
                    salida.println("Content-length: " + respuesta.length());
                    salida.println("Connection: close");
                    salida.println();
                    salida.println(respuesta);
                    salida.flush();
                } else if (req.startsWith("GET / ")) {
                    // Servir el archivo index.html con control de cache mediante Last-Modified
                    File file = new File("index.html");
                    if (!file.exists()) {
                        salida.println("HTTP/1.1 404 Not Found");
                        salida.println("Connection: close");
                        salida.println();
                        salida.flush();
                    } else {
                        // Preparar la fecha Last-Modified en formato RFC 1123 (GMT)
                        long lastModified = file.lastModified();
                        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                        String lastModifiedStr = sdf.format(new Date(lastModified));

                        boolean notModified = false;
                        if (ifModifiedSince != null) {
                            try {
                                Date ifModifiedSinceDate = sdf.parse(ifModifiedSince);
                                // Debug: imprimir las fechas
                                System.out.println(ifModifiedSinceDate);
                                System.out.println(lastModifiedStr);
                                // Comparar en segundos para evitar problemas de precisión
                                if ((lastModified / 1000) <= (ifModifiedSinceDate.getTime() / 1000)) {
                                    notModified = true;
                                }
                                System.out.println(notModified);
                            } catch (Exception e) {
                                // Error al parsear: ignorar el header y enviar el contenido completo
                            }
                        }

                        if (notModified) {
                            salida.println("HTTP/1.1 304 Not Modified");
                            salida.println("Last-Modified: " + lastModifiedStr);
                            salida.println("Connection: close");
                            salida.println();
                            salida.flush();
                        } else {
                            // Leer el contenido del archivo en una cadena
                            FileInputStream fis = new FileInputStream(file);
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            byte[] buffer = new byte[1024];
                            int n;
                            while ((n = fis.read(buffer)) != -1) {
                                baos.write(buffer, 0, n);
                            }
                            fis.close();
                            String content = baos.toString("UTF-8");

                            salida.println("HTTP/1.1 200 OK");
                            salida.println("Last-Modified: " + lastModifiedStr);
                            salida.println("Content-type: text/html; charset=utf-8");
                            salida.println("Content-length: " + content.getBytes("UTF-8").length);
                            salida.println("Connection: close");
                            salida.println();
                            salida.println(content);
                            salida.flush();
                        }
                    }
                } else {
                    salida.println("HTTP/1.1 404 Not Found");
                    salida.println("Connection: close");
                    salida.println();
                    salida.flush();
                }
            } catch (Exception e) {
                System.err.println("Error en la conexión: " + e.getMessage());
            } finally {
                try {
                    conexion.close();
                } catch (Exception e) {
                    System.err.println("Error en close: " + e.getMessage());
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso:\njava HTTP_Server <puerto-local>");
            System.exit(1);
        }

        int puerto_local = Integer.parseInt(args[0]);
        ServerSocket servidor = new ServerSocket(puerto_local);
        System.out.println("Servidor iniciado en el puerto " + puerto_local);

        for (;;) {
            Socket conexion = servidor.accept();
            new Worker(conexion).start();
        }
    }
}
