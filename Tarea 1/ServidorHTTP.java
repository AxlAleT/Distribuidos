import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

class ServidorHTTP {
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
                String line;
                String ifModifiedSince = null;
                while ((line = entrada.readLine()) != null && !line.equals("")) {
                    if (line.startsWith("If-Modified-Since:")) {
                        ifModifiedSince = line.substring("If-Modified-Since:".length()).trim();
                    }
                }
                if (req.startsWith("GET /suma?")) {
                    String parametros = req.split(" ")[1].split("\\?")[1];
                    String respuesta = String.valueOf(valor(parametros, "a") + valor(parametros, "b") + valor(parametros, "c"));
                    salida.println("HTTP/1.1 200 OK");
                    salida.println("Access-Control-Allow-Origin: *");
                    salida.println("Content-type: text/plain; charset=utf-8");
                    salida.println("Content-length: " + respuesta.length());
                    salida.println("Connection: close");
                    salida.println();
                    salida.println(respuesta);
                    salida.flush();
                } else if (req.startsWith("GET / ")) {
                    File file = new File("index.html");
                    long lastModified = file.lastModified();
                    lastModified = (lastModified / 1000) * 1000;
                    SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
                    sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                    String lastModifiedStr = sdf.format(new Date(lastModified));
                    if (ifModifiedSince != null) {
                        try {
                            Date ifModDate = sdf.parse(ifModifiedSince);
                            if (ifModDate.getTime() >= lastModified) {
                                salida.println("HTTP/1.1 304 Not Modified");
                                salida.println("Connection: close");
                                salida.println();
                                salida.flush();
                                conexion.close();
                                return;
                            }
                        } catch (Exception e) {
                        }
                    }
                    BufferedReader fileReader = new BufferedReader(new FileReader(file));
                    StringBuilder contentBuilder = new StringBuilder();
                    String lineFile;
                    while ((lineFile = fileReader.readLine()) != null) {
                        contentBuilder.append(lineFile).append("\n");
                    }
                    fileReader.close();
                    String respuesta = contentBuilder.toString();
                    salida.println("HTTP/1.1 200 OK");
                    salida.println("Last-Modified: " + lastModifiedStr);
                    salida.println("Content-type: text/html; charset=utf-8");
                    salida.println("Content-length: " + respuesta.getBytes("UTF-8").length);
                    salida.println("Connection: close");
                    salida.println();
                    salida.println(respuesta);
                    salida.flush();
                } else {
                    salida.println("HTTP/1.1 404 File Not Found");
                    salida.println("Connection: close");
                    salida.println();
                    salida.flush();
                }
            } catch (Exception e) {
            } finally {
                try {
                    conexion.close();
                } catch (Exception e) {
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
        for (;;) {
            Socket conexion = servidor.accept();
            new Worker(conexion).start();
        }
    }
}
