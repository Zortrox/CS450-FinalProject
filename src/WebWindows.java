/**
 * Created by Zortrox on 11/10/2016.
 */



public class WebWindows {

	public static void main (String args[]) {
		String IP = "127.0.0.1";
		int port = 80;

		DNSServer dns = new DNSServer(IP, 4567);
		dns.start();
		WebServer web = new WebServer(IP, port);
		web.start();
		Client client = new Client();
	}

}
