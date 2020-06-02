package com.emeryferrari.jse3dclient;
import java.net.*;
import java.io.*;
import com.emeryferrari.jse3d.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import com.emeryferrari.jse3d.network.*;
public class JSE3DClient {
	ArrayList<User> locations = new ArrayList<User>();
	Display display;
	InetAddress address;
	int port;
	String username;
	Socket socket;
	private boolean stop = false;
	private static final JSE3DClient CLASS_OBJ = new JSE3DClient();
	public static void main(String[] args) {
		for (int i = 0; i < 1000; i++) {
			CLASS_OBJ.locations.add(null);
		}
		if (args.length == 3) {
			CLASS_OBJ.address = null;
			CLASS_OBJ.port = 5107;
			CLASS_OBJ.username = args[2];
			try {
				CLASS_OBJ.address = InetAddress.getByName(args[0]);
				CLASS_OBJ.port = Integer.parseInt(args[1]);
			} catch (NumberFormatException ex) {
				System.out.println("Incorrectly formatted port. Defaulting to 5107...");
			} catch (UnknownHostException ex) {
				System.out.println("Incorrectly formatted IP address.");
				printUsage(2);
			}
			CLASS_OBJ.start();
		} else {
			printUsage(4);
		}
	}
	public void start() {
		if (address != null) {
			try {
				System.out.println("Connecting to server at " + address + ":" + port + "...");
				socket = new Socket(address, port);
				System.out.println("Connected!");
				PrintWriter writer = new PrintWriter(socket.getOutputStream());
				writer.println(username);
				writer.println(JSE3DClientConst.VERSION);
				writer.flush();
				ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
				Object object = ois.readObject();
				Scene scene = null;
				if (object instanceof Scene) {
					scene = (Scene) object;
				} else if (object instanceof Disconnect) {
					Disconnect ds = (Disconnect) object;
					if (ds.getType() == DisconnectType.USERNAME_TAKEN) {
						System.out.println("Specified username is taken by another user.\nDisconnecting...");
						socket.close();
						System.exit(5);
					} else if (ds.getType() == DisconnectType.USERNAME_INVALID){
						System.out.println("Username is invalid.\nDisconnecting...");
					}
					stop = true;
				} else {
					System.out.println("Invalid response from server.\nDisconnecting...");
					socket.close();
					System.exit(4);
				}
				Thread consoleReader = new Thread(new ConsoleReader());
				consoleReader.setName("command-processor");
				consoleReader.start();
				Thread receiver = new Thread(new Receiver(socket));
				receiver.setName("receiver");
				receiver.start();
				display = new Display(scene, "jse3dclient", true, true);
				display.startRender();
				JPanel buttons = new JPanel();
				buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
				JButton disconnect = new JButton("Disconnect");
				disconnect.addActionListener(new DisconnectListener());
				buttons.add(disconnect);
				display.getFrame().getContentPane().add(BorderLayout.SOUTH, buttons);
			} catch (IOException ex) {
				handleException(ex);
			} catch (ClassNotFoundException ex) {
				handleException(ex);
				System.exit(3);
			}
		}
	}
	public class DisconnectListener implements ActionListener {
		public void actionPerformed(ActionEvent ev) {
			System.out.println("Disconnecting...");
			try {
				ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
				oos.writeObject(new Disconnect(DisconnectType.GENERAL_DISCONNECT));
				oos.flush();
				socket.close();
			} catch (IOException ex) {
				handleException(ex);
			} finally {
				System.exit(7);
			}
 		}
	}
	public class Receiver implements Runnable {
		private Socket socket;
		public Receiver(Socket socket) {
			this.socket = socket;
		}
		public void run() {
			ObjectInputStream ois = null;
			try {
				ois = new ObjectInputStream(socket.getInputStream());
			} catch (IOException ex) {
				handleException(ex);
			}
			if (ois != null) {
				while (!stop) {
					try {
						Object object = ois.readObject();
						if (object instanceof Scene) {
							Scene tmp = (Scene) object;
							display.setScene(tmp);
						} else if (object instanceof Disconnect) {
							Disconnect ds = (Disconnect) object;
							if (ds.getType() == DisconnectType.USERNAME_INVALID) {
								System.out.println("Specified username is invalid.\nDisconnecting...");
								socket.close();
								System.exit(6);
							} else if (ds.getType() == DisconnectType.USERNAME_TAKEN) {
								System.out.println("Specified username is taken by another user.\nDisconnecting...");
								socket.close();
								System.exit(5);
							} else if (ds.getType() == DisconnectType.KICKED) {
								System.out.println("Kicked by server operator.\nDisconnecting...");
								socket.close();
								System.exit(9);
							} else if (ds.getType() == DisconnectType.GENERAL_DISCONNECT) {
								System.out.println("Disconnected by server.");
								socket.close();
								System.exit(10);
							}
							stop = true;
						} else if (object instanceof ArrayList<?>) {
							@SuppressWarnings("unchecked")
							ArrayList<User> tmp = (ArrayList<User>) object;
							locations = tmp;
						}
					} catch (IOException ex) {} catch (ClassNotFoundException ex) {
						handleException(ex);
						try {
							socket.close();
						} catch (IOException ex2) {}
						System.exit(8);
					}
				}
			}
		}
	}
	public static void printUsage(int exitCode) {
		System.out.println("Usage: java JSE3DClient ip_address port username");
		System.exit(exitCode);
	}
	public static void handleException(Exception ex) {
		System.out.println("  ** EXCEPTION THROWN **");
		System.out.println(ex.getClass() + " occurred in thread \"" + Thread.currentThread().getName() + "\":");
		System.out.println("Cause: " + ex.getMessage());
		System.out.println();
	}
	public class ConsoleReader implements Runnable {
		public void run() {
			try {
				BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				String line;
				while (!stop) {
					line = reader.readLine();
					if (line != null) {
						handleCommand(line);
					}
				}
				System.exit(0);
			} catch (IOException ex) {
				handleException(ex);
			}
		}
	}
	public void handleCommand(String command) {
		if (command.equalsIgnoreCase("stop")) {
			stop = true;
			System.out.println("Disconnecting...");
		} else {
			System.out.println("Unknown command.");
		}
	}
}