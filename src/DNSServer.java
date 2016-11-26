/**
 * Created by Zortrox on 11/10/2016.
 */

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.Dimension;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class DNSServer extends NetObject{

	private BlockingQueue<DatagramPacket> qPackets = new LinkedBlockingQueue<>();
	private Thread tPackets;

	HashMap<String, String> tableIP = new HashMap<>();

	DNSServer() {
		mIP = mSettings.get("ip");
		mPort = Integer.valueOf(mSettings.get("dns_port"));

		//ADD TEMPORARY SERVER
		tableIP.put("www.abc.com", "127.0.0.1:80");

		JFrame frame = new JFrame("DNS Server");
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setSize(new Dimension(300, 200));

		textArea = new JTextArea(1, 50);
		scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		textArea.setEditable(false);
		frame.getContentPane().add(scrollPane);

		DefaultCaret caret = (DefaultCaret) textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		frame.setVisible(true);

		frame.setLocation(0, 200);
	}

	public void run() {
		try {
			UDPConnection();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void UDPConnection() throws Exception{
		DatagramSocket serverSocket = new DatagramSocket(mPort);

		//queue packets
		tPackets = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					writeMessage("[Listening for packets]");

					while (true) {
						byte[] receiveData = new byte[PACKET_SIZE];
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						serverSocket.receive(receivePacket);

						qPackets.put(receivePacket);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		tPackets.start();

		while(true) {
			//receive data
			DatagramPacket receivePacket = qPackets.take();
			writeMessage("[New Packet]");
			Message msg = new Message();
			//process packet into the message
			processUDPData(receivePacket, msg);

			//get domain name
			String domain = new String(msg.mData);
			domain = domain.trim();
			int eqIndex = domain.indexOf('=');

			String sendMsg;
			if (eqIndex  > -1 && domain.substring(0, eqIndex).equals("new")) {
				String IP = domain.substring(domain.indexOf('>') + 1);
				domain = domain.substring(eqIndex + 1, domain.indexOf('>'));

				//add domain name & IP to map
				tableIP.put(domain, IP);

				writeMessage("New Site: " + domain + " -> " + IP);

				sendMsg = "success=true";
				sendUDPData(serverSocket, msg);
			} else {
				//get IP from domain
				String IP = tableIP.get(domain);
				if (IP == null) {
					//trim "www" if there
					int indexDot = domain.indexOf('.');
					if (indexDot >= 0) {
						String sub = domain.substring(0, indexDot);
						if (sub.equals("www")) {
							IP = tableIP.get(domain.substring(domain.indexOf('.')));
						} else {
							IP = tableIP.get("www." + domain);
						}
					}
				}

				if (IP != null) {
					sendMsg = "IP=" + IP;
					writeMessage("[" + domain + " -> " + IP + "]");
				} else {
					sendMsg = "error=no IP found";
					writeMessage("[" + domain + " not found]");
				}
			}
			msg.mData = sendMsg.getBytes();
			sendUDPData(serverSocket, msg);
		}
	}

	private void processUDPData(DatagramPacket receivePacket, Message msg) {
		msg.mData = receivePacket.getData();
		msg.mIP = receivePacket.getAddress();
		msg.mPort = receivePacket.getPort();
	}

	private boolean serverSaveData(String name, String data) {
		Path file = Paths.get("server-saves/" + name + ".txt");

		try {
			Files.createFile(file);
		} catch (Exception ex) {
			//file already exists
			//ex.printStackTrace();
		}

		try {
			List lines = Files.readAllLines(file);

			if (lines.contains(data)) {
				return false;
			}

			Files.write(file, (data + "\n").getBytes(), StandardOpenOption.APPEND);
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return true;
	}
}
