/**
 * Created by Zortrox on 11/11/2016.
 */

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;

class Message {
	byte[] mData;
	InetAddress mIP;
	int mPort;
}

public class NetObject extends Thread{

	JTextArea textArea;
	JScrollPane scrollPane;

	String mIP = "";
	int mPort = 0;

	Map<String, String> mSettings = readSettingsFile();

	protected static final int PACKET_SIZE = 64;

	public void run() { }

	void receiveUDPData(DatagramSocket socket, Message msg) throws Exception{
		msg.mData = new byte[PACKET_SIZE];
		DatagramPacket receivePacket = new DatagramPacket(msg.mData, msg.mData.length);
		socket.receive(receivePacket);
	}

	void sendUDPData(DatagramSocket socket, Message msg) throws Exception{
		//check message length
		if (msg.mData.length > PACKET_SIZE) {
			byte[] newData = new byte[PACKET_SIZE];
			newData = Arrays.copyOfRange(msg.mData, 0, 20);
			msg.mData = newData;
		}

		//send data
		DatagramPacket sendPacket = new DatagramPacket(msg.mData, msg.mData.length, msg.mIP, msg.mPort);
		socket.send(sendPacket);
	}

	void receiveTCPData(Socket socket, Message msg) throws Exception{
		DataInputStream inData = new DataInputStream(socket.getInputStream());

		//get size of receiving data
		byte[] byteSize = new byte[4];
		inData.readFully(byteSize);
		ByteBuffer bufSize = ByteBuffer.wrap(byteSize);
		int dataSize = bufSize.getInt();

		//receive data
		msg.mData = new byte[dataSize];
		inData.readFully(msg.mData);
	}

	void sendTCPData(Socket socket, Message msg) throws Exception {
		DataOutputStream outData = new DataOutputStream(socket.getOutputStream());

		//send size of data & name
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(msg.mData.length);
		byte[] dataSize = b.array();
		outData.write(dataSize);

		//send data
		outData.write(msg.mData);
	}

	public void writeMessage(String msg) {
		textArea.append(msg + "\n");
		scrollPane.scrollRectToVisible(textArea.getBounds());
	}

	public void clearMessages() {
		textArea.setText("");
	}

	public Map<String, String> readSettingsFile() {
		Map<String, String> settings = new HashMap<>();

		try {
			Path path = FileSystems.getDefault().getPath(".", "settings.ini");
			List<String> lines = Files.readAllLines(path, Charset.defaultCharset());

			for (int i=0; i < lines.size(); i++) {
				String line = lines.get(i).trim();

				if (!line.equals("") || line.charAt(0) != '#') {
					String setting = line.substring(0, line.indexOf('=')).trim();
					String value = line.substring(line.indexOf('=') + 1).trim();

					settings.put(setting, value);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return settings;
	}
}

