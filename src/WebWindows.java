/**
 * Created by Zortrox on 11/10/2016.
 */



public class WebWindows {

	public static void main (String args[]) {
		DNSServer dns = new DNSServer();
		dns.start();
		WebServer web = new WebServer();
		web.start();
		Client client = new Client();
	}

}
