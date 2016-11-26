/**
 * Created by Zortrox on 11/10/2016.
 */

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.Dimension;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;

public class WebServer extends NetObject{

	private BlockingQueue<Socket> qSockets = new LinkedBlockingQueue<>();
	private Thread tSockets;

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
		//add self to DNS
		DatagramSocket dnsSocket = new DatagramSocket();
		String serverID = "new=www.gecko.com>" + mIP + ":" + mPort;

		Message msgDNS = new Message();
		msgDNS.mData = serverID.getBytes();
		msgDNS.mIP = InetAddress.getByName(mSettings.get("dns_ip"));
		msgDNS.mPort = Integer.valueOf(mSettings.get("dns_port"));
		sendUDPData(dnsSocket, msgDNS);
		receiveUDPData(dnsSocket, msgDNS);

		String strData = new String(msgDNS.mData).trim();
		String code = strData.substring(0, strData.indexOf('='));

		if (code.equals("success")) {
			//mIP = strData.substring(strData.indexOf('=') + 1, strData.indexOf(':'));
			//mPort = Integer.parseInt(strData.substring(strData.indexOf(':') + 1));
		} else {
			writeMessage(strData.substring(strData.indexOf('=') + 1));
			//threadQuit = true;
		}

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

		//process requests
		while (true)
		{
			Socket clientSocket = qSockets.take();
			writeMessage("[new connection]");

			Message msg = new Message();
			receiveTCPData(clientSocket, msg);
			String recMsg = new String(msg.mData);
			String type = recMsg.substring(0, recMsg.indexOf('='));
			recMsg = recMsg.substring(recMsg.indexOf('=') + 1);

			if (type.equals("file")) {
				writeMessage("Sending file: " + recMsg);
				String sndMsg = serverReadFile(recMsg);
				msg.mData = sndMsg.getBytes();
				sendTCPData(clientSocket, msg);
			} else {
				String sndMsg = "error=unknown protocol";
				msg.mData = sndMsg.getBytes();
				sendTCPData(clientSocket, msg);
			}

		}
	}

	private String serverReadFile(String filename) {
		Path file = Paths.get("server-content/" + filename);
		String data = "file=";

		try {
			List lines = Files.readAllLines(file);

			for (int i = 0; i < lines.size(); i++) {
				data += lines.get(i) + "\n";
			}
		} catch (Exception ex) {
			//ex.printStackTrace();
			writeMessage("File does not exist: " + filename);
			return "error=404: file not found";
		}

		return data;
	}
}
