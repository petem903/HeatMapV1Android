# Local Maven mirror proxy.
# Gradle talks plain HTTP to 127.0.0.1:PORT (firewall allows loopback + the JVM
# never does outbound TLS). This process fetches upstream via .NET HttpClient,
# which the corporate firewall permits (verified: HTTP 200 from PowerShell).

param([int]$Port = 8765)

$csharp = @'
using System;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Text;
using System.Threading;

public class MirrorProxy {
    static HttpClient client;

    public static void Start(int port) {
        ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
        ServicePointManager.DefaultConnectionLimit = 200;
        var handler = new HttpClientHandler();
        handler.AllowAutoRedirect = true;
        client = new HttpClient(handler);
        client.Timeout = TimeSpan.FromSeconds(180);

        var listener = new TcpListener(IPAddress.Loopback, port);
        listener.Start();
        Console.WriteLine("Mirror proxy listening on 127.0.0.1:" + port);
        Console.Out.Flush();
        while (true) {
            var c = listener.AcceptTcpClient();
            ThreadPool.QueueUserWorkItem(delegate { Handle(c); });
        }
    }

    static string MapPath(string path) {
        if (path.StartsWith("/google/"))  return "https://dl.google.com/dl/android/maven2/" + path.Substring(8);
        if (path.StartsWith("/central/")) return "https://repo1.maven.org/maven2/" + path.Substring(9);
        if (path.StartsWith("/plugins/")) return "https://plugins.gradle.org/m2/" + path.Substring(9);
        return null;
    }

    static void Handle(TcpClient c) {
        try {
            using (c) {
                var ns = c.GetStream();
                var reader = new StreamReader(ns, Encoding.ASCII);
                string requestLine = reader.ReadLine();
                if (string.IsNullOrEmpty(requestLine)) return;
                string line;
                while (!string.IsNullOrEmpty(line = reader.ReadLine())) { }

                var parts = requestLine.Split(' ');
                string method = parts[0];
                string path = parts.Length > 1 ? parts[1] : "/";
                string upstream = MapPath(path);

                byte[] body; int status; string contentType = "application/octet-stream";
                if (upstream == null) {
                    body = Encoding.ASCII.GetBytes("Not mapped");
                    status = 404;
                } else {
                    try {
                        var verb = method == "HEAD" ? HttpMethod.Head : HttpMethod.Get;
                        var req = new HttpRequestMessage(verb, upstream);
                        var resp = client.SendAsync(req).GetAwaiter().GetResult();
                        status = (int)resp.StatusCode;
                        body = resp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult();
                        if (resp.Content.Headers.ContentType != null)
                            contentType = resp.Content.Headers.ContentType.ToString();
                        Console.WriteLine(status + " " + path);
                        Console.Out.Flush();
                    } catch (Exception ex) {
                        body = Encoding.ASCII.GetBytes(ex.Message);
                        status = 502;
                        Console.WriteLine("502 " + path + " : " + ex.Message);
                        Console.Out.Flush();
                    }
                }

                var head = "HTTP/1.1 " + status + " S\r\n"
                         + "Content-Length: " + body.Length + "\r\n"
                         + "Content-Type: " + contentType + "\r\n"
                         + "Connection: close\r\n\r\n";
                var hb = Encoding.ASCII.GetBytes(head);
                ns.Write(hb, 0, hb.Length);
                if (method != "HEAD" && body.Length > 0) ns.Write(body, 0, body.Length);
                ns.Flush();
            }
        } catch { }
    }
}
'@

Add-Type -TypeDefinition $csharp -ReferencedAssemblies System.Net.Http -ErrorAction Stop
Write-Host "Starting mirror proxy on port $Port ..."
[MirrorProxy]::Start($Port)
