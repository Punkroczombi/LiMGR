package zombie.limgr.interfaces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

import org.ini4j.Ini;

import zombie.limgr.Main;

public class SQ {

	private ServerSocket sock;
	private int port;
	private Thread run;
	
	public SQ(Ini config) {
		this.port = Integer.parseInt(config.get("ServerQuery", "port"));
	}

	public void Start() throws IOException {
		this.sock = new ServerSocket(this.port);
		this.run = new Thread(runner);
		this.run.start();
		System.out.println("Query-Port: "+this.port);
	}
	
	public void Stop() throws IOException {
		this.run.interrupt();
		this.sock.close();
		this.sock = null;
		System.out.println("ServerQuery disabled.\r\n");
	}
	
	private Runnable runner = new Runnable() {
		public void run() {
			while (true) {
				try {
					new Thread(new Controller(sock.accept())).start();
				} catch (IOException e) {
					e.printStackTrace();
					continue;
				}
			}
		}
	};
	
	class Controller implements Runnable{

		private Socket csock;
		
		BufferedReader in; //Received from Client
		PrintStream out; //Out to Client
		
		private CLI cli;
		
		public Controller(Socket csock) throws IOException {
			this.csock = csock;
			System.out.println("[i] "+csock.getInetAddress().toString()+" connects to ServerQuery.\r\n");
			this.in = new BufferedReader(new InputStreamReader(this.csock.getInputStream()));
			this.out = new PrintStream(this.csock.getOutputStream());
			this.cli = new CLI(this.out, this.out);
		}
		
		private boolean isAuthed = false;
		
		@Override
		public void run() {
			while(csock.isConnected()) {
				try {
					String s = in.readLine();
					if(s != null)
						this.read(s);
					else{
						System.out.println("[i] Client disconnected from ServerQuery.\r\n");
						break;
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
			return;
		}
		
		
		
		private void read(String s) throws IOException {
			if(!isAuthed) {
				if(s.equals(Main.config.get("ServerQuery", "authkey"))) {
					isAuthed = true;
					System.out.println("[i] "+csock.getInetAddress().toString()+" authenticated at the ServerQuery.\r\n");
					this.out.println();
					this.out.println(Main.textheader);
					this.out.println("Version: " + Main.version+"\n");
				}else {
					this.out.println("incorrect");
				}
			} else {
				String[] cmd = s.trim().split("\\s+");
				if(cmd[0].equalsIgnoreCase("quit"))
					this.out.println("Quit command not available via ServerQuery.\r\n");
				else
					this.cli.command(cmd);
			}
		}
		
	}
}
