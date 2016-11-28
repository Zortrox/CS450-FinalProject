/**
 * Created by Zortrox on 11/10/2016.
 */

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.Dimension;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class WebServer extends NetObject {

	private BlockingQueue<Socket> qSockets = new LinkedBlockingQueue<>();
	private Thread tSockets;

	public static void main(String[] args) {
		(new WebServer()).start();
	}

	WebServer() {
		mIP = mSettings.get("ip");
		mPort = Integer.valueOf(mSettings.get("port"));

		JFrame frame = new JFrame("Server");
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
			TCPConnection();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void TCPConnection() throws Exception{
		//queue up new requests
		tSockets = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					writeMessage("[listening for connections]");

					ServerSocket serverSocket = new ServerSocket(mPort);

					while (true) {
						Socket newSocket = serverSocket.accept();
						qSockets.put(newSocket);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		});
		tSockets.start();

		//add self to DNS
		DatagramSocket dnsSocket = new DatagramSocket();
		String serverID = "new=" + mSettings.get("domain") + ">" + mIP + ":" + mPort;

		Message msgDNS = new Message();
		msgDNS.mData = serverID.getBytes();
		msgDNS.mIP = InetAddress.getByName(mSettings.get("dns_ip"));
		msgDNS.mPort = Integer.valueOf(mSettings.get("dns_port"));
		sendUDPData(dnsSocket, msgDNS);
		receiveUDPData(dnsSocket, msgDNS);

		String strData = new String(msgDNS.mData).trim();
		String code = strData.substring(0, strData.indexOf('='));

		if (code.equals("success")) {
			writeMessage("[connected to DNS]");

			//process requests (4 threads)
			Runnable runConnection = new Runnable() {
				@Override
				public void run() {
					try {
						processConnection();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			};

			Thread thrConnection1 = new Thread(runConnection);
			thrConnection1.start();
			Thread thrConnection2 = new Thread(runConnection);
			thrConnection2.start();
			Thread thrConnection3 = new Thread(runConnection);
			thrConnection3.start();
			Thread thrConnection4 = new Thread(runConnection);
			thrConnection4.start();
		} else {
			writeMessage("Error: Could not connect to DNS");
		}
	}

	private void processConnection() throws Exception {
		while (true)
		{
			Socket clientSocket = qSockets.take();
			writeMessage("[new connection]");

			Message msg = new Message();
			boolean bClosed = false;
			do {
				receiveTCPData(clientSocket, msg);
				String recMsg = new String(msg.mData);
				String type = recMsg.substring(0, recMsg.indexOf('='));
				recMsg = recMsg.substring(recMsg.indexOf('=') + 1);


				if (type.equals("close")) {
					//close the connection
					bClosed = true;
				} else if (qSockets.size() > 4) {
					//DROP CONNECTION WHEN BUFFER SIZE LARGE
					writeMessage("[Too many connections: closing]");
					msg.mData = "close: too many connections".getBytes();
					sendTCPData(clientSocket, msg);
					bClosed = true;
				} else if (type.equals("file")) {
					writeMessage("Sending file: " + recMsg);
					msg.mData = serverReadFile(recMsg);
					sendTCPData(clientSocket, msg);
				} else {
					String sndMsg = "error=unknown protocol";
					msg.mData = sndMsg.getBytes();
					sendTCPData(clientSocket, msg);
				}
			}
			while (!bClosed);
		}
	}

	private byte[] serverReadFile(String filename) {
		Path file = Paths.get("server-content/" + filename);
		byte[] data;

		try {
			byte[] fileData = Files.readAllBytes(file);
			data = Arrays.copyOf("file=".getBytes(), fileData.length + 5);
			System.arraycopy(fileData, 0, data, 5, fileData.length);
		} catch (Exception ex) {
			//ex.printStackTrace();
			writeMessage("File does not exist: " + filename);
			return "error=404: file not found".getBytes();
		}

		return data;
	}
}
