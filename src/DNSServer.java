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

public class DNSServer extends NetObject{

	private BlockingQueue<Socket> qSockets = new LinkedBlockingQueue<>();
	private Thread tSockets;

	DNSServer(String IP, int port) {
		mIP = IP;
		mPort = port;

		//delete logs to overwrite
		File folder = new File("server-saves");
		File[] files = folder.listFiles();
		for(File f: files) {
			f.delete();
		}

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

		//process requests
		while (true)
		{
			Socket clientSocket = qSockets.take();
			writeMessage("[new connection]");

			Message msg = new Message();
			receiveTCPData(clientSocket, msg);
			String recMsg = new String(msg.mData);
			String name = recMsg.substring(0, recMsg.indexOf('='));
			recMsg = recMsg.substring(recMsg.indexOf('=') + 1);
			writeMessage("<" + name + ">: " + recMsg);

			//save data
			if (!serverSaveData(name, recMsg)) {
				writeMessage("[old data from " + name + "]");
			}

			String sndMsg = "Received: " + recMsg;
			msg.mData = sndMsg.getBytes();
			sendTCPData(clientSocket, msg);
		}
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
