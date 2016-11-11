/**
 * Created by Zortrox on 11/10/2016.
 */

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Document;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.*;

public class Client extends NetObject{

	private JTextField textAddress;
	private JEditorPane textArea;
	private HTMLEditorKit kit;
	private JButton btnAddress;

	private Thread threadConnection = null;
	private volatile boolean threadQuit = false;

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

		btnAddress.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
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
		});

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
			String msgSend = "file=sample.htm";

			Message msg = new Message();
			msg.mData = msgSend.getBytes();
			sendTCPData(socket, msg);
			receiveTCPData(socket, msg);
			String msgReceive = new String(msg.mData);

			String type = msgReceive.substring(0, msgReceive.indexOf('='));
			msgReceive = msgReceive.substring(msgReceive.indexOf('=') + 1);

			if (type.equals("file")) {
				clearMessages();
				writeMessage(msgReceive);
			} else if (type.equals("error")) {
				writeMessage(msgReceive);
			}

			socket.close();
		}
	}

	public void writeMessage(String msg) {
		kit.createDefaultDocument();
		textArea.setEditorKit(kit);
		textArea.setText(msg);
		scrollPane.scrollRectToVisible(textArea.getBounds());
	}

	public void clearMessages() {
		textArea.setText("");
	}
}
