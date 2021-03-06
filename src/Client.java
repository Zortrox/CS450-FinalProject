/**
 * Created by Zortrox on 11/10/2016.
 */

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.*;

public class Client extends NetObject {

	private JTextField textAddress;
	private JEditorPane textArea;
	private HTMLEditorKit kit;
	private JButton btnAddress;

	private Thread threadConnection = null;
	private volatile boolean threadQuit = false;

	public static void main(String[] args) {
		new Client();
	}

	Client() {
		JFrame frame = new JFrame("Client");
		frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		frame.setSize(new Dimension(640, 480));

		JPanel topPanel = new JPanel();
		topPanel.setLayout(new BorderLayout());
		textAddress = new JTextField();
		btnAddress = new JButton("Go");
		topPanel.add(textAddress, BorderLayout.CENTER);
		topPanel.add(btnAddress, BorderLayout.EAST);
		frame.getContentPane().add(topPanel, BorderLayout.NORTH);

		Action actionURL = new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				try {
					if (threadConnection != null) {
						threadQuit = true;
						threadConnection.join();
						threadQuit = false;
					}

					String url = textAddress.getText();
					int pos = url.indexOf(':');
					mIP = url.substring(0, pos < 0 ? url.length() : pos);
					mPort = pos < 0 ? 80 : Integer.parseInt(url.substring(pos + 1));
					threadConnection = new Thread(new Runnable() {
						@Override
						public void run() {
							try {
								TCPConnection();
							} catch (Exception ex) {
								ex.printStackTrace();
							}
						}
					});
					threadConnection.start();
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		};

		btnAddress.addActionListener(actionURL);
		textAddress.addActionListener(actionURL);

		kit = new HTMLEditorKit();
		textArea = new JEditorPane();
		textArea.setEditorKit(kit);
		scrollPane = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
				JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		textArea.setEditable(false);
		frame.getContentPane().add(scrollPane);

		DefaultCaret caret = (DefaultCaret) textArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		frame.setVisible(true);
	}

	private void TCPConnection() throws Exception {
		Socket socket = null;
		boolean bServerFound = false;

		//clear screen for message displaying
		clearMessages();

		//connect to DNS
		DatagramSocket clientSocket = new DatagramSocket();
		Message msgDNS = new Message();
		msgDNS.mData = mIP.getBytes();
		msgDNS.mIP = InetAddress.getByName(mSettings.get("dns_ip"));
		msgDNS.mPort = Integer.valueOf(mSettings.get("dns_port"));
		sendUDPData(clientSocket, msgDNS);
		receiveUDPData(clientSocket, msgDNS);

		String strData = new String(msgDNS.mData).trim();
		String code = strData.substring(0, strData.indexOf('='));

		if (code.equals("IP")) {
			mIP = strData.substring(strData.indexOf('=') + 1, strData.indexOf(':'));
			mPort = Integer.parseInt(strData.substring(strData.indexOf(':') + 1));
		} else {
			writeMessage(strData.substring(strData.indexOf('=') + 1));
			threadQuit = true;
		}

		//keep trying to connect to server
		while(!bServerFound && !threadQuit)
		{
			try
			{
				socket = new Socket(mIP, mPort);
				bServerFound = true;
			}
			catch(ConnectException e)
			{
				writeMessage("Server refused, retrying...");

				try
				{
					Thread.sleep(2000); //2 seconds
				}
				catch(InterruptedException ex){
					ex.printStackTrace();
				}
			}
		}

		if (!threadQuit) {
			//initialize message
			String msgSend = "file=index.htm";

			Message msg = new Message();
			msg.mData = msgSend.getBytes();
			sendTCPData(socket, msg);
			receiveTCPData(socket, msg);
			String msgReceive = new String(msg.mData);

			String type = msgReceive.substring(0, msgReceive.indexOf('='));
			msgReceive = msgReceive.substring(msgReceive.indexOf('=') + 1);

			if (type.equals("file")) {
				String htmlText = msgReceive;

				//download all files
				ArrayList<String> allFilenames = new ArrayList<String>();
				Matcher m = Pattern.compile("(?<=src=\").*?(?=\")")
						.matcher(msgReceive);
				while (m.find()) {
					allFilenames.add(m.group());
				}
				for (int i = 0; i < allFilenames.size(); i++) {
					msg.mData = ("file=" + allFilenames.get(i)).getBytes();
					sendTCPData(socket, msg);
					receiveTCPData(socket, msg);

					String filename = allFilenames.get(i);
					int lastIndex = filename.lastIndexOf('/');
					filename = filename.substring(lastIndex + 1);

					msgReceive = new String(msg.mData);
					type = msgReceive.substring(0, msgReceive.indexOf('='));
					msgReceive = msgReceive.substring(msgReceive.indexOf('=') + 1);

					if (type.equals("file")) {
						Path file = Paths.get("temp/" + filename);
						Files.write(file, Arrays.copyOfRange(msg.mData, 5, msg.mData.length));
					}
				}

				//write html to file
				clearMessages();
				writeMessage(htmlText);
			} else if (type.equals("error")) {
				writeMessage(msgReceive);
			} else if (type.equals("close")) {
				//close the connection and stop
				socket.close();
				return;
			}

			msg.mData = "close=true".getBytes();
			sendTCPData(socket, msg);

			socket.close();
		}
	}

	public void writeMessage(String msg) {
		kit.createDefaultDocument();
		textArea.setEditorKit(kit);
		msg = msg.replaceAll("(src=\")", "$1file:temp/");
		textArea.setText(msg);
		//scroll back to top
		textArea.setCaretPosition(0);
	}

	public void clearMessages() {
		textArea.setText("");
	}
}
